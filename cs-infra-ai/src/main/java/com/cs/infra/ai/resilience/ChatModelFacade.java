package com.cs.infra.ai.resilience;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <b>全仓唯一的大模型调用入口</b>（收敛原 4 处各自 {@code ChatClient.builder().build()} 的裸调用，
 * 闭环 DEF-026）：统一提供连接/读取超时、可重试异常的指数退避重试、流式首包探测、
 * 三态熔断（{@link CircuitBreaker}）与多供应商 failover。
 *
 * <h3>为什么备用供应商用手动 {@code new OpenAiChatModel(...)} 而非注册 bean</h3>
 * <p>application.yml 是单 provider 硬绑定（{@code spring.ai.model.chat=dashscope}），若把备用
 * 供应商也注册为 {@code ChatModel} bean，会触发 {@code ChatClientAutoConfiguration} 的
 * 单候选断言（{@code @ConditionalOnSingleCandidate}）失败或装配歧义。备用模型在构造器里以
 * <b>普通对象</b>手动构造（不注册为 bean），容器内 {@code DashScopeChatModel} 仍是唯一
 * ChatModel bean，自动装配面零影响。</p>
 *
 * <p><b>为什么复用 {@code OpenAiChatModel} 而不是手写 RestClient 调 OpenAI 兼容接口</b>：
 * {@code spring-ai-starter-model-openai}（embedding 在用）已把 {@code OpenAiApi/OpenAiChatModel}
 * 带进 classpath，复用它<b>零新增依赖</b>（不触碰 D20）；GLM/DeepSeek/硅基流动均提供 OpenAI 兼容的
 * {@code chat/completions} 接口，流式自动统一为 {@code Flux<ChatResponse>}，与主链
 * {@code DashScopeChatModel} 的消费代码（delta/usage 提取）完全同构——省掉自研响应式 SSE 解析，
 * 也保证 tokenCost（末 chunk usage，D28）在备用供应商上同样取得到（实测硅基流动流式回传 usage）。</p>
 *
 * <h3>供应商 failover 链（D13 矩阵的容灾扩展）</h3>
 * <p>主 DashScope(qwen-plus) → 备1 硅基流动免费 Chat 模型 → 备2 GLM-4-Flash → 备3 DeepSeek。
 * <b>备1 是 7B 级免费模型，能力弱于 qwen-plus，failover 后回答质量会下降但服务不中断</b>——
 * 这正是熔断降级的语义：可用性优先于质量，是刻意取舍而非缺陷。每个供应商独立维护熔断状态，
 * 任一供应商故障只影响自己的链位。apiKey 未配置的备用供应商自动跳过（链缩短而非报错）。</p>
 *
 * <h3>流式与同步的失败策略差异（本类最重要的设计点）</h3>
 * <ul>
 *   <li><b>同步调用</b>：同供应商先指数退避+抖动重试（仅可重试异常：超时/5xx/429；
 *       4xx 参数类错误重试无意义，直接换下一供应商）；全链失败抛 {@link ModelUnavailableException}
 *       交调用方按降级矩阵处理。</li>
 *   <li><b>流式调用</b>：不做同供应商重试（首包等待已花掉探测阈值，重试同一家延迟翻倍），
 *       首包前失败直接 failover 下一供应商；<b>首包后失败绝不 failover</b>（用户已看到一半内容，
 *       重发会重复/错乱），抛 {@link StreamInterruptedException} 交调用方保留部分内容并推 error 事件。</li>
 * </ul>
 *
 * <p><b>超时口径</b>：{@code timeout(firstTokenTimeoutMs)} 挂在每个元素间隔上——对第一个元素即
 * 首包探测，对后续元素即「chunk 间停滞上限」（中途停滞超时按首包后失败处理，推 error 终止，
 * 不让用户吊在半截回答上）。同步调用用 {@code block(callTimeoutMs)} 整体兜底。</p>
 */
@Slf4j
@Service
public class ChatModelFacade {

    /** failover 链上的一个供应商：模型实例 + 独立熔断器 */
    private record Provider(String name, ChatModel model, CircuitBreaker breaker) {
    }

    private final AiResilienceProperties props;
    private final List<Provider> providers = new ArrayList<>();

    public ChatModelFacade(DashScopeChatModel dashScopeChatModel, AiResilienceProperties props) {
        this.props = props;
        // 主供应商：唯一注册为 bean 的 ChatModel（DashScope starter 自动装配，yml options 已带 qwen-plus）
        providers.add(new Provider("dashscope", dashScopeChatModel, newCircuit("dashscope")));
        registerBackup("siliconflow", props.getSiliconflow(), "SILICONFLOW_API_KEY");
        registerBackup("zhipu", props.getZhipu(), "ZHIPU_API_KEY");
        registerBackup("deepseek", props.getDeepseek(), "DEEPSEEK_API_KEY");
        List<String> chain = providers.stream().map(Provider::name).toList();
        log.info("ChatModelFacade 就绪：failover 链 = {}（failoverEnabled={}）", chain, props.isFailoverEnabled());
    }

    /** 注册备用供应商（手动构造，不注册为 bean；apiKey/baseUrl 为空则跳过） */
    private void registerBackup(String name, AiResilienceProperties.Backup cfg, String envKey) {
        if (!props.isEnabled() || !props.isFailoverEnabled()) {
            return;
        }
        String key = firstNonBlank(cfg.getApiKey(), System.getenv(envKey));
        String baseUrl = cfg.getBaseUrl();
        String model = cfg.getModel();
        if (key == null || key.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            log.info("备用供应商[{}]未配置 apiKey/baseUrl（{} 为空），跳过注册，failover 链不含它", name, envKey);
            return;
        }
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(key)
                .completionsPath(cfg.getChatPath())
                .build();
        OpenAiChatModel model2 = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.7).build())
                .build();
        providers.add(new Provider(name, model2, newCircuit(name)));
        log.info("备用供应商[{}]已注册（model={}，chatPath={}）", name, model, cfg.getChatPath());
    }

    // ==================== 同步调用 ====================

    /**
     * 同步调用（问题重写/意图识别等短任务入口）。
     *
     * <p>统一走流式端点 + {@code collectList().block()}：① 超时可由 reactor 精确控制（DashScope
     * 原生同步调用无超时配置面，裸调用可能无限阻塞，正是 DEF-026 的病灶之一）；② 主备供应商
     * 行为完全同构；③ usage 同样在末 chunk 返回，需要时可用 {@link #stream} 系语义提取。</p>
     *
     * @param systemPrompt system 角色提示词，可为 null（纯 user 任务）
     * @param userPrompt   user 提示词
     * @return 模型回答全文
     * @throws ModelUnavailableException 全部供应商失败或熔断中，调用方按降级矩阵处理
     */
    public String call(String systemPrompt, String userPrompt) {
        List<String> tried = new ArrayList<>();
        for (Provider p : usableProviders()) {
            if (!p.breaker().tryAcquire()) {
                log.warn("[facade] 供应商[{}]熔断中(OPEN/试探许可耗尽)，同步调用快速跳过，不真实调用", p.name());
                continue;
            }
            for (int attempt = 1; attempt <= props.getRetry().getMaxAttempts(); attempt++) {
                try {
                    String text = doCall(p, systemPrompt, userPrompt);
                    p.breaker().onSuccess();
                    return text;
                } catch (Exception e) {
                    p.breaker().onFailure();
                    boolean retryable = isRetryable(e);
                    log.warn("[facade] 供应商[{}]同步调用失败 attempt={}/{} retryable={} err={}",
                            p.name(), attempt, props.getRetry().getMaxAttempts(), retryable, rootMsg(e));
                    if (!retryable || attempt == props.getRetry().getMaxAttempts()) {
                        break;   // 4xx 参数类错误重试无意义，直接换下一供应商
                    }
                    sleepBackoff(attempt);
                }
            }
            tried.add(p.name());
        }
        throw new ModelUnavailableException("全部供应商不可用（已试: " + tried + "），触发调用方降级");
    }

    /** 健康探测专用：只打主供应商、<b>不经过熔断统计</b>——探测流量计入熔断会造成正反馈
     * （探测越失败熔断越开、熔断开了探测更失败）。失败原样抛异常，由调用方转 FAIL 状态。 */
    public String probe(String systemPrompt, String userPrompt) {
        return doCall(providers.get(0), systemPrompt, userPrompt);
    }

    private String doCall(Provider p, String systemPrompt, String userPrompt) {
        List<ChatResponse> responses = p.model().stream(buildPrompt(systemPrompt, userPrompt))
                .collectList()
                .block(Duration.ofMillis(props.getCallTimeoutMs()));
        if (responses == null || responses.isEmpty()) {
            throw new IllegalStateException("供应商[" + p.name() + "]返回空响应");
        }
        StringBuilder sb = new StringBuilder();
        for (ChatResponse r : responses) {
            if (r != null && r.getResult() != null && r.getResult().getOutput() != null
                    && r.getResult().getOutput().getText() != null) {
                sb.append(r.getResult().getOutput().getText());
            }
        }
        String text = sb.toString();
        if (text.isBlank()) {
            throw new IllegalStateException("供应商[" + p.name() + "]返回空内容");
        }
        return text;
    }

    // ==================== 流式调用 ====================

    /**
     * 流式调用（grounded 答案生成入口），返回与主链消费代码完全兼容的 {@code Flux<ChatResponse>}：
     * delta 从 {@code result.output.text} 取，tokenCost 从末 chunk {@code metadata.usage.totalTokens}
     * 取（D28 口径不变，failover 到备用供应商时同样成立）。
     *
     * <p>失败策略见类 Javadoc：首包前失败（含 {@link #tryAcquire} 拒绝、超时、连接失败）逐级
     * failover；首包后失败抛 {@link StreamInterruptedException}（不 failover）。</p>
     */
    public Flux<ChatResponse> stream(String systemPrompt, String userPrompt) {
        // Flux.defer：把链构建推迟到订阅时；gotFirst 按订阅实例隔离（每次重发都是新的探测）
        return Flux.defer(() -> streamFailover(systemPrompt, userPrompt, 0, new AtomicBoolean(false)));
    }

    private Flux<ChatResponse> streamFailover(String systemPrompt, String userPrompt,
                                              int idx, AtomicBoolean gotFirst) {
        List<Provider> chain = usableProviders();
        if (idx >= chain.size()) {
            return Flux.error(new ModelUnavailableException(
                    "全部供应商不可用（流式首包前失败已试遍链上 " + idx + " 个供应商），触发调用方降级"));
        }
        Provider p = chain.get(idx);
        if (!p.breaker().tryAcquire()) {
            log.warn("[facade] 供应商[{}]熔断中(OPEN/试探许可耗尽)，流式快速跳过，不真实调用", p.name());
            return streamFailover(systemPrompt, userPrompt, idx + 1, gotFirst);
        }
        return p.model().stream(buildPrompt(systemPrompt, userPrompt))
                .timeout(Duration.ofMillis(props.getFirstTokenTimeoutMs()))
                .doOnNext(r -> gotFirst.set(true))
                .doOnComplete(p.breaker()::onSuccess)
                .onErrorResume(e -> {
                    p.breaker().onFailure();
                    if (gotFirst.get()) {
                        // 首包之后失败：内容已推送，绝不能 failover 重发（重复/错乱）——
                        // 抛 StreamInterruptedException，由调用方保留部分内容并推 error 事件
                        log.error("[facade] 供应商[{}]流式中途断开（首包后失败，不 failover）: {}",
                                p.name(), rootMsg(e));
                        return Flux.error(new StreamInterruptedException(p.name(), e));
                    }
                    // 首包之前失败：用户还没看到任何内容，failover 到下一供应商，用户无感知。
                    // 注：此处不区分可重试异常——换的是不同供应商，4xx 在另一家完全可能成功。
                    log.warn("[facade] 供应商[{}]流式首包前失败({})，failover → 链位 {}",
                            p.name(), rootMsg(e), idx + 1);
                    return streamFailover(systemPrompt, userPrompt, idx + 1, gotFirst);
                });
    }

    // ==================== 内部工具 ====================

    /** 当前生效的 failover 链（总开关关闭时收缩为仅主供应商，熔断与超时仍生效） */
    private List<Provider> usableProviders() {
        return (props.isEnabled() && props.isFailoverEnabled()) ? providers : List.of(providers.get(0));
    }

    private Prompt buildPrompt(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userPrompt));
        return new Prompt(messages);
    }

    /** 指数退避 + 抖动：base * 2^(n-1)，封顶 backoffMaxMs，再加 [0, delay/2] 随机抖动防惊群 */
    private void sleepBackoff(int attempt) {
        long delay = Math.min(props.getRetry().getBackoffMaxMs(),
                props.getRetry().getBackoffBaseMs() * (1L << (attempt - 1)));
        delay += ThreadLocalRandom.current().nextLong(delay / 2 + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ModelUnavailableException("重试退避等待被中断", ie);
        }
    }

    /**
     * 可重试判定：<b>只对网络超时、5xx、429 限流重试</b>；4xx 参数类错误（401 认证错、400 请求错、
     * 404 模型名错）重试也没用，只会放大故障与计费。无法识别的异常默认可重试（宁可多试一次，
     * 避免误杀瞬时抖动）。
     */
    private boolean isRetryable(Throwable e) {
        if (e instanceof NonTransientAiException) {
            return false;   // Spring AI 对 4xx 抛出的「不可重试」标记
        }
        Throwable root = rootCause(e);
        if (root instanceof WebClientResponseException w) {
            int s = w.getStatusCode().value();
            return s == 429 || s >= 500;
        }
        if (root instanceof RestClientResponseException r) {
            int s = r.getStatusCode().value();
            return s == 429 || s >= 500;
        }
        if (root instanceof TimeoutException) {
            return true;    // reactor timeout / block 超时
        }
        return true;
    }

    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private String rootMsg(Throwable e) {
        String msg = rootCause(e).toString();
        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }

    private CircuitBreaker newCircuit(String name) {
        AiResilienceProperties.Circuit c = props.getCircuit();
        return new CircuitBreaker(name, c.getWindowSize(), c.getMinCalls(), c.getFailureRateThreshold(),
                c.getOpenDurationMs(), c.getHalfOpenMaxTrials(), c.getHalfOpenSuccessThreshold());
    }
}
