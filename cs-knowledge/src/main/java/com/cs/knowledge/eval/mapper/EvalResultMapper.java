package com.cs.knowledge.eval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.knowledge.eval.entity.EvalResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评测运行结果 Mapper（路9 评估体系）：仅常规 CRUD（insert 建运行 / updateById 回写指标 / select 查历史）。
 */
@Mapper
public interface EvalResultMapper extends BaseMapper<EvalResult> {
}
