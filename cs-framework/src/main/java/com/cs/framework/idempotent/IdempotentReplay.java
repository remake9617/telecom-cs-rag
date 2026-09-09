package com.cs.framework.idempotent;

/**
 * 幂等命中时的结果还原提供方（业务模块实现，{@link IdempotentAspect} 经 Spring 容器按类型获取）。
 *
 * <p>为什么需要这个接口：幂等切面在 cs-framework（最底层模块），它只认得「Redis 里存的
 * 存量标识字符串」（如 ticketId），不知道也不该知道业务结果怎么还原——依赖方向是
 * cs-ticket → cs-framework，框架反向 import 业务 VO 会破坏分层。</p>
 *
 * <p>实现约定：返回值将作为切面返回值<b>直接透传给 HTTP 响应</b>（如 {@code R.ok(TicketVO)}）；
 * 返回 {@code null} 表示存量结果已不存在（如工单已被删除），切面降级为正常执行业务。</p>
 */
public interface IdempotentReplay {

    /**
     * 按存量标识还原业务结果。
     *
     * @param storedValue Redis 中存量的业务标识（建单场景 = ticketId 字符串）
     * @return 直接透传给 HTTP 响应的结果对象；null = 结果已不存在，降级为正常执行
     */
    Object resolve(String storedValue);
}
