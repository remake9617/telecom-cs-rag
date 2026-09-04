package com.cs.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计快照（对应表 stat_snapshot，uk_date 唯一）。
 *
 * <p>每日定时归档当日核心指标与热点问题 TopN（JSON 列），供历史回看与
 * 阶段二评估体系做时序对比；看板实时接口不走快照，保证演示数据新鲜。</p>
 */
@Data
@TableName("stat_snapshot")
public class StatSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期（唯一） */
    private LocalDate statDate;

    private Integer askCount;

    private Integer resolveCount;

    private Integer ticketCount;

    /** 热点问题 TopN（JSON 数组：[{"question":...,"count":...}]） */
    private String hotQuestions;

    private LocalDateTime createdAt;
}
