package com.cs.ticket.controller;

import com.cs.framework.common.PageVO;
import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.ticket.dto.TicketCreateRequest;
import com.cs.ticket.dto.TicketReplyRequest;
import com.cs.ticket.entity.Ticket;
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
 * <p><b>认证与鉴权（已收口，无任何开发期回退）</b>：userId / 角色均取自 {@link SecurityUtils}。
 * 未认证请求（无 token 或 token 失效）由过滤器链在到达本 Controller 前拦截并返回 <b>HTTP 401</b>；
 * {@code requireUserId()} 在上下文缺失时抛 1002（UNAUTHORIZED）；
 * {@code requireAdmin()} 在角色不足时抛 1003（FORBIDDEN）——按 D23，1003 以
 * <b>HTTP 200 + code=1003</b> 下发（统一 R 体系），前端提示“无权限”而不跳登录。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * 创建工单（AI 判定或用户手动转人工）。
     *
     * <p>手动建单时 {@code aiReason} 传 null：本端点不经意图识别环节，无真实判定理由可填，
     * 不为凑字段而编造内容（契约将其标为可空，前端已兜底）；
     * {@code reply}/{@code handlerId}/{@code repliedAt} 新建时同为 null。</p>
     *
     * <p>返回 VO 统一走 {@link TicketService#toVO(Ticket)}，不在 Controller 内手拼字段：
     * 手拼曾造成本端点漏发 {@code createdAt} 等契约字段（DEF-016），
     * 而其余三个端点（mine/list/reply）字段完整，形成同一 VO 两种口径。</p>
     */
    @PostMapping
    public R<TicketVO> create(@Valid @RequestBody TicketCreateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        Ticket ticket = ticketService.createTicket(
                userId, request.getConversationId(), request.getQuestion(), null);
        return R.ok(ticketService.toVO(ticket));
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
