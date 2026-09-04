package com.cs.ticket.controller;

import com.cs.framework.common.PageVO;
import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.ticket.dto.TicketCreateRequest;
import com.cs.ticket.dto.TicketReplyRequest;
import com.cs.ticket.service.TicketService;
import com.cs.ticket.vo.TicketVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单接口（对应 contract/rest-api.md 第 4 节 /api/ticket）。
 *
 * <p>userId / 角色取自 {@link SecurityUtils}（认证收口前回退开发默认用户，见该类注释）；
 * 管理员接口用编程式 {@code requireAdmin()} 校验，错误统一走 R 体系。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /** 创建工单（AI 判定或用户手动转人工；MVP 手动建单 aiReason 为空） */
    @PostMapping
    public R<TicketVO> create(@Valid @RequestBody TicketCreateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        var ticket = ticketService.createTicket(
                userId, request.getConversationId(), request.getQuestion(), null);
        TicketVO vo = new TicketVO();
        vo.setId(ticket.getId());
        vo.setQuestion(ticket.getQuestion());
        vo.setAiReason(ticket.getAiReason());
        vo.setStatus(ticket.getStatus());
        return R.ok(vo);
    }

    /** 我的工单（访客） */
    @GetMapping("/mine")
    public R<List<TicketVO>> mine() {
        return R.ok(ticketService.listMine(SecurityUtils.requireUserId()));
    }

    /** 工单列表（管理员，可按状态过滤分页） */
    @GetMapping
    public R<PageVO<TicketVO>> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size) {
        SecurityUtils.requireAdmin();
        return R.ok(ticketService.listByStatus(status, current, size));
    }

    /** 后台回复工单（管理员） */
    @PutMapping("/{id}/reply")
    public R<TicketVO> reply(@PathVariable Long id, @Valid @RequestBody TicketReplyRequest request) {
        SecurityUtils.requireAdmin();
        return R.ok(ticketService.reply(id, request.getReply(), SecurityUtils.requireUserId()));
    }
}
