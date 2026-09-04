package com.cs.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转人工工单（对应表 ticket）。
 *
 * <p>闭环（D9）：AI 判定答不了或用户手动转人工 → 生成工单（OPEN）→
 * 管理员后台回复（REPLIED）→ 用户查看回复；CLOSED 为后续状态流转预留。</p>
 */
@Data
@TableName("ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提单人（sys_user.id） */
    private Long userId;

    /** 来源会话（chat_conversation.id，可空：非对话场景手动建单） */
    private Long conversationId;

    /** 用户原始问题 */
    private String question;

    /** AI 判定转人工的原因（意图识别/低置信说明，可空） */
    private String aiReason;

    /** 状态：OPEN / REPLIED / CLOSED */
    private String status;

    /** 管理员回复内容 */
    private String reply;

    /** 处理人（管理员 sys_user.id） */
    private Long handlerId;

    private LocalDateTime createdAt;

    private LocalDateTime repliedAt;
}
