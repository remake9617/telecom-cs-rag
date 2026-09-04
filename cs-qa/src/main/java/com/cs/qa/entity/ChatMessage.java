package com.cs.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息（对应表 chat_message）。role=user/assistant 交替，记录一问一答。
 *
 * <p>{@code rewrittenQuery} 保存问题重写结果、{@code intent} 保存意图识别结果，
 * 便于回溯 RAG 链路每一步（也是答辩展示"问题理解"的数据）。</p>
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    private String content;

    /** 问题重写后的 query（仅 user 消息有） */
    private String rewrittenQuery;

    /** 意图：KB(知识库) / TICKET(转人工) / CHITCHAT(闲聊) */
    private String intent;

    /** 本条回答消耗的 token（assistant 消息） */
    private Integer tokenCost;

    private LocalDateTime createdAt;
}
