import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ticketApi, type CreateTicketParams, type TicketListParams } from '@/api';
import { notify } from '@/utils/notify';
import { queryKeys } from './queryKeys';

// 工单：访客「我的工单」+ 管理员「工单列表/后台回复」。

export function useMyTickets() {
  return useQuery({ queryKey: queryKeys.myTickets, queryFn: ticketApi.mine });
}

export function useCreateTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: CreateTicketParams) => ticketApi.create(params),
    onSuccess: () => {
      notify.success('工单已提交，客服会尽快回复');
      qc.invalidateQueries({ queryKey: queryKeys.myTickets });
    },
  });
}

export function useTicketList(params: TicketListParams) {
  return useQuery({ queryKey: queryKeys.tickets(params), queryFn: () => ticketApi.list(params) });
}

export function useReplyTicket() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { id: number; reply: string }) => ticketApi.reply(vars.id, vars.reply),
    onSuccess: () => {
      notify.success('回复已发送');
      qc.invalidateQueries({ queryKey: ['ticket'] });
    },
  });
}
