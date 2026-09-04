package com.cs.stats.dto;

import lombok.Data;

/**
 * 概览看板（rest-api 第 6 节 GET /api/stats/overview）。
 *
 * <p>口径（M3-report 与 DECISIONS 详述）：</p>
 * <ul>
 *   <li>askCount 咨询量 = chat_message 中 role=user 的消息数；</li>
 *   <li>resolveRate 解决率 = LIKE / (LIKE + DISLIKE)，无反馈时为 null（样本不足不硬造百分比）；</li>
 *   <li>ticketRate 转人工率 = 工单总数 / askCount；</li>
 *   <li>openTicketCount 待处理工单数（status=OPEN），管理后台常用指标。</li>
 * </ul>
 */
@Data
public class OverviewVO {

    private Long askCount;

    /** 0~1 之间的小数，前端渲染为百分比；无反馈样本时为 null */
    private Double resolveRate;

    private Double ticketRate;

    private Long openTicketCount;
}
