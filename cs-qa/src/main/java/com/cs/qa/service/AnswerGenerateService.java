package com.cs.qa.service;

import com.cs.infra.ai.resilience.ChatModelFacade;
import com.cs.knowledge.dto.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 答案生成（M2 prompt 工程核心）——基于检索到的 chunk，用 qwen-plus 流式生成 grounded 答案。
 *
 * <p>prompt 关键设计：</p>
 * <ul>
 *   <li><b>grounding 约束</b>：只准依据【参考资料】作答、禁止编造、资料不足则明说并引导转人工——防幻觉核心（呼应创新点3 幻觉率指标）；</li>
 *   <li><b>引用标注 [n]</b>：答案中标来源序号，前端可映射到具体 chunk 做溯源；</li>
 *   <li><b>流式</b>：stream() 返回 Flux，逐 token 推送，改善等待体验。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerGenerateService {

    private final ChatModelFacade chatModelFacade;

    /** system prompt：客服角色 + grounding 硬约束 + 引用标注 + 无资料兜底 */
    private static final String SYSTEM_PROMPT = """
            你是中国电信的智能客服助手。请严格依据【参考资料】回答用户问题。
            规则：
            1. 只能使用【参考资料】中的信息作答，禁止编造资料中没有的内容；
            2. 在引用具体信息处用 [n] 标注来源编号（n 对应参考资料前的序号）；
            3. 若参考资料不足以回答，明确告知"根据现有资料无法确定"，并建议用户转人工客服；
            4. 回答简洁、准确、口语化，符合客服场景。
            """;

    /**
     * 流式生成 grounded 答案。
     *
     * <p><b>为什么返回 {@code Flux<ChatResponse>} 而非 {@code Flux<String>}：</b>
     * 纯文本流丢失了 token 用量信息。DashScope 在流式模式下会在<b>最后一个 chunk</b>
     * 的 {@code ChatResponse.getMetadata().getUsage()} 里回传累计 token，只有拿到
     * {@link ChatResponse} 才能提取 usage 并写入 {@code chat_message.token_cost}，
     * 修复「流式结束显示 N tokens、刷新后标签消失」。文本分片由调用方从
     * {@code response.getResult().getOutput().getText()} 提取。</p>
     *
     * <p><b>system prompt 与 grounding 约束（含引用编号 [n] 要求）保持完全不变</b>，
     * 仅将 {@code .stream().content()} 改为 {@code .stream().chatResponse()}（Spring AI 1.1.2 方法名）。</p>
     *
     * @param query  用户问题（重写后）
     * @param chunks 检索到的参考资料（已 rerank 排序）
     * @return 流式 {@link ChatResponse}（逐 chunk），调用方自取文本与 usage
     */
    public Flux<ChatResponse> generateStream(String query, List<RetrievedChunk> chunks) {
        String userPrompt = buildUserPrompt(query, chunks);
        // 走统一封装层：超时/首包探测/熔断/多供应商 failover 全部收敛在 facade。
        // failover 只发生在首包之前（用户无感知）；首包之后流中途断开时 facade 抛
        // StreamInterruptedException（不 failover，避免已推送内容重复），由 QaService 的
        // catch 保留已生成部分并落库 + 推 error 事件。
        // tokenCost 口径（D28）不变：仍从流式末 chunk 的 metadata.usage.totalTokens 提取，
        // failover 到备用供应商时同样成立（实测硅基流动流式回传 usage）。
        return chatModelFacade.stream(SYSTEM_PROMPT, userPrompt);
    }

    /** 组装 user prompt：带序号的参考资料 + 用户问题 */
    private String buildUserPrompt(String query, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder("【参考资料】\n");
        if (chunks == null || chunks.isEmpty()) {
            sb.append("（无相关资料）\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                sb.append("[").append(i + 1).append("] ")
                        .append(chunks.get(i).getContent()).append("\n\n");
            }
        }
        sb.append("【用户问题】\n").append(query);
        return sb.toString();
    }
}
