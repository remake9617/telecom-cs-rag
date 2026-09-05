package com.cs.ticket.service;

import com.cs.framework.common.PageVO;
import com.cs.ticket.entity.Ticket;
import com.cs.ticket.vo.TicketVO;

import java.util.List;

/**
 * 工单服务（M3 客服闭环，D9）。
 *
 * <p><b>跨模块约定</b>：{@link #createTicket} 是给问答链路（cs-qa）调用的公开入口——
 * QaService 在意图识别为 TICKET、或知识库无召回兜底时调用建单，并把工单号写进 SSE
 * {@code ticket_hint} 事件（接线已完成：cs-qa/pom.xml 已依赖 cs-ticket）。
 * 依赖方向是 cs-qa → cs-ticket，本模块不反向依赖 cs-qa（CONVENTIONS 第 2 节）。</p>
 */
public interface TicketService {

    /**
     * 创建工单（OPEN 状态）。
     *
     * <p>{@code reply} / {@code handlerId} / {@code repliedAt} 新建时为 null（契约标为可空），
     * 由 {@link #reply} 在管理员回复时写入。</p>
     *
     * @param userId         提单用户 ID
     * @param conversationId 来源会话 ID（可空）
     * @param question       用户问题
     * @param aiReason       AI 判定转人工原因（可空：用户手动转人工时无此项）
     * @return 已落库的工单，含自增 ID 与 {@code createdAt}
     *         （实现方必须在 insert 前显式赋值 createdAt，见 TicketServiceImpl 注释）
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

    /**
     * 实体 → 契约 VO 的<b>唯一</b>映射入口（rest-api.md 第 97 行 TicketVO 全字段）。
     *
     * <p>为什么提升为接口方法：Controller 与 Impl 必须共用同一份映射逻辑。
     * 此前 {@code POST /api/ticket} 在 Controller 内手拼 VO，只 set 了
     * {@code id/question/aiReason/status} 四个字段，漏发契约要求的
     * {@code createdAt}/{@code reply}/{@code handlerId}/{@code repliedAt}（DEF-016）。
     * 收口到本方法后，新增字段只需改一处，四个端点自动一致。</p>
     *
     * @param ticket 工单实体（非 null）
     * @return 字段完整的 TicketVO
     */
    TicketVO toVO(Ticket ticket);
}
