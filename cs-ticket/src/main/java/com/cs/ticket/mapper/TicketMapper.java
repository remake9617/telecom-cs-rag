package com.cs.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.ticket.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单 Mapper。
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
