package com.cs.stats.task;

import com.cs.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计快照定时任务（M3 引入 @EnableScheduling）。
 *
 * <p>每日 00:05 归档前一日的核心指标与热点问题 TopN 到 stat_snapshot（uk_date 唯一、
 * 幂等覆盖）。任务失败不影响业务主链——快照是旁路数据，次日重跑或手动补档即可。</p>
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SnapshotTask {

    private final StatsService statsService;

    /** 每日 00:05 执行（cron：秒 分 时 日 月 周） */
    @Scheduled(cron = "0 5 0 * * ?")
    public void archiveDaily() {
        try {
            statsService.snapshotToday();
        } catch (Exception e) {
            // 定时任务不允许异常外抛导致线程终止，记录后下次触发自动重试
            log.error("统计快照任务失败", e);
        }
    }
}
