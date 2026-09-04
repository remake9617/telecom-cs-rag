package com.cs.stats.dto;

import lombok.Data;

/**
 * 趋势行（rest-api 第 6 节 GET /api/stats/trend → [{date, askCount, resolveCount}]）。
 */
@Data
public class TrendVO {

    /** yyyy-MM-dd */
    private String date;

    /** 当日提问数（chat_message role=user） */
    private Long askCount;

    /** 当日点赞数（feedback type=LIKE，解决口径的日粒度体现） */
    private Long resolveCount;
}
