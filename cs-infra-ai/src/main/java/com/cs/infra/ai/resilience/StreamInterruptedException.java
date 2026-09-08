package com.cs.infra.ai.resilience;

/**
 * 流式回答在<b>首包之后</b>中断（流中途断开、chunk 间隔超时）时抛出。
 *
 * <p><b>为什么与 {@link ModelUnavailableException} 分开：</b>流式回答一旦开始推送，就无法回退到
 * 备用供应商重发——用户已经看到一半内容了，重发会导致内容重复或错乱。所以首包后失败
 * <b>不做 failover</b>，只能：① 保留已生成的部分内容并落库（QaService catch 块）；
 * ② 推 SSE {@code error} 事件告知前端；③ 记为一次熔断失败计入统计。绝不能静默吞掉，
 * 否则用户看到一个截断的回答却不知道发生了什么。</p>
 *
 * <p>这是流式场景熔断策略的核心设计点（面试高频问题：「流式怎么做熔断？」——
 * 首包前可切换、首包后不可切换，只能优雅终止）。</p>
 */
public class StreamInterruptedException extends RuntimeException {

    private final String provider;

    public StreamInterruptedException(String provider, Throwable cause) {
        super("流式回答在首包之后中断（供应商=" + provider + "），已停止生成、保留部分内容", cause);
        this.provider = provider;
    }

    /** 中断发生的供应商名（dashscope / siliconflow / ...），用于日志与统计归因 */
    public String getProvider() {
        return provider;
    }
}
