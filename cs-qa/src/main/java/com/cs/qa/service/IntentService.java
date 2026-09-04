package com.cs.qa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 意图识别（M2 prompt 工程之一）——把用户问题分类为 KB / TICKET / CHITCHAT，决定后续走哪条路：
 * <ul>
 *   <li>KB：走知识库检索问答（主路径）</li>
 *   <li>TICKET：走转人工工单</li>
 *   <li>CHITCHAT：直接闲聊回复，不检索</li>
 * </ul>
 *
 * <p>对应 DESIGN 4.3 数据流的意图分流、创新点2（面向客服的意图识别）。</p>
 * <p><b>降级</b>：LLM 失败或输出无法识别时，默认 KB（走知识库问答是最安全的兜底）。</p>
 */
@Slf4j
@Service
public class IntentService {

    public static final String INTENT_KB = "KB";
    public static final String INTENT_TICKET = "TICKET";
    public static final String INTENT_CHITCHAT = "CHITCHAT";

    private final ChatClient chatClient;

    public IntentService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /** 分类 prompt：给出类别定义 + 强约束只输出类别代码 */
    private static final String INTENT_PROMPT = """
            你是电信运营商智能客服的「意图分类器」。判断用户问题属于以下哪一类，只输出类别代码（KB/TICKET/CHITCHAT），不要任何解释：
            - KB：业务知识库能回答的咨询（套餐资费、流量通话、宽带、账单规则、办理流程、常见问答等）
            - TICKET：需要人工介入的（故障报修需上门、投诉、账号异常、退款、明确要求转人工等）
            - CHITCHAT：问候、闲聊、与电信业务无关的内容

            【用户问题】%s

            【类别代码】
            """;

    /**
     * 分类用户意图。
     *
     * @param query 用户问题（一般用重写后的）
     * @return KB / TICKET / CHITCHAT
     */
    public String classify(String query) {
        try {
            String result = chatClient.prompt().user(INTENT_PROMPT.formatted(query)).call().content();
            if (result != null) {
                String r = result.trim().toUpperCase();
                if (r.contains(INTENT_TICKET)) return INTENT_TICKET;
                if (r.contains(INTENT_CHITCHAT)) return INTENT_CHITCHAT;
                if (r.contains(INTENT_KB)) return INTENT_KB;
            }
        } catch (Exception e) {
            log.warn("意图识别失败，降级为 KB: {}", e.getMessage());
        }
        return INTENT_KB;   // 默认走知识库问答
    }
}
