package com.cs.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单视图对象（对齐 rest-api 第 4 节 TicketVO，前端 Mock 依据）。
 */
@Data
public class TicketVO {

    private Long id;

    private String question;

    private String aiReason;

    /** OPEN / REPLIED / CLOSED */
    private String status;

    private String reply;

    private Long handlerId;

    private LocalDateTime createdAt;

    private LocalDateTime repliedAt;
}
