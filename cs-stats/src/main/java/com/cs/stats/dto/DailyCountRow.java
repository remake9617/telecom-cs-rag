package com.cs.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Mapper 每日聚合计数行（chat_message 按天提问数 / feedback 按天点赞数共用）。
 */
@Data
@AllArgsConstructor
public class DailyCountRow {

    /** yyyy-MM-dd */
    private String date;

    private Long cnt;
}
