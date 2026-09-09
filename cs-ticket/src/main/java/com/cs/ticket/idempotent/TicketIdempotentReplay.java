package com.cs.ticket.idempotent;

import com.cs.framework.common.R;
import com.cs.framework.idempotent.IdempotentReplay;
import com.cs.ticket.entity.Ticket;
import com.cs.ticket.mapper.TicketMapper;
import com.cs.ticket.service.TicketService;
import com.cs.ticket.vo.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 建单幂等的结果还原提供方（路10）：幂等命中时按存量 ticketId 查库还原 {@code R<TicketVO>}。
 *
 * <p>为什么放 cs-ticket 而不在切面里：幂等切面在 cs-framework（最底层模块），依赖方向
 * cs-ticket → cs-framework，框架不能反向依赖业务 VO；键的组成与结果的还原都是业务知识，
 * 由业务模块通过 {@link IdempotentReplay} 接口提供给切面。</p>
 */
@Component
@RequiredArgsConstructor
public class TicketIdempotentReplay implements IdempotentReplay {

    private final TicketMapper ticketMapper;
    private final TicketService ticketService;

    /**
     * @param storedValue Redis 占位 key 中回写的 ticketId 字符串
     * @return 工单仍存在 → {@code R.ok(TicketVO)}（与正常建单响应同形，前端零感知）；
     *         工单已被删除 → null（切面降级为正常建单）
     */
    @Override
    public Object resolve(String storedValue) {
        Ticket ticket = ticketMapper.selectById(Long.valueOf(storedValue));
        if (ticket == null) {
            return null;
        }
        // 与 TicketController.create 相同的映射口径：toVO 是实体→契约 VO 的唯一入口
        return R.ok(ticketService.toVO(ticket));
    }
}
