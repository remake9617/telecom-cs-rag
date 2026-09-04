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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    /** MVP 演示用默认用户；M3 接入 JWT 后从认证上下文取真实 userId */
    private static final long DEFAULT_USER_ID = 1L;

    private final QueryRewriteService queryRewriteService;
    private final IntentService intentService;
    private final RetrievalService retrievalService;
    private final AnswerGenerateService answerGenerateService;
    private final ConversationMemoryService memoryService;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatReferenceMapper referenceMapper;
    private final TicketService ticketService;   // 集成接线：TICKET 意图/无召回兑底时建工单

    /** 流式问答入口：立即返回 SseEmitter，问答流程异步执行 */
    public SseEmitter chatStream(Long userId, Long conversationId, String question) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Long uid = userId != null ? userId : DEFAULT_USER_ID;
        CompletableFuture.runAsync(() -> {
            try {
                handleChat(uid, conversationId, question, emitter);
            } catch (Exception e) {
                log.error("问答流程异常", e);
                sendEvent(emitter, "error", Map.of("code", 3002, "message", "问答处理失败"));
                emitter.complete();
            }
        });
        return emitter;
    }

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
            finishWithText(emitter, convId, hint, rewritten, intent, List.of(), true);
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
                finishWithText(emitter, convId, fallback, rewritten, intent, List.of(), true);
                return;
            }
        }

        // ⑨ 流式生成（阻塞消费 Flux，逐字推 message 事件）
        StringBuilder answer = new StringBuilder();
        try {
            answerGenerateService.generateStream(rewritten, chunks)
                    .toStream()
                    .forEach(delta -> {
                        answer.append(delta);
                        sendEvent(emitter, "message", Map.of("delta", delta));
                    });
        } catch (Exception e) {
            log.error("流式生成失败", e);
            sendEvent(emitter, "error", Map.of("code", 3002, "message", "答案生成失败"));
            emitter.complete();
            return;
        }

        // ⑩ assistant 落库 + 引用 + 记忆 → done
        String answerText = answer.toString();
        Long msgId = saveAssistantMessage(convId, answerText, rewritten, intent);
        saveReferences(msgId, chunks);
        memoryService.append(convId, "assistant", answerText);
        sendEvent(emitter, "done", Map.of("conversationId", convId, "messageId", msgId));
        emitter.complete();
    }

    /** 直接以固定文本结束（转人工/兑底场景）：message → ticket_hint → 落库 → done */
    private void finishWithText(SseEmitter emitter, Long convId, String text,
                                String rewritten, String intent, List<RetrievedChunk> chunks, boolean ticketHint) {
        sendEvent(emitter, "message", Map.of("delta", text));
        if (ticketHint) {
            sendEvent(emitter, "ticket_hint", Map.of("conversationId", convId));
        }
        Long msgId = saveAssistantMessage(convId, text, rewritten, intent);
        saveReferences(msgId, chunks);
        memoryService.append(convId, "assistant", text);
        sendEvent(emitter, "done", Map.of("conversationId", convId, "messageId", msgId));
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

    private Long saveAssistantMessage(Long convId, String content, String rewritten, String intent) {
        ChatMessage m = new ChatMessage();
        m.setConversationId(convId);
        m.setRole("assistant");
        m.setContent(content);
        m.setRewrittenQuery(rewritten);
        m.setIntent(intent);
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
            ref.setScore(c.getScore());
            ref.setRerankScore(c.getRerankScore());
            referenceMapper.insert(ref);
        }
    }

    private List<Map<String, Object>> buildReferences(List<RetrievedChunk> chunks) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            Map<String, Object> ref = new HashMap<>();
            ref.put("docTitle", c.getDocTitle());
            ref.put("chunkText", c.getContent());
            ref.put("score", c.getRerankScore() != null ? c.getRerankScore() : c.getScore());
            refs.add(ref);
        }
        return refs;
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("SSE 事件[{}]发送失败: {}", name, e.getMessage());
        }
    }
}
