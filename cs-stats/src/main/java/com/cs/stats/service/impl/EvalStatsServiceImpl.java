package com.cs.stats.service.impl;

import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.stats.dto.EvalRunVO;
import com.cs.stats.mapper.EvalRunMapper;
import com.cs.stats.service.EvalStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评测结果看板读侧服务实现：纯只读直查，组装逻辑仅参数校验（口径同 StatsServiceImpl）。
 */
@Service
@RequiredArgsConstructor
public class EvalStatsServiceImpl implements EvalStatsService {

    private final EvalRunMapper evalRunMapper;

    @Override
    public List<EvalRunVO> recentRuns(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR, "limit 取值 1~100");
        }
        return evalRunMapper.selectRecentRuns(limit);
    }
}
