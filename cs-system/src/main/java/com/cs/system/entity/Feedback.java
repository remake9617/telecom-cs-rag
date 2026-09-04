package com.cs.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 点赞点踩反馈（对应表 feedback）。
 *
 * <p>MVP 仅落库（D10/D15），作为阶段二 RAG 评估体系的真实反馈数据来源。</p>
 */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被反馈的消息（chat_message.id，通常为 assistant 回答） */
    private Long messageId;

    /** 反馈人（sys_user.id） */
    private Long userId;

    /** 类型：LIKE / DISLIKE */
    private String type;

    /** 可选文字反馈 */
    private String comment;

    private LocalDateTime createdAt;
}
