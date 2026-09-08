package com.cs.qa.service;

import com.cs.infra.ai.resilience.ChatModelFacade;
import com.cs.qa.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 问题重写（M2 prompt 工程之一）——结合最近多轮对话，把含指代/省略的问题补全为可独立检索的完整问题。
 *
 * <p>例：上文聊"5G畅享129套餐"，用户接着问"它多少钱" → 重写为"5G畅享129套餐多少钱"。
 * 重写后的 query 再去检索，召回质量显著高于用原始口语化问题。</p>
 *
 * <p><b>降级</b>：无历史（首轮）直接返回原问题；LLM 调用失败、或输出未通过有效性校验
 * （见 {@link #isValidRewrite}）时返回原问题，绝不阻断主链。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatModelFacade chatModelFacade;
    private final ConversationMemoryService memoryService;

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
            // 走统一封装层（超时/重试/熔断/failover）；全供应商失败时 facade 抛
            // ModelUnavailableException，由下方 catch 降级用原问题（降级目标保持不变）
            String rewritten = chatModelFacade.call(null, prompt);
            if (isValidRewrite(rewritten)) {
                log.debug("问题重写: [{}] → [{}]", query, rewritten.trim());
                return rewritten.trim();
            }
            // 输出无效（如备1 弱模型实测输出 "-"）：视为重写失败降级，带原始输出便于排查
            log.warn("问题重写输出无效，视为重写失败降级用原问题: raw=[{}]", rewritten);
        } catch (Exception e) {
            log.warn("问题重写失败，降级用原问题: {}", e.getMessage());
        }
        return query;
    }

    /**
     * 重写输出的有效性校验（模型输出非空 ≠ 有效）。
     *
     * <p><b>为什么对主/备供应商统一生效、不是只为备1 加的</b>：原降级语义是「重写失败降级用
     * 原问题」，但判定条件只有「非空」，不完整——阶段二实测中备1（硅基流动 7B 免费模型）
     * 曾输出 "-" 这类纯标点垃圾并被当作有效重写，污染后续检索与意图识别（路7 实测发现，
     * 统筹裁定为缺陷）。本校验是既有降级语义的<b>判定条件补全</b>：qwen-plus 偶发输出垃圾时
     * 同样会被拦截回退，与供应商无关。</p>
     *
     * <p>命中任一条即判无效：① null 或 trim 后为空；② trim 后长度 < 2（重写后的问题
     * 不可能单字符）；③ 全部由标点/短横线/空白构成（如 "-"、"—"、"。。。"、"..."——
     * 判定口径：不含任何字母或数字，中文实体词/数字均算有效内容）。</p>
     *
     * @param output 模型原始输出（可能为 null）
     * @return true = 有效，可采用为重写结果
     */
    static boolean isValidRewrite(String output) {
        if (output == null) {
            return false;
        }
        String t = output.trim();
        if (t.length() < 2) {
            return false;
        }
        // 全标点/短横线/空白（无任何字母或数字）= 无效；其余视为有效
        return t.chars().anyMatch(Character::isLetterOrDigit);
    }
}
