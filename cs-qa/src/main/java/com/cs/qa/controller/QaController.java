package com.cs.qa.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.common.R;
import com.cs.framework.exception.BizException;
import com.cs.framework.security.LoginUser;
import com.cs.framework.security.SecurityUtils;
import com.cs.knowledge.entity.KbDocument;
import com.cs.knowledge.mapper.KbDocumentMapper;
import com.cs.qa.dto.ChatRequest;
import com.cs.qa.entity.ChatConversation;
import com.cs.qa.entity.ChatMessage;
import com.cs.qa.entity.ChatReference;
import com.cs.qa.mapper.ChatConversationMapper;
import com.cs.qa.mapper.ChatMessageMapper;
import com.cs.qa.mapper.ChatReferenceMapper;
import com.cs.qa.memory.ConversationMemoryService;
import com.cs.qa.service.QaService;
import com.cs.qa.vo.ConversationVO;
import com.cs.qa.vo.MessageVO;
import com.cs.qa.vo.ReferenceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 问答接口（对应 contract/rest-api.md 第 2 节 /api/qa）。
 *
 * <p>认证与数据归属（D17/D23）：所有端点均为受保护端点，userId 一律经
 * {@link SecurityUtils#requireUserId()} 从 JWT 认证上下文取真实值，<b>不再使用任何默认用户回退</b>
 * （旧 {@code QaService.DEFAULT_USER_ID=1L} 已随 DEF-065 清除）。未认证请求（无 token 或 token 失效）
 * 由 Spring Security 过滤器链在到达 Controller <b>之前</b>拦截并返回 HTTP 401；若上下文意外缺失而代码
 * 走到 {@code requireUserId()}，则抛 1002（UNAUTHORIZED）做兜底。管理类校验用 {@code requireAdmin()}，
 * 角色不足时抛 1003（FORBIDDEN），按 D23 以 HTTP 200 + code=1003 下发（前端提示“无权限”而非跳登录）。
 * 会话历史读取与删除额外做「本人或 ADMIN」归属校验，防止任何登录用户越权读/删他人会话。</p>
 *
 * <p>返回体：查询端点统一返回 {@code R<T>} + 出参 VO；SSE 端点返回 {@link SseEmitter}（合理例外，
 * 见 CONVENTIONS §4/§7）。时间字段用 {@code LocalDateTime}，由全局 JacksonConfig 统一格式化。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatReferenceMapper referenceMapper;
    private final ConversationMemoryService memoryService;
    /**
     * 仅用于历史引用的「旧数据降级」兜底：本轮之前落库的 chat_reference 行 doc_title 为 null，
     * 按 docId 批量查 kb_document.title 补齐。注入 cs-knowledge 的 Mapper，依赖方向 cs-qa → cs-knowledge 合法（§2）。
     */
    private final KbDocumentMapper kbDocumentMapper;

    /** 流式问答（SSE）：POST + text/event-stream，前端用 fetch+ReadableStream 消费 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return qaService.chatStream(SecurityUtils.requireUserId(), request.getConversationId(), request.getQuestion());
    }

    /**
     * 我的会话列表。
     *
     * <p>按当前登录 userId 过滤（D17 数据归属校验，只返回本人会话）并按最近活跃时间倒序——
     * 前端未配排序，顺序完全由此处 {@code ORDER BY last_active_at DESC} 决定，必须保留。</p>
     */
    @GetMapping("/conversations")
    public R<List<ConversationVO>> conversations() {
        Long userId = SecurityUtils.requireUserId();
        List<ChatConversation> list = conversationMapper.selectList(
                new QueryWrapper<ChatConversation>().eq("user_id", userId).orderByDesc("last_active_at"));
        return R.ok(list.stream().map(ConversationVO::from).toList());
    }

    /**
     * 会话历史消息（含引用溯源回放）。
     *
     * <p>流程：① 归属校验 → ② 按 conversation_id 查全部消息（保留 {@code orderByAsc("id")}，
     * 前端三张表均未配 sorter，排序全靠后端 ORDER BY）→ ③ <b>批量</b>回填引用（严禁 N+1）→ ④ 组装 MessageVO。</p>
     *
     * <p><b>ADMIN 豁免归属校验的理由</b>：管理后台需要能查看任意会话以处理工单与投诉，
     * 故 ADMIN 可读他人会话；普通用户仅能读本人会话，否则抛 1003。</p>
     */
    @GetMapping("/conversations/{id}/messages")
    public R<List<MessageVO>> messages(@PathVariable Long id) {
        // ① 归属校验：会话不存在抛 3003；非本人且非 ADMIN 抛 1003
        requireConversationAccess(id);

        // ② 查该会话全部消息，按 id 升序（一问一答的时间顺序）
        List<ChatMessage> messages = messageMapper.selectList(
                new QueryWrapper<ChatMessage>().eq("conversation_id", id).orderByAsc("id"));

        // ③ 批量回填引用（严禁 N+1）：只对 assistant 消息查引用，user 消息本就无引用
        List<Long> assistantMsgIds = messages.stream()
                .filter(m -> "assistant".equalsIgnoreCase(m.getRole()))
                .map(ChatMessage::getId)
                .toList();

        Map<Long, List<ChatReference>> refsByMsgId = Map.of();
        Map<Long, String> docTitleFallback = Map.of();
        if (!assistantMsgIds.isEmpty()) {
            // 一次 IN 查询取回该会话全部 assistant 消息的引用，按 messageId 分组
            List<ChatReference> allRefs = referenceMapper.selectList(
                    new QueryWrapper<ChatReference>().in("message_id", assistantMsgIds));
            refsByMsgId = allRefs.stream().collect(Collectors.groupingBy(ChatReference::getMessageId));

            // 旧数据降级：doc_title 为 null 的行，收集其 docId 后「一次」批量查 kb_document.title 兜底
            // （同样避免逐条查造成 N+1）。chunk_text 为 null 时由 ReferenceVO 回填空串，不做 ES 回查——
            // 避免 ES 不可用时整个历史接口 500，也避免 N+1 的 ES 往返。
            List<Long> docIdsNeedFallback = allRefs.stream()
                    .filter(r -> r.getDocTitle() == null && r.getDocId() != null)
                    .map(ChatReference::getDocId)
                    .distinct()
                    .toList();
            if (!docIdsNeedFallback.isEmpty()) {
                // 用 HashMap 手动装填（而非 Collectors.toMap）：kb_document.title 理论上可能为 null，
                // Collectors.toMap 对 null value 会抛 NPE，HashMap.put 容忍 null。
                // 用 selectList + in（而非已废弃的 selectBatchIds），一次 IN 查询完成兜底，避免 N+1。
                Map<Long, String> titleMap = new HashMap<>();
                List<KbDocument> docs = kbDocumentMapper.selectList(
                        new QueryWrapper<KbDocument>().in("id", docIdsNeedFallback));
                for (KbDocument doc : docs) {
                    titleMap.put(doc.getId(), doc.getTitle());
                }
                docTitleFallback = titleMap;
            }
        }

        // ④ 组装 MessageVO：assistant 带上引用列表，user 消息 references 传 null（前端 ReferenceList 对 null/空数组安全）
        final Map<Long, List<ChatReference>> refsFinal = refsByMsgId;
        final Map<Long, String> titleFinal = docTitleFallback;
        List<MessageVO> result = messages.stream()
                .map(m -> {
                    if ("assistant".equalsIgnoreCase(m.getRole())) {
                        List<ReferenceVO> refs = refsFinal.getOrDefault(m.getId(), List.of()).stream()
                                .map(r -> ReferenceVO.from(r, titleFinal.get(r.getDocId())))
                                .toList();
                        return MessageVO.from(m, refs);
                    }
                    return MessageVO.from(m, null);
                })
                .toList();
        return R.ok(result);
    }

    /**
     * 删除会话（级联清理消息、引用与 Redis 记忆）。
     *
     * <p>归属校验：本人或 ADMIN 可删，否则 1003（不存在则 3003）。</p>
     *
     * <p>{@code schema.sql} 无外键级联，必须代码显式清理，否则残留孤儿数据。五步顺序不可颠倒：
     * ① 查会话全部 message id → ② 按 message id 批量删 chat_reference → ③ 按 conversation_id 删 chat_message
     * → ④ 删 chat_conversation → ⑤ 清 Redis 会话记忆。</p>
     *
     * <p><b>Redis 清理与事务的关系</b>：{@code clear()} 是 Redis 操作、不受 MySQL 事务保护，
     * 故放在 MySQL 删除之后执行。若事务最终回滚，Redis 记忆已被清空，后果只是下一轮问答丢失上下文滑窗、
     * 会重新累积，属可接受的降级；反之若先清 Redis 再删 MySQL 且删除失败，则会出现「数据库有历史但模型看不到
     * 上下文」的错位，更难排查。</p>
     */
    @DeleteMapping("/conversations/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        // 归属校验：本人或 ADMIN 可删
        requireConversationAccess(id);

        // ① 查该会话下全部 message id（只 select id，减少数据传输）
        List<Long> msgIds = messageMapper.selectList(
                        new QueryWrapper<ChatMessage>().select("id").eq("conversation_id", id))
                .stream().map(ChatMessage::getId).toList();

        // ② 按 message id 批量删 chat_reference（ids 为空时跳过，避免生成非法 SQL "IN ()"）
        if (!msgIds.isEmpty()) {
            referenceMapper.delete(new QueryWrapper<ChatReference>().in("message_id", msgIds));
        }

        // ③ 按 conversation_id 删 chat_message
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("conversation_id", id));

        // ④ 删 chat_conversation
        conversationMapper.deleteById(id);

        // ⑤ 清 Redis 会话记忆（放最后，理由见方法 Javadoc）
        memoryService.clear(id);

        return R.ok();
    }

    /**
     * 会话归属校验：确认会话存在且当前用户有权访问（本人或 ADMIN）。
     *
     * @param conversationId 会话 ID
     * @return 校验通过的会话实体（复用以免二次查询）
     * @throws BizException 3003 会话不存在 / 1002 未认证 / 1003 非本人且非 ADMIN
     */
    private ChatConversation requireConversationAccess(Long conversationId) {
        ChatConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        // getCurrentUser() 未认证返回 null，需显式处理为 1002（受保护端点理论上已被 JWT 过滤器拦截）
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        // ADMIN 豁免：管理后台需查看/处理任意会话的工单与投诉
        boolean isAdmin = "ADMIN".equals(current.getRole());
        boolean isOwner = conv.getUserId() != null && conv.getUserId().equals(current.getUserId());
        if (!isAdmin && !isOwner) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return conv;
    }
}
