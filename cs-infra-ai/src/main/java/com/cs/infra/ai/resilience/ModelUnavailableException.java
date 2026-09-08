package com.cs.infra.ai.resilience;

/**
 * failover 链上全部供应商不可用（逐一失败或熔断中）时抛出，由调用方按降级矩阵处理：
 * <ul>
 *   <li>问题重写 → 用原问题继续（QueryRewriteService catch）；</li>
 *   <li>意图识别 → 降级 KB 意图（IntentService catch）；</li>
 *   <li>grounding 生成（流式首包前全失败）→ 检索结果直出 + 明确提示（QaService 兜底）；</li>
 *   <li>健康探测不走本异常路径（probe 只打主供应商、不计熔断）。</li>
 * </ul>
 *
 * <p>注意：这是「全链失败」的信号，绝不允许被静默吞掉——每个 catch 方都必须留下可见的降级行为与日志。</p>
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message) {
        super(message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
