package com.cs.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.stats.dto.DailyCountRow;
import com.cs.stats.dto.HotQuestionVO;
import com.cs.stats.dto.OverviewVO;
import com.cs.stats.dto.TrendVO;
import com.cs.stats.entity.StatSnapshot;
import com.cs.stats.mapper.StatSnapshotMapper;
import com.cs.stats.mapper.StatsMapper;
import com.cs.stats.service.StatsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计服务实现。
 *
 * <p>口径详见 {@link OverviewVO}。trend 缺数日补零：聚合 SQL 只返回有数据的日期，
 * 直接返回会断线，前端画折线图需要连续日期轴，故在服务层按日历补齐。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StatsMapper statsMapper;
    private final StatSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;

    @Override
    public OverviewVO overview() {
        OverviewVO vo = new OverviewVO();
        long askCount = statsMapper.countUserMessages();
        long like = statsMapper.countFeedbackByType("LIKE");
        long dislike = statsMapper.countFeedbackByType("DISLIKE");
        long ticketCount = statsMapper.countTickets();

        vo.setAskCount(askCount);
        // 无反馈样本不硬造 0%——null 表示"暂无数据"，前端展示"-"
        vo.setResolveRate(like + dislike == 0 ? null : (double) like / (like + dislike));
        vo.setTicketRate(askCount == 0 ? 0.0 : (double) ticketCount / askCount);
        vo.setOpenTicketCount(statsMapper.countOpenTickets());
        return vo;
    }

    @Override
    public List<HotQuestionVO> hotQuestions(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR, "limit 取值 1~100");
        }
        return statsMapper.selectHotQuestions(limit);
    }

    @Override
    public List<TrendVO> trend(int days) {
        if (days <= 0 || days > 90) {
            throw new BizException(ErrorCode.PARAM_ERROR, "days 取值 1~90");
        }
        LocalDate today = LocalDate.now();
        String startTime = today.minusDays(days - 1L).atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME);

        Map<String, Long> askByDay = statsMapper.countDailyAsks(startTime).stream()
                .collect(Collectors.toMap(DailyCountRow::getDate, DailyCountRow::getCnt));
        Map<String, Long> likeByDay = statsMapper.countDailyLikes(startTime).stream()
                .collect(Collectors.toMap(DailyCountRow::getDate, DailyCountRow::getCnt));

        List<TrendVO> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String day = today.minusDays(days - 1L - i).format(DAY);
            TrendVO vo = new TrendVO();
            vo.setDate(day);
            vo.setAskCount(askByDay.getOrDefault(day, 0L));
            vo.setResolveCount(likeByDay.getOrDefault(day, 0L));
            result.add(vo);
        }
        return result;
    }

    @Override
    public StatSnapshot snapshotToday() {
        LocalDate today = LocalDate.now();
        List<HotQuestionVO> hot = statsMapper.selectHotQuestions(10);
        StatSnapshot snapshot = new StatSnapshot();
        snapshot.setStatDate(today);
        snapshot.setAskCount((int) statsMapper.countUserMessages());
        snapshot.setResolveCount((int) statsMapper.countFeedbackByType("LIKE"));
        snapshot.setTicketCount((int) statsMapper.countTickets());
        snapshot.setHotQuestions(toJson(hot));

        // uk_date 唯一：当天已有快照则覆盖更新（幂等，任务重跑安全）
        StatSnapshot existing = snapshotMapper.selectOne(
                new LambdaQueryWrapper<StatSnapshot>().eq(StatSnapshot::getStatDate, today));
        if (existing != null) {
            snapshot.setId(existing.getId());
            snapshotMapper.updateById(snapshot);
        } else {
            snapshotMapper.insert(snapshot);
        }
        log.info("统计快照归档: date={}, askCount={}, resolveCount={}, ticketCount={}",
                today, snapshot.getAskCount(), snapshot.getResolveCount(), snapshot.getTicketCount());
        return snapshot;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("热点问题序列化失败", e);
            return "[]";
        }
    }
}
