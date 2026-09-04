package com.cs.stats.dto;

import lombok.Data;

/**
 * 热点问题行（rest-api 第 6 节 [{question, count}]）。
 */
@Data
public class HotQuestionVO {

    /** 用户原始问题内容 */
    private String question;

    /** 提问次数 */
    private Long count;
}
