package com.cs.infra.ai.resilience;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 手写三态熔断器：{@code CLOSED → OPEN → HALF_OPEN → CLOSED}，状态存进程内（MVP 单实例部署，
 * 不需要 Redis 共享；若演进为多实例，此处替换为 Redis 计数即可，接口不变）。
 *
 * <p><b>为什么手写而不用 Resilience4j</b>：D20 锁定依赖清单禁止新增 Maven 依赖；且手写状态机
 * 的每一条迁移条件都可讲清，是「高可用」创新点（D10 创新点4）的核心答辩素材。</p>
 *
 * <h3>状态迁移条件（全部可讲）</h3>
 * <ul>
 *   <li>{@code CLOSED → OPEN}：滑动窗口内最近 N 次调用样本数 ≥ minCalls 且失败率 ≥ failureRateThreshold；</li>
 *   <li>{@code OPEN}：所有请求快速失败（{@link #tryAcquire()} 返回 false，不发起真实调用），
 *       持续 openDurationMs 后；</li>
 *   <li>{@code OPEN → HALF_OPEN}：开放时长到期，放行试探请求（并发许可上限 halfOpenMaxTrials，
 *       其余请求在试探期间继续快速失败）；</li>
 *   <li>{@code HALF_OPEN → CLOSED}：试探成功次数 ≥ halfOpenSuccessThreshold（供应商真正恢复）；</li>
 *   <li>{@code HALF_OPEN → OPEN}：任一试探失败（立即重新熔断并重新计时，绝不带病放行）。</li>
 * </ul>
 *
 * <h3>并发正确性（路7 启动包硬要求）</h3>
 * <p>全部状态由单把锁保护：状态迁移（读写 state/openedAt/试探许可）与滑动窗口计数（环形数组
 * 增减）必须作为整体原子生效，否则会出现「窗口已超阈值但状态未迁」的撕裂读。取舍说明：
 * 模型调用是秒级网络 IO，锁只保护纳秒级的计数与枚举比较，开销可忽略；相比
 * {@code AtomicReference} + CAS 需要把多个字段打包成一个不可变对象整体替换的实现，
 * 单锁的正确性论证简单得多——正确性优先，吞吐瓶颈永远在网络而非此锁。</p>
 *
 * <p>SSE 消费跑在 {@code CompletableFuture.runAsync} 的异步线程里，同一供应商的熔断器会被
 * 同步链路（重写/意图）与异步流式链路并发访问，因此线程安全是硬要求而非理论洁癖。</p>
 */
@Slf4j
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int windowSize;
    private final int minCalls;
    private final double failureRateThreshold;
    private final long openDurationMs;
    private final int halfOpenMaxTrials;
    private final int halfOpenSuccessThreshold;

    private final Object lock = new Object();

    private State state = State.CLOSED;
    /** 滑动窗口环形数组：true = 该次调用失败 */
    private final boolean[] window;
    private int windowPos = 0;
    private int windowCount = 0;
    private int windowFailures = 0;
    private long openedAt = 0;
    private int halfOpenActive = 0;
    private int halfOpenSuccesses = 0;

    public CircuitBreaker(String name, int windowSize, int minCalls, double failureRateThreshold,
                          long openDurationMs, int halfOpenMaxTrials, int halfOpenSuccessThreshold) {
        this.name = name;
        this.windowSize = Math.max(1, windowSize);
        this.minCalls = Math.max(1, minCalls);
        this.failureRateThreshold = failureRateThreshold;
        this.openDurationMs = openDurationMs;
        this.halfOpenMaxTrials = Math.max(1, halfOpenMaxTrials);
        this.halfOpenSuccessThreshold = Math.max(1, halfOpenSuccessThreshold);
        this.window = new boolean[this.windowSize];
    }

    /**
     * 尝试放行一次请求。
     *
     * @return true = 放行（CLOSED 正常调用，或 HALF_OPEN 分得一个试探许可）；
     *         false = 快速拒绝（OPEN 期间 / HALF_OPEN 试探许可耗尽），调用方必须跳过该供应商，
     *         严禁在 false 时仍发起真实调用（否则熔断器形同虚设）
     */
    public boolean tryAcquire() {
        synchronized (lock) {
            switch (state) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (System.currentTimeMillis() - openedAt >= openDurationMs) {
                        state = State.HALF_OPEN;
                        halfOpenActive = 1;      // 本次放行即占用一个试探许可
                        halfOpenSuccesses = 0;
                        log.info("[熔断器:{}] OPEN 持续 {}ms 到期 → HALF_OPEN，放行试探(许可 1/{})",
                                name, openDurationMs, halfOpenMaxTrials);
                        return true;
                    }
                    return false;
                case HALF_OPEN:
                    if (halfOpenActive < halfOpenMaxTrials) {
                        halfOpenActive++;
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }
    }

    /** 记一次成功：CLOSED 下计入窗口；HALF_OPEN 下试探成功，达到阈值则闭合 */
    public void onSuccess() {
        synchronized (lock) {
            switch (state) {
                case CLOSED:
                    record(false);
                    break;
                case HALF_OPEN:
                    halfOpenSuccesses++;
                    halfOpenActive = Math.max(0, halfOpenActive - 1);
                    if (halfOpenSuccesses >= halfOpenSuccessThreshold) {
                        state = State.CLOSED;
                        resetWindow();
                        log.info("[熔断器:{}] HALF_OPEN 试探成功 {}/{} → CLOSED，恢复正常放行",
                                name, halfOpenSuccesses, halfOpenSuccessThreshold);
                    }
                    break;
                case OPEN:
                    // 迟到成功（请求发起于 OPEN 之前），忽略不计
                    break;
                default:
                    break;
            }
        }
    }

    /** 记一次失败：CLOSED 下计入窗口并判失败率；HALF_OPEN 下试探失败立即打回 OPEN */
    public void onFailure() {
        synchronized (lock) {
            switch (state) {
                case CLOSED:
                    record(true);
                    if (windowCount >= minCalls
                            && (double) windowFailures / windowCount >= failureRateThreshold) {
                        state = State.OPEN;
                        openedAt = System.currentTimeMillis();
                        halfOpenActive = 0;
                        halfOpenSuccesses = 0;
                        log.warn("[熔断器:{}] 失败率 {}/{} ≥ {} → OPEN，{}ms 内快速失败不真实调用",
                                name, windowFailures, windowCount, failureRateThreshold, openDurationMs);
                    }
                    break;
                case HALF_OPEN:
                    state = State.OPEN;
                    openedAt = System.currentTimeMillis();
                    halfOpenActive = 0;
                    halfOpenSuccesses = 0;
                    log.warn("[熔断器:{}] HALF_OPEN 试探失败 → 重新 OPEN，重新计时 {}ms", name, openDurationMs);
                    break;
                case OPEN:
                    // 已在 OPEN 中（并发的其它请求先触发了迁移），忽略
                    break;
                default:
                    break;
            }
        }
    }

    /** 当前状态快照（仅用于日志与健康观测，不做业务判断） */
    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    /** 写入一个样本到环形窗口（调用方必须持锁） */
    private void record(boolean failure) {
        if (windowCount == windowSize) {
            // 窗口已满：新样本挤掉最旧的样本，失败计数同步增减
            if (window[windowPos]) {
                windowFailures--;
            }
        } else {
            windowCount++;
        }
        window[windowPos] = failure;
        if (failure) {
            windowFailures++;
        }
        windowPos = (windowPos + 1) % windowSize;
    }

    private void resetWindow() {
        Arrays.fill(window, false);
        windowPos = 0;
        windowCount = 0;
        windowFailures = 0;
    }
}
