package com.cs.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.system.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 反馈 Mapper。
 *
 * <p>messageId 存在性校验用只读 SQL 直接查 chat_message 表——cs-system 按依赖契约
 * 只能依赖 cs-framework，不能反向依赖 cs-qa 的 Service/Mapper；
 * 跨模块表级只读是统计/系统类模块的常规做法（M3-report 说明）。</p>
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /** 校验被反馈的消息是否存在（只读，不写 chat_message） */
    @Select("SELECT COUNT(*) FROM chat_message WHERE id = #{messageId}")
    int countMessageById(Long messageId);
}
