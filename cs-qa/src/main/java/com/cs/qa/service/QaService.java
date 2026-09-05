package com.cs.qa.service;

import com.cs.knowledge.dto.RetrievalRequest;
import com.cs.knowledge.dto.RetrievedChunk;
import com.cs.knowledge.service.RetrievalService;
import com.cs.ticket.entity.Ticket;
import com.cs.ticket.service.TicketService;
import com.cs.qa.entity.ChatConversation;
import com.cs.qa.entity.ChatMessage;
import com.cs.qa.entity.ChatReference;
import com.cs.qa.mapper.ChatConversationMapper;
import com.cs.qa.mapper.ChatMessageMapper;
import com.cs.qa.mapper.ChatReferenceMapper;
import com.cs.qa.memory.ConversationMemoryService;
import com.cs.qa.vo.ReferenceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 问答编排（M2 核心）——串起完整链路（DESIGN 4.3）：
 * <pre>
 * ① 会话复用/新建 → ② user 消息落库+写记忆 → ③ 问题重写 → ④ 意图识别
 * → ⑤ 分流(TICKET/KB/CHITCHAT) → ⑥ 检索(M1 RetrievalService) → ⑦ 推 reference 溯源
 * → ⑧ 无召回兜底 → ⑨ 流式生成(推 message) → ⑩ assistant 落库+引用+记忆 → done
 * </pre>
 *
 * <p>SSE 事件遵循 contract/rest-api.md：message / reference / done / error / ticket_hint。</p>
 * <p>异步：MVP 用 CompletableFuture+commonPool（生产应换专用线程池，阶段二）；
 * 流式生成用 Flux.toStream() 阻塞消费，保证事件顺序。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private static final long SSE_TIMEOUT = 180_000L;
    private static final int TOP_K = 5;

    private final QueryRewriteService queryRewriteService;
    private final IntentService intentService;
    private final RetrievalService retrievalService;
    private final AnswerGenerateService answerGenerateService;
    private final ConversationMemoryService memoryService;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatReferenceMapper referenceMapper;
    private final TicketService ticketService;   // 集成接线：TICKET 意图/无召回兑底时建工单

    /**
     * 流式问答入口：立即返回 SseEmitter，问答流程异步执行。
     *
     * <p><b>userId 契约（DEF-065）</b>：由 Controller 从认证上下文经
     * {@link com.cs.framework.security.SecurityUtils#requireUserId()} 取得，<b>调用方必须保证非 null</b>。
     * requireUserId() 在未认证时已抛 1002，故此处不再静默回退、也不二次抛异常（属重复）。
     * 历史上的 {@code DEFAULT_USER_ID=1L} 静默回退已移除：收口后该分支不可达，但保留会让
     * 未来别的调用方传入 null 时静默归属到管理员(id=1)，属数据归属漂移，违反 D17。</p>
     *
     * @param userId         当前登录用户 ID，非 null
     * @param conversationId 会话 ID；为 null 时新建会话
     * @param question       用户问题
     * @return 立即返回的 SSE 发射器，问答链路在异步线程执行
     */
    public SseEmitter chatStream(Long userId, Long conversationId, String question) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        // userId 由调用方（Controller）保证非 null，为 effectively final，可直接被下方 lambda 捕获
        CompletableFuture.runAsync(() -> {
            try {
                handleChat(userId, conversationId, question, emitter);
            } catch (Exception e) {
                log.error("问答流程异常", e);
                sendEvent(emitter, "error", Map.of("code", 3002, "message", "问答处理失败"));
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 问答主链路（异步线程内执行）。userId 沿用 {@link #chatStream} 的契约，非 null。
     */
    private void handleChat(Long userId, Long conversationId, String question, SseEmitter emitter) {
        // ① 会话：复用或新建
        Long convId = resolveConversation(userId, conversationId, question);

        // ② user 消息落库 + 写入记忆
        saveUserMessage(convId, question);
        memoryService.append(convId, "user", question);

        // ③ 问题重写（结合多轮记忆补全指代）
        String rewritten = queryRewriteService.rewrite(convId, question);

        // ④ 意图识别
        String intent = intentService.classify(rewritten);
        log.info("问答: conv={}, intent={}, rewritten=[{}]", convId, intent, rewritten);

        // ⑤ 分流：转人工（集成接线：真正建工单）
        if (IntentService.INTENT_TICKET.equals(intent)) {
            Ticket ticket = ticketService.createTicket(userId, convId, question, "意图识别为转人工(TICKET)");
            String hint = "这个问题需要人工客服为您处理，我已为您创建工单（编号 " + ticket.getId() + "），请稍后在「我的工单」查看回复。";
            finishWithText(emitter, convId, hint, rewritten, intent, List.of(), ticket.getId());
            return;
        }

        // ⑥ 检索（KB 意图才检索；CHITCHAT 不检索，直接生成）
        List<RetrievedChunk> chunks = List.of();
        if (IntentService.INTENT_KB.equals(intent)) {
            chunks = retrievalService.hybridRetrieve(
                    RetrievalRequest.builder().query(rewritten).topK(TOP_K).build());
            // ⑦ 推引用来源（溯源）
            sendEvent(emitter, "reference", Map.of("references", buildReferences(chunks)));

            // ⑧ 无召回兑底：建工单转人工（集成接线）
            if (chunks.isEmpty()) {
                Ticket ticket = ticketService.createTicket(userId, convId, question, "知识库无召回，转人工");
                String fallback = "抱歉，我在知识库中没有找到足够相关的资料。我已为您创建工单（编号 " + ticket.getId() + "），人工客服会尽快处理，您可在「我的工单」查看回复。";
                finishWithText(emitter, convId, fallback, rewritten, intent, List.of(), ticket.getId());
                return;
            }
        }

        // ⑨ 流式生成（阻塞消费 Flux<ChatResponse>，逐字推 message 事件 + 累积 token 用量）
        StringBuilder answer = new StringBuilder();
        // tokenCost 持有者：DashScope 流式模式下 usage 只在最后一个 chunk 返回，前面的 chunk 为 null 或 0，
        // 故用可变持有者在遍历中「持续用非 null 值覆盖」，初值 null 表示「实测未拿到 usage」。
        // 线程安全说明：下面 .toStream().forEach(...) 是顺序阻塞消费，全程跑在 chatStream 里
        // CompletableFuture.runAsync 的同一个异步线程内，事实单线程访问该数组、无并发写；
        // 用数组而非局部变量只是为了绕过 lambda 对捕获变量「必须 effectively final」的限制。
        final Integer[] tokenCostRef = {null};
        try {
            answerGenerateService.generateStream(rewritten, chunks)
                    .toStream()
                    .forEach(response -> {
                        // 文本分片：getResult()/getOutput() 在某些 chunk（如只带 usage 的末尾 chunk）可能为 null，
                        // 必须做空值防护，否则流式中途 NPE 会中断整个回答。
                        if (response != null && response.getResult() != null
                                && response.getResult().getOutput() != null) {
                            String delta = response.getResult().getOutput().getText();
                            if (delta != null && !delta.isEmpty()) {
                                answer.append(delta);
                                sendEvent(emitter, "message", Map.of("delta", delta));
                            }
                        }
                        // 累积 usage：仅在拿到非 null 且 >0 的 totalTokens 时覆盖持有者（通常只有末尾 chunk 有值）
                        // 注：Spring AI 1.1.2 的 Usage.getTotalTokens() 返回 Integer（非 Long）
                        if (response != null && response.getMetadata() != null
                                && response.getMetadata().getUsage() != null) {
                            Integer total = response.getMetadata().getUsage().getTotalTokens();
                            if (total != null && total > 0) {
                                tokenCostRef[0] = total;
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("流式生成失败", e);
            sendEvent(emitter, "error", Map.of("code", 3002, "message", "答案生成失败"));
            emitter.complete();
            return;
        }

        // ⑩ assistant 落库 + 引用 + 记忆 → done
        String answerText = answer.toString();
        Integer tokenCost = tokenCostRef[0];
        Long msgId = saveAssistantMessage(convId, answerText, rewritten, intent, tokenCost);
        saveReferences(msgId, chunks);
        memoryService.append(convId, "assistant", answerText);
        // done 事件必须最后发（ticket_hint 已在 finishWithText 分支先于 done；正常问答分支无 ticket_hint）
        sendEvent(emitter, "done", donePayload(convId, msgId, tokenCost));
        emitter.complete();
    }

    /** 直接以固定文本结束（转人工/兜底场景）：message → ticket_hint → 落库 → done */
    private void finishWithText(SseEmitter emitter, Long convId, String text,
                                String rewritten, String intent, List<RetrievedChunk> chunks, Long ticketId) {
        sendEvent(emitter, "message", Map.of("delta", text));
        if (ticketId != null) {
            // 后端已自动建单：ticket_hint 带 ticketId + autoCreated，前端显示“查看工单”而非重复建单
            sendEvent(emitter, "ticket_hint", Map.of("conversationId", convId, "ticketId", ticketId, "autoCreated", true));
        }
        // 转人工/兜底场景不调模型，tokenCost 传 null（前端 typeof==='number' 守卫自动不显示 token 标签）。
        // SSE 事件顺序不可颠倒：ticket_hint 必须在 done 之前发——① 后端 SseEmitter.complete() 之后发不出去
        // （阶段一踩坑 #10）；② 前端 chatStream.ts:140-145 收到 done 会立即 reader.cancel() 并 return，之后发的任何事件永远读不到。
        Long msgId = saveAssistantMessage(convId, text, rewritten, intent, null);
        saveReferences(msgId, chunks);
        memoryService.append(convId, "assistant", text);
        sendEvent(emitter, "done", donePayload(convId, msgId, null));
        emitter.complete();
    }

    // ==================== 辅助方法 ====================

    private Long resolveConversation(Long userId, Long conversationId, String question) {
        if (conversationId != null) {
            ChatConversation exist = conversationMapper.selectById(conversationId);
            if (exist != null) {
                exist.setLastActiveAt(LocalDateTime.now());
                conversationMapper.updateById(exist);
                return conversationId;
            }
        }
        ChatConversation conv = new ChatConversation();
        conv.setUserId(userId);
        conv.setTitle(question.length() > 20 ? question.substring(0, 20) : question);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setLastActiveAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv.getId();
    }

    private void saveUserMessage(Long convId, String content) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(convId);
        m.setRole("user");
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    private Long saveAssistantMessage(Long convId, String content, String rewritten, String intent, Integer tokenCost) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(convId);
        m.setRole("assistant");
        m.setContent(content);
        m.setRewrittenQuery(rewritten);
        m.setIntent(intent);
        // token_cost 列与实体字段都现成，本轮首次真正写入（可为 null，表示未拿到 usage 或不调模型的兜底场景）
        m.setTokenCost(tokenCost);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        return m.getId();
    }

    private void saveReferences(Long messageId, List<RetrievedChunk> chunks) {
        for (RetrievedChunk c : chunks) {
            ChatReference ref = new ChatReference();
            ref.setMessageId(messageId);
            ref.setDocId(c.getDocId());
            ref.setEsChunkId(c.getChunkId());
            // 引用快照：落库时冗余标题与正文，使历史回放零 ES 往返且 ES 不可用时不会 500（详见 ChatReference 类 Javadoc）
            ref.setDocTitle(c.getDocTitle());
            ref.setChunkText(c.getContent());
            ref.setScore(c.getScore());
            ref.setRerankScore(c.getRerankScore());
            referenceMapper.insert(ref);
        }
    }

    /**
     * 构建 SSE {@code reference} 事件的引用列表。
     *
     * <p><b>为什么返回 {@code List<ReferenceVO>} 而非旧的 {@code List<Map<String,Object>>}：</b>
     * 流式路径（此处）与历史回放路径（{@code QaController}）共用同一 {@link ReferenceVO} 结构，
     * 杜绝两条路径字段名漂移（符合 CONVENTIONS §5「禁止手拼响应」）。score 取值口径由
     * {@link ReferenceVO#from(RetrievedChunk)} 统一保证为 {@code rerankScore != null ? rerankScore : score}。</p>
     */
    private List<ReferenceVO> buildReferences(List<RetrievedChunk> chunks) {
        List<ReferenceVO> refs = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            refs.add(ReferenceVO.from(c));
        }
        return refs;
    }

    /**
     * 组装 {@code done} 事件载荷（两处 done 共用，集中处理 null 值）。
     *
     * <p><b>为什么用 {@link HashMap} 而非 {@code Map.of}：</b>{@code Map.of} 不允许 null 值，
     * 而 {@code tokenCost} 在「实测未拿到 usage」或「转人工兜底不调模型」时为 null，
     * 用 {@code Map.of} 传 null 会在运行期抛 NPE——这是本次改造最易踩的运行期坑。
     * {@code conversationId} 与 {@code messageId} 必须非 null（缺 messageId 前端点赞点踩静默变灰，
     * 缺 conversationId 新会话无法回填 id、URL 不同步），由调用方保证。</p>
     */
    private Map<String, Object> donePayload(Long conversationId, Long messageId, Integer tokenCost) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("messageId", messageId);
        payload.put("tokenCost", tokenCost);   // 允许 null（HashMap 容忍），前端 typeof==='number' 守卫自动隐藏标签
        return payload;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("SSE 事件[{}]发送失败: {}", name, e.getMessage());
        }
    }
}
