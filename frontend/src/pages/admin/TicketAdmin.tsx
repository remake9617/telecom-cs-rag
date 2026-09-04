import { useState } from 'react';
import { Button, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useReplyTicket, useTicketList } from '@/hooks/useTickets';
import { formatDateTime } from '@/utils/format';
import type { TicketStatus, TicketVO } from '@/types';

const { Text, Paragraph } = Typography;

const STATUS_META: Record<TicketStatus, { color: string; label: string }> = {
  OPEN: { color: 'orange', label: '待处理' },
  REPLIED: { color: 'green', label: '已回复' },
  CLOSED: { color: 'default', label: '已关闭' },
};

// 工单管理（管理员）：按状态筛选 + 分页 + 后台回复。
export default function TicketAdmin() {
  const [status, setStatus] = useState<TicketStatus | undefined>();
  const [page, setPage] = useState({ current: 1, size: 10 });
  const { data, isLoading } = useTicketList({ status, current: page.current, size: page.size });
  const reply = useReplyTicket();

  const [target, setTarget] = useState<TicketVO | null>(null);
  const [replyText, setReplyText] = useState('');

  const openReply = (t: TicketVO): void => {
    setTarget(t);
    setReplyText(t.reply ?? '');
  };

  const submitReply = (): void => {
    if (!target || !replyText.trim()) return;
    reply.mutate(
      { id: target.id, reply: replyText.trim() },
      {
        onSuccess: () => {
          setTarget(null);
          setReplyText('');
        },
      },
    );
  };

  const columns: ColumnsType<TicketVO> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '问题', dataIndex: 'question', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: TicketStatus) => {
        const m = STATUS_META[v] ?? { color: 'default', label: v };
        return <Tag color={m.color}>{m.label}</Tag>;
      },
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (v: string) => formatDateTime(v) },
    { title: '回复时间', dataIndex: 'repliedAt', width: 180, render: (v?: string) => formatDateTime(v) },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: unknown, record) => (
        <Button type="link" size="small" onClick={() => openReply(record)}>
          {record.reply ? '查看/编辑回复' : '回复'}
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Text strong style={{ fontSize: 16 }}>
          工单管理
        </Text>
        <Select
          allowClear
          placeholder="按状态筛选"
          style={{ width: 160 }}
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage((p) => ({ ...p, current: 1 }));
          }}
          options={[
            { value: 'OPEN', label: '待处理' },
            { value: 'REPLIED', label: '已回复' },
            { value: 'CLOSED', label: '已关闭' },
          ]}
        />
      </Space>

      <Table<TicketVO>
        rowKey="id"
        columns={columns}
        dataSource={data?.records}
        loading={isLoading}
        scroll={{ x: 900 }}
        expandable={{
          expandedRowRender: (t) => (
            <div style={{ paddingInline: 8 }}>
              {t.aiReason && (
                <Paragraph style={{ marginBottom: 4 }}>
                  <Text type="secondary">转人工原因：</Text>
                  {t.aiReason}
                </Paragraph>
              )}
              {t.reply ? (
                <Paragraph style={{ marginBottom: 0 }}>
                  <Text type="secondary">客服回复：</Text>
                  {t.reply}
                </Paragraph>
              ) : (
                <Text type="secondary">暂无回复</Text>
              )}
            </div>
          ),
        }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total: data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <Modal
        open={!!target}
        title={`回复工单 #${target?.id ?? ''}`}
        onOk={submitReply}
        onCancel={() => setTarget(null)}
        confirmLoading={reply.isPending}
        okText="发送回复"
        cancelText="取消"
        okButtonProps={{ disabled: !replyText.trim() }}
      >
        <Paragraph type="secondary" style={{ marginTop: 0 }}>
          用户问题：{target?.question}
        </Paragraph>
        <Input.TextArea
          rows={4}
          value={replyText}
          onChange={(e) => setReplyText(e.target.value)}
          placeholder="输入回复内容，发送后用户可在「我的工单」查看"
          maxLength={500}
          showCount
        />
      </Modal>
    </div>
  );
}
