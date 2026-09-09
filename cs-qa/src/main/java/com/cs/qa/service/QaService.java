package com.cs.qa.service;

import com.cs.infra.ai.resilience.ModelUnavailableException;
import com.cs.infra.ai.resilience.StreamInterruptedException;
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
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
        // DEF-081 客户端断开取消联动：断开（onError/onTimeout 或 send 失败）时 cancel 流订阅 →
        // 模型调用被中止、不再继续白烧付费 token。与 D31「模型失败」是两种不同情形：断开时
        // 客户端已不存在，推 error 事件没有意义（只会触发 ResponseBodyEmitter has already
        // completed 警告刷屏，正是 DEF-078/081 的现象），但已生成部分仍应落库（用户刷新后
        // 能看到那半截回答）。onCompletion 在正常完成时也会触发，此时流已消费完，
        // cancel 是无害 no-op。线程安全：回调从 Servlet 容器线程只触碰 Atomic 持有者，
        // answer/tokenCostRef 的读写全部在异步线程内，无跨线程可变共享。
        StreamGuard guard = new StreamGuard();
        emitter.onError(e -> {
            log.info("SSE 连接错误（客户端断开），取消后续生成: {}", e.getMessage());
            guard.cancel();
        });
        emitter.onTimeout(() -> {
            log.info("SSE 超时，取消后续生成");
            guard.cancel();
        });
        emitter.onCompletion(guard::cancel);
        // userId 由调用方（Controller）保证非 null，为 effectively final，可直接被下方 lambda 捕获
        CompletableFuture.runAsync(() -> {
            try {
                handleChat(userId, conversationId, question, emitter, guard);
            } catch (Exception e) {
                int code = resolveErrorCode(e);
                log.error("问答流程异常（error 事件按 {} 上报，不再统一映射 3002 掩盖非模型根因）", code, e);
                if (!guard.disconnected()) {
                    sendEvent(emitter, "error", Map.of("code", code, "message",
                            code == 3002 ? "问答处理失败" : "系统内部错误，请稍后重试"), guard);
                }
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * DEF-081 取消联动持有者：断开标志 + kill 信号。
     *
     * <p>cancel 从 Servlet 容器线程（emitter 回调）或异步线程（send 失败）调用，
     * 而流消费在 CompletableFuture 异步线程——仅通过 AtomicBoolean/CompletableFuture
     * 交互，不直接触碰 answer 累积缓冲，无并发写冲突。若断开先于流订阅发生，
     * 已完成的 kill future 会让 takeUntilOther 立即收尾（竞态闭合）。</p>
     */
    private static final class StreamGuard {
        private final AtomicBoolean disconnected = new AtomicBoolean(false);
        private final CompletableFuture<Void> kill = new CompletableFuture<>();

        boolean disconnected() {
            return disconnected.get();
        }

        /** kill 信号源（takeUntilOther 消费）；已完成则流立即以 onComplete 收尾 */
        CompletableFuture<Void> kill() {
            return kill;
        }

        /** 客户端已断开：置标志并触发 kill，流正常收尾且上游模型调用被取消 */
        void cancel() {
            disconnected.set(true);
            kill.complete(null);   // 幂等：重复 complete 无副作用
        }
    }

    /**
     * 问答主链路（异步线程内执行）。userId 沿用 {@link #chatStream} 的契约，非 null。
     */
    private void handleChat(Long userId, Long conversationId, String question, SseEmitter emitter, StreamGuard guard) {
        // ⓪ 请求载荷守卫（路7 补刀2）：question 为空时整条链路无意义，且 saveUserMessage 落库
        // 会因 chat_message.content NOT NULL 且无默认值炸出 DataIntegrityViolationException
        // （复用既有会话时 resolveConversation 不触碰 question，不会被 NPE 兜住），
        // 该 DB 异常曾被外层 catch 误判成 3002 模型失败。尽早短路，错误码用 1001 参数错误。
        if (question == null || question.isBlank()) {
            log.warn("收到空问题，跳过落库与模型链路: userId={}, conversationId={}", userId, conversationId);
            sendEvent(emitter, "error", Map.of("code", 1001, "message", "问题不能为空"), guard);
            emitter.complete();
            return;
        }

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
            finishWithText(emitter, convId, hint, rewritten, intent, List.of(), ticket.getId(), guard);
            return;
        }

        // ⑥ 检索（KB 意图才检索；CHITCHAT 不检索，直接生成）
        List<RetrievedChunk> chunks = List.of();
        if (IntentService.INTENT_KB.equals(intent)) {
            chunks = retrievalService.hybridRetrieve(
                    RetrievalRequest.builder().query(rewritten).topK(TOP_K).build());
            // ⑦ 推引用来源（溯源）
            sendEvent(emitter, "reference", Map.of("references", buildReferences(chunks)), guard);

            // ⑧ 无召回兑底：建工单转人工（集成接线）
            if (chunks.isEmpty()) {
                Ticket ticket = ticketService.createTicket(userId, convId, question, "知识库无召回，转人工");
                String fallback = "抱歉，我在知识库中没有找到足够相关的资料。我已为您创建工单（编号 " + ticket.getId() + "），人工客服会尽快处理，您可在「我的工单」查看回复。";
                finishWithText(emitter, convId, fallback, rewritten, intent, List.of(), ticket.getId(), guard);
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
                    // DEF-081：断开联动用 takeUntilOther 而非订阅 cancel——直接 cancel 上游订阅
                    // 不会给 toStream() 的阻塞迭代器投递终止哨兵（BlockingSubscriber 只在自己被
                    // cancel 时才 offer DONE），迭代器会在空队列上永久阻塞；takeUntilOther 触发时
                    // 以 onComplete 正常收尾：迭代自然退出、上游模型调用同时被取消
                    .takeUntilOther(Mono.fromFuture(guard.kill()))
                    .toStream()
                    .forEach(response -> {
                        // 文本分片：getResult()/getOutput() 在某些 chunk（如只带 usage 的末尾 chunk）可能为 null，
                        // 必须做空值防护，否则流式中途 NPE 会中断整个回答。
                        if (response != null && response.getResult() != null
                                && response.getResult().getOutput() != null) {
                            String delta = response.getResult().getOutput().getText();
                            if (delta != null && !delta.isEmpty()) {
                                answer.append(delta);
                                sendEvent(emitter, "message", Map.of("delta", delta), guard);
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
            // 异常分类分派（路7 补刀2）：模型链路失败 → 3002；其余（DB 约束、ES、Redis 等系统异常）
            // → 1999，绝不把 DataIntegrityViolationException 这类数据库异常统一映射成「模型失败」——
            // 容器实测中该掩盖让排障整轮才定位到真因是 DB 约束而非模型。
            int code = resolveErrorCode(e);
            if (code == 3002) {
                log.error("流式生成失败（模型链路：重试/failover 已耗尽）", e);
            } else {
                log.error("流式问答发生非模型异常，按 1999 上报", e);
            }
            // 首包后断流：已生成的部分内容不能静默丢弃——保留落库并写记忆，保证历史回放与
            // 上下文连续；已推给前端的 message 分片不重发，error 事件告知本次回答不完整。
            // 部分内容为空则什么都不落（saveAssistantMessage 入口守卫双保险）。
            // tokenCost 若已从流式 chunk 拿到则随部分内容一并落库（允许 null，D28 口径不变）。
            trySavePartial(convId, answer.toString(), rewritten, intent, chunks, tokenCostRef[0]);
            if (guard.disconnected()) {
                // 断开与模型失败是两种情形（D31 只约束后者）：连接已断，不推 error 事件，
                // 已生成部分已在上方落库，info 级留痕即可
                log.info("客户端已断开且流式异常终止，已生成部分落库，不再推 error: conv={}", convId);
                emitter.complete();
                return;
            }
            sendEvent(emitter, "error", Map.of("code", code, "message",
                    code == 3002 ? "答案生成失败" : "系统内部错误，请稍后重试"), guard);
            emitter.complete();
            return;
        }

        // DEF-081：断开联动使 toStream 迭代提前正常结束（cancel 信号让阻塞迭代退出）或
        // send 失败置位——已生成部分仍落库（用户刷新后能看到那半截回答），不推 done（连接已断），
        // info 级日志而非 error（不再是异常，而是预期行为）
        if (guard.disconnected()) {
            log.info("客户端已断开，生成已取消，已生成部分内容落库: conv={}, 已生成 {} 字",
                    convId, answer.length());
            trySavePartial(convId, answer.toString(), rewritten, intent, chunks, tokenCostRef[0]);
            emitter.complete();
            return;
        }

        // ⑩ assistant 落库 + 引用 + 记忆 → done
        String answerText = answer.toString();
        Integer tokenCost = tokenCostRef[0];
        Long msgId = saveAssistantMessage(convId, answerText, rewritten, intent, tokenCost);
        if (msgId != null) {
            saveReferences(msgId, chunks);
            memoryService.append(convId, "assistant", answerText);
        }
        // msgId 为 null 只发生在「流正常完成但零文本」的病态场景：入口守卫跳过落库，done 载荷
        // 的 messageId 为 null——前端 typeof 守卫使点赞点踩静默变灰，可接受且排障有 warn 日志。
        // done 事件必须最后发（ticket_hint 已在 finishWithText 分支先于 done；正常问答分支无 ticket_hint）
        sendEvent(emitter, "done", donePayload(convId, msgId, tokenCost), guard);
        emitter.complete();
    }

    /** 直接以固定文本结束（转人工/兑底场景）：message → ticket_hint → 落库 → done */
    private void finishWithText(SseEmitter emitter, Long convId, String text,
                                String rewritten, String intent, List<RetrievedChunk> chunks, Long ticketId,
                                StreamGuard guard) {
        sendEvent(emitter, "message", Map.of("delta", text), guard);
        if (ticketId != null) {
            // 后端已自动建单：ticket_hint 带 ticketId + autoCreated，前端显示“查看工单”而非重复建单
            sendEvent(emitter, "ticket_hint", Map.of("conversationId", convId, "ticketId", ticketId, "autoCreated", true), guard);
        }
        // 转人工/兜底场景不调模型，tokenCost 传 null（前端 typeof==='number' 守卫自动不显示 token 标签）。
        // SSE 事件顺序不可颠倒：ticket_hint 必须在 done 之前发——① 后端 SseEmitter.complete() 之后发不出去
        // （阶段一踩坑 #10）；② 前端 chatStream.ts:140-145 收到 done 会立即 reader.cancel() 并 return，之后发的任何事件永远读不到。
        Long msgId = saveAssistantMessage(convId, text, rewritten, intent, null);
        saveReferences(msgId, chunks);
        memoryService.append(convId, "assistant", text);
        sendEvent(emitter, "done", donePayload(convId, msgId, null), guard);
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
        if (content == null || content.isBlank()) {
            log.warn("user 消息内容为空，跳过落库（防 chat_message.content NOT NULL 约束报错）: convId={}", convId);
            return;
        }
        ChatMessage m = new ChatMessage();
        m.setConversationId(convId);
        m.setRole("user");
        m.setContent(content);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    private Long saveAssistantMessage(Long convId, String content, String rewritten, String intent, Integer tokenCost) {
        // 入口守卫（路7 补刀2）：content 为空绝不 insert —— MyBatis-Plus NOT_NULL 字段策略会把
        // null 字段从 INSERT 语句中整个省略，chat_message.content 是 NOT NULL 且无默认值，
        // MySQL 8.4 严格模式直接抛 DataIntegrityViolationException，曾被外层 catch 误判成
        // 3002 模型失败（排障被误导一整轮）。守卫放在入口而非某个 catch 分支，
        // 对本方法的全部调用方（⑩ 正常路径 / finishWithText / 部分内容落库）统一生效。
        if (content == null || content.isBlank()) {
            log.warn("assistant 消息内容为空，跳过落库: convId={}, intent={}", convId, intent);
            return null;
        }
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

    /**
     * 流式失败时保留已生成的部分内容（非空才落库），供异常分派后的 catch 分支复用。
     *
     * <p>为什么连引用与记忆一起落：历史回放与多轮上下文都依赖 assistant 消息存在；
     * 部分内容为空则什么都不做（{@link #saveAssistantMessage} 入口守卫双保险）。</p>
     */
    private void trySavePartial(Long convId, String partial, String rewritten, String intent,
                                List<RetrievedChunk> chunks, Integer tokenCost) {
        if (partial == null || partial.isBlank()) {
            return;
        }
        Long partialMsgId = saveAssistantMessage(convId, partial, rewritten, intent, tokenCost);
        if (partialMsgId != null) {
            saveReferences(partialMsgId, chunks);
        }
        memoryService.append(convId, "assistant", partial);
    }

    /**
     * 按异常类型分派 SSE {@code error} 事件错误码（路7 补刀2）：模型链路异常
     * （{@link ModelUnavailableException} / {@link StreamInterruptedException}，含被包装为
     * cause 的形态）→ 3002；其余一切（DataIntegrityViolationException 等 DataAccessException、
     * ES/Redis 异常等）→ 1999，与 GlobalExceptionHandler 的系统兜底同口径。
     *
     * <p>价值不在错误码本身，而在排障时不被误导：统一 3002 会让「数据库约束炸了」被当成
     * 「模型不可用」排查一整轮（容器实测教训）。</p>
     */
    static int resolveErrorCode(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof ModelUnavailableException || cur instanceof StreamInterruptedException) {
                return 3002;
            }
            Throwable next = cur.getCause();
            if (next == cur) {
                break;
            }
            cur = next;
        }
        return 1999;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data, StreamGuard guard) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 发送失败几乎总是客户端已断开（broken pipe）：联动取消，停止继续白烧模型 token（DEF-081）
            log.warn("SSE 事件[{}]发送失败: {}", name, e.getMessage());
            guard.cancel();
        }
    }
}
