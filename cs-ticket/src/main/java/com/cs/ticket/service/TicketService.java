package com.cs.ticket.service;

import com.cs.framework.common.PageVO;
import com.cs.ticket.entity.Ticket;
import com.cs.ticket.vo.TicketVO;

import java.util.List;

/**
 * 工单服务（M3 客服闭环，D9）。
 *
 * <p><b>跨模块约定</b>：{@link #createTicket} 是给问答链路（cs-qa）调用的公开入口——
 * QaService 意图识别为 TICKET 时由其调用建单（M2 现阶段仅回提示语，
 * 接线工作归统筹会话集成，本模块不反向依赖 cs-qa）。</p>
 */
public interface TicketService {

    /**
     * 创建工单（OPEN 状态）。
     *
     * @param userId         提单用户 ID
     * @param conversationId 来源会话 ID（可空）
     * @param question       用户问题
     * @param aiReason       AI 判定转人工原因（可空）
     * @return 已落库的工单（含自增 ID）
     */
    Ticket createTicket(Long userId, Long conversationId, String question, String aiReason);

    /**
     * 管理员回复工单：OPEN → REPLIED，记录处理人与回复时间。
     *
     * @param ticketId  工单 ID
     * @param reply     回复内容
     * @param handlerId 处理人（管理员）ID
     * @return 回复后的 VO
     */
    TicketVO reply(Long ticketId, String reply, Long handlerId);

    /**
     * 我的工单（数据归属：仅本人创建，按创建时间倒序）。
     */
    List<TicketVO> listMine(Long userId);

    /**
     * 工单分页列表（管理员），可按状态过滤，按创建时间倒序。
     *
     * @param status  状态过滤（可空=全部）
     * @param current 页码（1 起）
     * @param size    每页大小
     */
    PageVO<TicketVO> listByStatus(String status, long current, long size);
}
