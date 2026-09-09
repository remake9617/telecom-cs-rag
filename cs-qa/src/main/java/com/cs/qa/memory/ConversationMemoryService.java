package com.cs.qa.memory;

import com.cs.infra.ai.resilience.ChatModelFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会话短期记忆（Redis）——保存每个会话的活跃上下文，供「问题重写」和「答案生成」提供多轮上下文。
 *
 * <p>设计（DESIGN 5.3，阶段二批次 0 路10 升级为<b>摘要压缩</b>）：</p>
 * <ul>
 *   <li><b>双层存储</b>：MySQL(chat_message) 存完整历史用于持久化与展示；Redis 只存"活跃上下文"，读写快、天然带 TTL。</li>
 *   <li><b>摘要压缩（C5，替代原纯滑窗截断）</b>：列表长度达到 {@link #COMPRESS_THRESHOLD} 时，
 *       把最老 {@link #COMPRESS_BATCH} 条经 {@link ChatModelFacade} 压缩成一条 {@code role=summary}
 *       的特殊轮次插回列表头部——超长会话的早期上下文不再被直接丢弃，而是以摘要形态继续参与
 *       问题重写与生成。原纯截断行为保留为<b>摘要失败时的降级路径</b>。</li>
 *   <li><b>Key</b>：conv:memory:{conversationId}；TTL 6 小时（会话记忆是短期的）。</li>
 * </ul>
 *
 * <p><b>压缩触发为什么克制（不是每轮都摘要）</b>：摘要本身要花 token。触发点设在 16 条
 * （原截断阈值 10 + 6），压缩后列表回到「1 条摘要 + 10 条原文」= 11 条，需再积累 5 条才再次触发——
 * 即首轮摘要出现在约第 8 轮问答，之后平均每 2.5 轮一次，单次只压缩 6 条、成本有界。</p>
 *
 * <p><b>压缩为什么走 {@link ChatModelFacade} 而非自己构造 ChatClient</b>：自建客户端会绕过
 * 超时/重试/熔断/failover，重新制造路7 刚收敛掉的问题（DEF-026 的病灶）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private static final String KEY_PREFIX = "conv:memory:";
    /** 压缩触发阈值：列表达到该长度时压缩最老一批（原滑窗截断阈值为 10） */
    private static final int COMPRESS_THRESHOLD = 16;
    /** 每次压缩的最老条数：压缩后列表 = 1 摘要 + (阈值-本值) 原文 */
    private static final int COMPRESS_BATCH = 6;
    /** 摘要失败降级为纯截断时保留的最近条数（与阶段一滑窗行为一致） */
    private static final int MAX_TURNS = 10;
    /** 摘要正文长度上限：防个别模型的冗长输出撑爆记忆条目 */
    private static final int SUMMARY_MAX_CHARS = 600;
    /** 记忆过期时间：会话闲置 6 小时后自动清理 */
    private static final Duration TTL = Duration.ofHours(6);

    /** 摘要轮次的 role 取值（区别于 user/assistant 的特殊轮次） */
    public static final String ROLE_SUMMARY = "summary";

    /**
     * 摘要 prompt：压缩是<b>有损</b>操作，必须保留「后续问答还会用到」的信息——
     * 用户身份与诉求主题、已给出的结论、尚未解决的问题、具体套餐/业务名词；
     * 丢弃寒暄与重复。已有旧摘要时要求并入（多轮压缩不丢失仍然有效的信息）。
     */
    private static final String SUMMARY_PROMPT = """
            你是电信运营商智能客服的「会话记忆压缩」助手。请把下面的对话历史压缩成一段简洁摘要，供后续对话作为上下文使用。
            要求：
            1. 保留：用户身份与核心诉求、已给出的结论或答案、尚未解决的问题、涉及的具体套餐/业务名词/金额/号码；
            2. 丢弃：寒暄、重复表达、与诉求无关的内容；
            3. 若提供了旧摘要，把其中仍然有效的要点并入新摘要，不要丢失信息；
            4. 只输出摘要正文，不要任何解释、前缀或标题。

            【旧摘要】（可能为空）
            %s

            【待压缩对话】
            %s

            【压缩后的摘要】
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatModelFacade chatModelFacade;

    /**
     * 追加一条消息到会话记忆；列表达到压缩阈值时触发摘要压缩（失败降级为纯截断）。
     *
     * <p>压缩在追加线程内同步执行：问答链路本就跑在异步线程（chatStream 的
     * CompletableFuture），此处的偶发 LLM 调用不会阻塞 Servlet 线程；
     * 换异步执行会让「下一次问答能否用到摘要」变得不确定，得不偿失。</p>
     */
    public void append(Long conversationId, String role, String content) {
        String key = KEY_PREFIX + conversationId;
        try {
            String json = objectMapper.writeValueAsString(new Turn(role, content));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("写入会话记忆失败: conv={}, err={}", conversationId, e.getMessage());
            return;
        }
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size >= COMPRESS_THRESHOLD) {
            compress(conversationId, key);
        }
    }

    /**
     * 取最近 n 条记忆（时间正序：旧→新），用于拼接多轮上下文。
     *
     * <p><b>摘要补偿</b>：列表头部存在摘要轮次且不在请求窗口内时，把摘要前置、
     * 少取一条原文返回——调用方（问题重写取最近 6 条）因此同时拿到「早期上下文的摘要 +
     * 最近原文」，而不是只看到压缩后的最近几条。列表长度不足 n 时窗口天然包含摘要，无需补偿。</p>
     */
    public List<Turn> getRecent(Long conversationId, int n) {
        String key = KEY_PREFIX + conversationId;
        List<String> jsons = redisTemplate.opsForList().range(key, -n, -1);
        List<Turn> window = parse(jsons);
        if (window.isEmpty() || isSummary(window.get(0))) {
            return window;
        }
        String head = redisTemplate.opsForList().index(key, 0);
        List<Turn> headTurns = head == null ? List.of() : parse(List.of(head));
        if (headTurns.isEmpty() || !isSummary(headTurns.get(0))) {
            return window;
        }
        // 窗口已满 n 条：丢掉窗口内最老的 1 条原文，让位给摘要
        List<Turn> compensated = new ArrayList<>(n);
        compensated.add(headTurns.get(0));
        compensated.addAll(window.subList(Math.max(1, window.size() - (n - 1)), window.size()));
        return compensated;
    }

    /** 清空某会话的记忆 */
    public void clear(Long conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    // ==================== 摘要压缩 ====================

    /**
     * 摘要压缩：最老 COMPRESS_BATCH 条（含旧摘要轮次）→ 一条新摘要轮次插回头部。
     *
     * <p>失败降级为纯截断（保留最近 {@link #MAX_TURNS} 条，即阶段一的既有行为）——
     * 摘要是辅助优化不是关键路径，<b>绝不能让问答主链因摘要失败而失败</b>。
     * 同一会话并发压缩的最坏后果是多插一条重复摘要，不损坏数据（每会话问答实际串行），
     * 不为此引入分布式锁。</p>
     */
    private void compress(Long conversationId, String key) {
        try {
            List<Turn> oldest = parse(redisTemplate.opsForList().range(key, 0, COMPRESS_BATCH - 1L));
            if (oldest.size() < 2) {
                return;   // 列表几乎为空（竞态下已被别处压缩/清理），无需压缩
            }
            String oldSummary = null;
            List<Turn> toCompress = oldest;
            if (isSummary(oldest.get(0))) {
                oldSummary = oldest.get(0).getContent();
                toCompress = oldest.subList(1, oldest.size());
            }
            if (toCompress.isEmpty()) {
                return;
            }
            String history = toCompress.stream()
                    .map(t -> ("user".equals(t.getRole()) ? "用户" : "客服") + ": " + t.getContent())
                    .collect(Collectors.joining("\n"));
            String summary = chatModelFacade.call(null,
                    SUMMARY_PROMPT.formatted(oldSummary == null ? "（无）" : oldSummary, history));
            if (summary == null || summary.isBlank() || summary.length() > SUMMARY_MAX_CHARS * 2) {
                // 输出无效视为摘要失败，走与异常相同的降级（截断），不让垃圾摘要污染上下文
                log.warn("会话记忆摘要输出无效，降级为纯截断: conv={}, len={}",
                        conversationId, summary == null ? -1 : summary.length());
                truncate(key);
                return;
            }
            String trimmed = summary.trim();
            if (trimmed.length() > SUMMARY_MAX_CHARS) {
                trimmed = trimmed.substring(0, SUMMARY_MAX_CHARS);
            }
            // 先删被压缩的条目、再把摘要插回头部（lpush）：列表保持 旧→新 的顺序语义
            redisTemplate.opsForList().trim(key, COMPRESS_BATCH, -1);
            redisTemplate.opsForList().leftPush(key,
                    objectMapper.writeValueAsString(new Turn(ROLE_SUMMARY, trimmed)));
            redisTemplate.expire(key, TTL);
            log.info("会话记忆摘要压缩完成: conv={}, 压缩 {} 条 → 1 条摘要（{} 字）",
                    conversationId, oldest.size(), trimmed.length());
        } catch (Exception e) {
            log.warn("会话记忆摘要压缩失败，降级为纯截断（问答主链不受影响）: conv={}, err={}",
                    conversationId, e.getMessage());
            truncate(key);
        }
    }

    /** 降级路径：纯截断保留最近 MAX_TURNS 条（阶段一滑窗行为），失败仅记日志不外抛 */
    private void truncate(String key) {
        try {
            redisTemplate.opsForList().trim(key, -MAX_TURNS, -1);
        } catch (Exception e) {
            log.warn("会话记忆降级截断失败: {}", e.getMessage());
        }
    }

    private boolean isSummary(Turn turn) {
        return ROLE_SUMMARY.equals(turn.getRole());
    }

    private List<Turn> parse(List<String> jsons) {
        if (jsons == null || jsons.isEmpty()) {
            return List.of();
        }
        return jsons.stream()
                .map(j -> {
                    try {
                        return objectMapper.readValue(j, Turn.class);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 一轮对话（角色 + 内容）；role=summary 表示压缩产生的摘要轮次 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Turn {
        /** user / assistant / summary */
        private String role;
        private String content;
    }
}
