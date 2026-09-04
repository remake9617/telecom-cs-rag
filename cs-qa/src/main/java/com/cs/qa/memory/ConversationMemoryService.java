package com.cs.qa.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 会话短期记忆（Redis）——保存每个会话最近 N 轮对话，供「问题重写」和「答案生成」提供多轮上下文。
 *
 * <p>设计（DESIGN 5.3）：</p>
 * <ul>
 *   <li><b>双层存储</b>：MySQL(chat_message) 存完整历史用于持久化与展示；Redis 只存"活跃上下文"，读写快、天然带 TTL。</li>
 *   <li><b>滑动窗口</b>：Redis List 用 rightPush 追加、trim 只保留最近 MAX_TURNS 条，防止上下文无限增长撑爆模型窗口。</li>
 *   <li><b>Key</b>：conv:memory:{conversationId}；TTL 6 小时（会话记忆是短期的）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private static final String KEY_PREFIX = "conv:memory:";
    /** 保留的最近消息条数（约 5 轮一问一答） */
    private static final int MAX_TURNS = 10;
    /** 记忆过期时间：会话闲置 6 小时后自动清理 */
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 追加一条消息到会话记忆，并裁剪为最近 MAX_TURNS 条 */
    public void append(Long conversationId, String role, String content) {
        String key = KEY_PREFIX + conversationId;
        try {
            String json = objectMapper.writeValueAsString(new Turn(role, content));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_TURNS, -1);   // 只保留最后 MAX_TURNS 条
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.warn("写入会话记忆失败: conv={}, err={}", conversationId, e.getMessage());
        }
    }

    /** 取最近 n 条记忆（时间正序：旧→新），用于拼接多轮上下文 */
    public List<Turn> getRecent(Long conversationId, int n) {
        String key = KEY_PREFIX + conversationId;
        List<String> jsons = redisTemplate.opsForList().range(key, -n, -1);
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

    /** 清空某会话的记忆 */
    public void clear(Long conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /** 一轮对话（角色 + 内容） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Turn {
        /** user / assistant */
        private String role;
        private String content;
    }
}
