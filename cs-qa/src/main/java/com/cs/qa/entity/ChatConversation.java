package com.cs.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（对应表 chat_conversation）。一个用户可有多个会话，每个会话含多轮消息。
 */
@Data
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 会话标题（一般取首个问题或摘要） */
    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;
}
