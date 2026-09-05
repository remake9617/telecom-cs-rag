package com.cs.stats.dto;

import lombok.Data;

/**
 * 概览看板（rest-api.md 第 6 节 GET /api/stats/overview）。
 *
 * <p>口径（D24 与 M3-report 详述，本轮不改动既有四项）：</p>
 * <ul>
 *   <li>askCount 咨询量 = chat_message 中 role=user 的消息数；</li>
 *   <li>resolveRate 解决率 = LIKE / (LIKE + DISLIKE)，无反馈时为 null（样本不足不硬造百分比）；</li>
 *   <li>ticketRate 转人工率 = 工单总数 / askCount，askCount 为 0 时为 null（与 resolveRate 对称，不硬造 0%）；</li>
 *   <li>openTicketCount 待处理工单数（status=OPEN），管理后台常用指标；</li>
 *   <li>kbCount / docCount / userCount 存量规模三项，支撑看板「知识库 / 文档」卡
 *       （前端 Dashboard.tsx 直接消费 kbCount / docCount，后端不发则恒显示 0 / 0）。</li>
 * </ul>
 *
 * <p><b>本轮不做</b>：{@code avgTokenCost}——D10 把 token 成本分析归入阶段二，
 * 且前端零消费点（types 中的 {@code avgTokenCost?} 为死字段），不实现、不下发。</p>
 */
@Data
public class OverviewVO {

    private Long askCount;

    /** 0~1 之间的小数，前端渲染为百分比；无反馈样本时为 null（有意设计，前端显示“-”） */
    private Double resolveRate;

    /** 0~1 之间的小数，前端渲染为百分比；askCount 为 0 时为 null（与 resolveRate 对称，DEF-070），前端 formatPercent 显示「-」 */
    private Double ticketRate;

    private Long openTicketCount;

    /** 知识库数 = kb_knowledge_base 表行数（含停用库，反映存量规模而非可用数） */
    private Long kbCount;

    /** 文档数 = kb_document 表行数（含 PENDING/PROCESSING/FAILED，即全部已登记文档） */
    private Long docCount;

    /** 用户数 = sys_user 表行数（含禁用账号，反映注册规模） */
    private Long userCount;
}
