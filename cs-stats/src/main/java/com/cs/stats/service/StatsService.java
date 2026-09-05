package com.cs.stats.service;

import com.cs.stats.dto.HotQuestionVO;
import com.cs.stats.dto.OverviewVO;
import com.cs.stats.dto.TrendVO;
import com.cs.stats.entity.StatSnapshot;

import java.util.List;

/**
 * 统计看板服务（rest-api 第 6 节，管理员）。
 *
 * <p>实时接口直查业务表聚合（演示数据新鲜）；另提供每日快照归档，
 * 供历史回看与阶段二评估体系使用。</p>
 */
public interface StatsService {

    /**
     * 概览：咨询量 / 解决率 / 转人工率 / 待处理工单 / 知识库数 / 文档数 / 用户数。
     *
     * <p>字段口径与可空性见 {@link OverviewVO}（resolveRate 无样本时返回 null，
     * 不硬造 0%；前端据此展示“-”）。</p>
     */
    OverviewVO overview();

    /** 热点问题 TopN */
    List<HotQuestionVO> hotQuestions(int limit);

    /** 近 N 天趋势（缺数日补零，保证曲线连续） */
    List<TrendVO> trend(int days);

    /** 归档当日快照（uk_date 唯一，重复执行覆盖更新）；由定时任务调用 */
    StatSnapshot snapshotToday();
}
