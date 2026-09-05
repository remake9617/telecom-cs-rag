package com.cs.ticket.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单视图对象（对齐 rest-api.md 第 4 节与第 97 行 TicketVO 速查，前端 Mock 依据）。
 *
 * <p>字段可空性与契约一致：{@code reply} / {@code handlerId} / {@code repliedAt} 带 {@code ?}，
 * 新建（OPEN）时为 null，管理员回复后置值；{@code aiReason} 仅 AI 自动建单时非 null。
 * 前端消费点（MyTickets.tsx、TicketAdmin.tsx）均已做 null 兜底。</p>
 *
 * <p><b>唯一映射入口</b>：{@code TicketService#toVO(Ticket)}，禁止在 Controller 内手拼字段。</p>
 */
@Data
public class TicketVO {

    private Long id;

    /** 用户原始问题 */
    private String question;

    /** AI 判定转人工的原因；用户手动建单时为 null */
    private String aiReason;

    /** OPEN / REPLIED / CLOSED */
    private String status;

    /** 管理员回复内容；未回复时为 null */
    private String reply;

    /** 处理人（管理员）ID；未回复时为 null（前端当前零渲染点，补齐为契约完整性） */
    private Long handlerId;

    /** 建单时间；由服务层在 insert 前显式赋值（MP 不回读 DB 列默认值） */
    private LocalDateTime createdAt;

    /** 回复时间；未回复时为 null */
    private LocalDateTime repliedAt;
}
