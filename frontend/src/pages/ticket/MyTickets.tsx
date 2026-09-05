import { useState } from 'react';
import { Button, Card, Input, List, Modal, Space, Tag, Typography } from 'antd';
import { CustomerServiceOutlined } from '@ant-design/icons';
import StateBlock from '@/components/common/StateBlock';
import { useCreateTicket, useMyTickets } from '@/hooks/useTickets';
import { formatDateTime } from '@/utils/format';
import type { TicketStatus } from '@/types';

const { Text, Paragraph } = Typography;

// 工单状态展示元数据
const STATUS_META: Record<TicketStatus, { color: string; label: string }> = {
  OPEN: { color: 'orange', label: '待处理' },
  REPLIED: { color: 'green', label: '已回复' },
  CLOSED: { color: 'default', label: '已关闭' },
};

// 我的工单（访客）：查看自己提交的工单与后台回复，也可手动提交新工单。
export default function MyTickets() {
  const { data, isLoading, error, refetch } = useMyTickets();
  const createTicket = useCreateTicket();
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState('');

  const submit = (): void => {
    const q = question.trim();
    if (!q) return;
    createTicket.mutate(
      { question: q },
      {
        onSuccess: () => {
          setOpen(false);
          setQuestion('');
        },
      },
    );
  };

  return (
    <div style={{ height: '100%', overflow: 'auto', padding: 16 }}>
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
          <Text strong style={{ fontSize: 16 }}>
            我的工单
          </Text>
          <Button type="primary" icon={<CustomerServiceOutlined />} onClick={() => setOpen(true)}>
            提交工单
          </Button>
        </Space>

        <StateBlock
          loading={isLoading}
          error={error}
          onRetry={refetch}
          empty={!data || data.length === 0}
          emptyText="暂无工单，遇到问题可点击右上角提交"
        >
          <List
            dataSource={data}
            renderItem={(t) => {
              const meta = STATUS_META[t.status] ?? { color: 'default', label: t.status };
              return (
                <List.Item style={{ border: 'none', padding: 0, marginBottom: 12 }}>
                  <Card size="small" style={{ width: '100%' }}>
                    <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
                      <Text strong>{t.question}</Text>
                      <Tag color={meta.color}>{meta.label}</Tag>
                    </Space>
                    <div style={{ marginTop: 4 }}>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        提交于 {formatDateTime(t.createdAt)}
                        {t.aiReason ? ` · 转人工原因：${t.aiReason}` : ''}
                      </Text>
                    </div>
                    {t.reply && (
                      <div
                        style={{
                          marginTop: 10,
                          padding: '8px 12px',
                          borderRadius: 8,
                          background: 'rgba(22,119,255,0.08)',
                        }}
                      >
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          客服回复（{formatDateTime(t.repliedAt)}）：
                        </Text>
                        <Paragraph style={{ marginBottom: 0, marginTop: 2 }}>{t.reply}</Paragraph>
                      </div>
                    )}
                  </Card>
                </List.Item>
              );
            }}
          />
        </StateBlock>
      </div>

      <Modal
        open={open}
        title="提交人工工单"
        onOk={submit}
        onCancel={() => setOpen(false)}
        confirmLoading={createTicket.isPending}
        okText="提交"
        cancelText="取消"
        okButtonProps={{ disabled: !question.trim() }}
      >
        <Input.TextArea
          rows={4}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="请描述你的问题，客服会尽快回复"
          maxLength={500}
          showCount
        />
      </Modal>
    </div>
  );
}
