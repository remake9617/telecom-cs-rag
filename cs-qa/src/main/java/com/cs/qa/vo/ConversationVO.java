package com.cs.qa.vo;

import com.cs.qa.entity.ChatConversation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图对象（对齐 rest-api.md 第 28/93 行 {@code ConversationVO{id, title, lastActiveAt}}）。
 *
 * <p><b>刻意不含 {@code userId}、{@code createdAt}</b>：契约与前端类型都没有这两个字段，
 * 前端 grep {@code userId} 命中 0，删除零影响；会话列表只需标题与最近活跃时间即可渲染。</p>
 *
 * <p>{@code lastActiveAt} 直接用 {@link LocalDateTime}：全局 {@code JacksonConfig} 已统一序列化为
 * {@code yyyy-MM-dd HH:mm:ss}，此处不加 {@code @JsonFormat}、不自行转 String。</p>
 */
@Data
public class ConversationVO {

    private Long id;

    /** 会话标题；源实体为 null 或空串时回填「新会话」 */
    private String title;

    /** 最近活跃时间（会话列表按此倒序） */
    private LocalDateTime lastActiveAt;

    /**
     * 实体 → VO（唯一映射入口）。
     *
     * <p>title 为 null 或空串时回填「新会话」：前端 {@code ChatPage.tsx:189} 直接渲染 {@code {c.title}}，
     * null 虽不崩但列表项会没标题，影响观感。</p>
     *
     * @param c 会话实体，可能为 null（防御性处理）
     * @return 非 null 的会话视图
     */
    public static ConversationVO from(ChatConversation c) {
        ConversationVO vo = new ConversationVO();
        if (c == null) {
            vo.setTitle("新会话");
            return vo;
        }
        vo.setId(c.getId());
        String title = c.getTitle();
        vo.setTitle(title != null && !title.isEmpty() ? title : "新会话");
        vo.setLastActiveAt(c.getLastActiveAt());
        return vo;
    }
}
