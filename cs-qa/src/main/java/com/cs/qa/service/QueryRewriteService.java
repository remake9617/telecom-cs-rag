package com.cs.qa.service;

import com.cs.qa.memory.ConversationMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 问题重写（M2 prompt 工程之一）——结合最近多轮对话，把含指代/省略的问题补全为可独立检索的完整问题。
 *
 * <p>例：上文聊"5G畅享129套餐"，用户接着问"它多少钱" → 重写为"5G畅享129套餐多少钱"。
 * 重写后的 query 再去检索，召回质量显著高于用原始口语化问题。</p>
 *
 * <p><b>降级</b>：无历史（首轮）直接返回原问题；LLM 调用失败也返回原问题，绝不阻断主链。</p>
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final ChatClient chatClient;
    private final ConversationMemoryService memoryService;

    public QueryRewriteService(ChatClient.Builder builder, ConversationMemoryService memoryService) {
        this.chatClient = builder.build();
        this.memoryService = memoryService;
    }

    /** 重写 prompt：角色 + 规则 + 输出约束（只输出改写结果） */
    private static final String REWRITE_PROMPT = """
            你是电信运营商智能客服的「问题重写」助手。请根据对话历史，把用户当前问题改写成语义完整、可独立检索的问题。
            规则：
            1. 补全指代词（"它/这个/那个/上面说的"等）为具体对象；
            2. 保留关键业务实体（套餐名、金额、号码、宽带速率等）；
            3. 只输出改写后的问题本身，不要任何解释、前缀或引号；
            4. 若当前问题已完整、无需改写，则原样输出。

            【对话历史】
            %s

            【当前问题】
            %s

            【改写后的问题】
            """;

    /**
     * 结合最近多轮记忆重写问题。
     *
     * @param conversationId 会话 ID（取该会话的历史）
     * @param query          用户当前原始问题
     * @return 重写后的问题（无历史或失败时返回原问题）
     */
    public String rewrite(Long conversationId, String query) {
        List<ConversationMemoryService.Turn> memory = memoryService.getRecent(conversationId, 6);
        if (memory.isEmpty()) {
            return query;   // 首轮无历史，无需重写
        }
        String history = memory.stream()
                .map(t -> ("user".equals(t.getRole()) ? "用户" : "客服") + ": " + t.getContent())
                .collect(Collectors.joining("\n"));
        try {
            String prompt = REWRITE_PROMPT.formatted(history, query);
            String rewritten = chatClient.prompt().user(prompt).call().content();
            if (rewritten != null && !rewritten.isBlank()) {
                log.debug("问题重写: [{}] → [{}]", query, rewritten.trim());
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.warn("问题重写失败，降级用原问题: {}", e.getMessage());
        }
        return query;
    }
}
