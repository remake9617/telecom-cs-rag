package com.cs.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.stats.entity.StatSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统计快照 Mapper。
 */
@Mapper
public interface StatSnapshotMapper extends BaseMapper<StatSnapshot> {
}
