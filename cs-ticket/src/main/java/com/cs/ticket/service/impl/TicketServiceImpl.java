package com.cs.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.common.PageVO;
import com.cs.framework.exception.BizException;
import com.cs.ticket.entity.Ticket;
import com.cs.ticket.mapper.TicketMapper;
import com.cs.ticket.service.TicketService;
import com.cs.ticket.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单服务实现。
 *
 * <p>状态机（MVP 最简闭环，D9）：OPEN --管理员回复--> REPLIED；CLOSED 预留阶段二坐席台。
 * 重复回复已 REPLIED 的工单直接以最新内容覆盖（运营场景常见「补充回复」，
 * 严格禁止反而增加交互成本），故仅在 CLOSED 时抛状态非法。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    /** 状态常量：对齐 schema.sql ticket.status 注释 */
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_REPLIED = "REPLIED";
    public static final String STATUS_CLOSED = "CLOSED";

    private final TicketMapper ticketMapper;

    @Override
    public Ticket createTicket(Long userId, Long conversationId, String question, String aiReason) {
        Ticket ticket = new Ticket();
        ticket.setUserId(userId);
        ticket.setConversationId(conversationId);
        ticket.setQuestion(question);
        ticket.setAiReason(aiReason);
        ticket.setStatus(STATUS_OPEN);
        ticketMapper.insert(ticket);
        log.info("创建工单: id={}, userId={}, conversationId={}", ticket.getId(), userId, conversationId);
        return ticket;
    }

    @Override
    public TicketVO reply(Long ticketId, String reply, Long handlerId) {
        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND);
        }
        if (STATUS_CLOSED.equals(ticket.getStatus())) {
            throw new BizException(ErrorCode.TICKET_STATE_ILLEGAL, "工单已关闭，不能再回复");
        }
        ticket.setStatus(STATUS_REPLIED);
        ticket.setReply(reply);
        ticket.setHandlerId(handlerId);
        ticket.setRepliedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        log.info("工单回复: id={}, handlerId={}", ticketId, handlerId);
        return toVO(ticket);
    }

    @Override
    public List<TicketVO> listMine(Long userId) {
        List<Ticket> tickets = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getUserId, userId)
                        .orderByDesc(Ticket::getId));
        return tickets.stream().map(this::toVO).toList();
    }

    @Override
    public PageVO<TicketVO> listByStatus(String status, long current, long size) {
        QueryWrapper<Ticket> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("id");
        Page<Ticket> page = ticketMapper.selectPage(Page.of(current, size), wrapper);
        List<TicketVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageVO.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private TicketVO toVO(Ticket ticket) {
        TicketVO vo = new TicketVO();
        vo.setId(ticket.getId());
        vo.setQuestion(ticket.getQuestion());
        vo.setAiReason(ticket.getAiReason());
        vo.setStatus(ticket.getStatus());
        vo.setReply(ticket.getReply());
        vo.setHandlerId(ticket.getHandlerId());
        vo.setCreatedAt(ticket.getCreatedAt());
        vo.setRepliedAt(ticket.getRepliedAt());
        return vo;
    }
}
