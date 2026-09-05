package com.cs.qa.vo;

import com.cs.qa.entity.ChatMessage;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 消息视图对象（对齐 rest-api.md 第 29/94 行
 * {@code MessageVO{id, role(user/assistant), content, references?, createdAt}}，
 * 并新增 {@code tokenCost} 用于修复「流式结束显示 N tokens、刷新后标签消失」）。
 *
 * <p><b>刻意不含 {@code conversationId}、{@code rewrittenQuery}、{@code intent}</b>：
 * 后两者是 RAG 链路的内部调试数据（问题重写结果、意图分类），<b>不应下发给普通用户</b>——
 * 既是信息泄露面（暴露内部检索意图判定），也是无意义载荷；conversationId 前端从路由已知，无需回传。</p>
 *
 * <p>{@code createdAt} 直接用 {@link LocalDateTime}：全局 {@code JacksonConfig} 已统一序列化为
 * {@code yyyy-MM-dd HH:mm:ss}，不加 {@code @JsonFormat}、不自行转 String。</p>
 */
@Data
public class MessageVO {

    /**
     * 消息 ID，<b>必须非 null</b>：前端 {@code MessageBubble.tsx:39} 的
     * {@code canFeedback = typeof message.id === 'number'}，缺 id 会让点赞点踩按钮静默变灰。
     */
    private Long id;

    /**
     * 角色，<b>必须小写</b> {@code user}/{@code assistant}：前端 {@code MessageBubble.tsx:36}
     * 是 {@code isUser = message.role === 'user'} 严格比较，大写 {@code USER} 会把用户消息
     * 渲染成左侧机器人气泡，整个对话视觉错乱。
     */
    private String role;

    private String content;

    /**
     * 引用溯源列表；对 {@code role='user'} 的消息可为 null 或空列表
     * （前端 {@code ReferenceList.tsx:15} 对空数组返回 null，安全）。
     */
    private List<ReferenceVO> references;

    private LocalDateTime createdAt;

    /**
     * 本条回答消耗的 token（仅 assistant 消息有意义）。
     * 本轮对契约的<b>新增字段</b>，数据源 {@code chat_message.token_cost}；
     * 前端 {@code MessageBubble.tsx:177} 用 {@code typeof === 'number'} 守卫，为 null 时不显示标签。
     */
    private Integer tokenCost;

    /**
     * 实体 + 引用列表 → VO（唯一映射入口）。
     *
     * @param m          消息实体，可能为 null（防御性处理）
     * @param references 已批量回填并组装好的引用视图列表，可为 null
     * @return 非 null 的消息视图
     */
    public static MessageVO from(ChatMessage m, List<ReferenceVO> references) {
        MessageVO vo = new MessageVO();
        if (m == null) {
            vo.setReferences(references);
            return vo;
        }
        vo.setId(m.getId());
        // 防御性统一小写：DB 存的本就是小写，但严格比较下大小写漂移会导致气泡错位，故在此兜底
        vo.setRole(m.getRole() != null ? m.getRole().toLowerCase(Locale.ROOT) : null);
        vo.setContent(m.getContent());
        vo.setReferences(references);
        vo.setCreatedAt(m.getCreatedAt());
        vo.setTokenCost(m.getTokenCost());
        return vo;
    }
}
