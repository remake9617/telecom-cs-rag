import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Drawer, Empty, Grid, List, Popconfirm, Space, Spin, Typography } from 'antd';
import { DeleteOutlined, MenuOutlined, MessageOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import MessageBubble from '@/components/chat/MessageBubble';
import Composer from '@/components/chat/Composer';
import { useChatStore } from '@/stores/chatStore';
import {
  useConversationMessages,
  useConversations,
  useDeleteConversation,
} from '@/hooks/useConversations';
import { useChatStream } from '@/hooks/useChatStream';
import { useFeedback } from '@/hooks/useFeedback';
import { useCreateTicket } from '@/hooks/useTickets';
import { formatConversationTime } from '@/utils/format';
import type { FeedbackType } from '@/api';

const { Text, Title } = Typography;

const SUGGESTIONS = [
  '5G 畅享套餐 129 档包含多少流量？',
  '宽带断网了怎么办？',
  '套餐变更是次月生效还是即时生效？',
];

/** 空会话欢迎页：给出可点击的示例问题，降低首用门槛 */
function Welcome({ onPick }: { onPick: (q: string) => void }) {
  return (
    <div style={{ textAlign: 'center', paddingTop: 48 }}>
      <Title level={4} style={{ marginBottom: 4 }}>
        你好，我是电信智能客服
      </Title>
      <Text type="secondary">我可以帮你解答套餐资费、宽带报障、账单规则等问题。试试问我：</Text>
      <Space direction="vertical" style={{ marginTop: 20, width: '100%', maxWidth: 460 }}>
        {SUGGESTIONS.map((q) => (
          <Button key={q} block style={{ height: 'auto', padding: '8px 12px', textAlign: 'left' }} onClick={() => onPick(q)}>
            <Text style={{ whiteSpace: 'normal' }}>{q}</Text>
          </Button>
        ))}
      </Space>
    </div>
  );
}

// 用户对话端主页面：左侧会话列表 + 右侧流式对话区。
// 会话状态与流式缓冲以 chatStore 为单一数据源；列表/历史由 TanStack Query 负责。
export default function ChatPage() {
  const { conversationId: cidParam } = useParams();
  const activeId = cidParam ? Number(cidParam) : null;
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const isMobile = screens.md === false;

  const messages = useChatStore((s) => s.messages);
  const streaming = useChatStore((s) => s.streaming);
  const storeConvId = useChatStore((s) => s.conversationId);
  const hydrate = useChatStore((s) => s.hydrate);
  const newConversation = useChatStore((s) => s.newConversation);

  const conversationsQuery = useConversations();
  const messagesQuery = useConversationMessages(activeId);
  const deleteConv = useDeleteConversation();
  const { send, stop } = useChatStream();
  const feedbackMutate = useFeedback().mutate;
  const createTicketMutate = useCreateTicket().mutate;

  const [drawerOpen, setDrawerOpen] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastHydrated = useRef<number | null | undefined>(undefined);

  // 切换会话：把服务端历史灌入 store；进入「新对话」入口则清空
  useEffect(() => {
    if (activeId == null) {
      if (lastHydrated.current !== null) {
        newConversation();
        lastHydrated.current = null;
      }
      return;
    }
    if (activeId !== lastHydrated.current && messagesQuery.data) {
      hydrate(activeId, messagesQuery.data);
      lastHydrated.current = activeId;
    }
  }, [activeId, messagesQuery.data, hydrate, newConversation]);

  // 新会话完成首轮流式后，把 URL 同步为 /chat/:id；并标记已就地持有消息，跳过重复拉历史
  useEffect(() => {
    if (storeConvId != null && activeId == null) {
      lastHydrated.current = storeConvId;
      navigate(`/chat/${storeConvId}`, { replace: true });
    }
  }, [storeConvId, activeId, navigate]);

  // 内容变化时自动滚动到底部（打字机跟随）
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  const handleSelect = (id: number): void => {
    navigate(`/chat/${id}`);
    setDrawerOpen(false);
  };
  const handleNewChat = (): void => {
    navigate('/chat');
    setDrawerOpen(false);
  };
  const handleDelete = (id: number): void => {
    deleteConv.mutate(id, { onSuccess: () => activeId === id && handleNewChat() });
  };

  const handleFeedback = useCallback(
    (messageId: number, type: FeedbackType) => feedbackMutate({ messageId, type }),
    [feedbackMutate],
  );

  const handleCreateTicket = useCallback(() => {
    const lastUserQ =
      [...messages].reverse().find((m) => m.role === 'user')?.content ?? '用户请求人工协助';
    createTicketMutate(
      { question: lastUserQ, conversationId: storeConvId ?? undefined },
      { onSuccess: () => navigate('/tickets') },
    );
  }, [messages, storeConvId, createTicketMutate, navigate]);

  const sidebar = (
    <>
      <div style={{ padding: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} block onClick={handleNewChat}>
          新建对话
        </Button>
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px 8px' }}>
        {conversationsQuery.isLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin />
          </div>
        ) : (conversationsQuery.data?.length ?? 0) === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话" style={{ marginTop: 40 }} />
        ) : (
          <List
            dataSource={conversationsQuery.data}
            renderItem={(c) => {
              const active = c.id === activeId;
              return (
                <List.Item
                  onClick={() => handleSelect(c.id)}
                  style={{
                    cursor: 'pointer',
                    borderRadius: 8,
                    paddingInline: 8,
                    marginBottom: 2,
                    background: active ? 'rgba(22,119,255,0.10)' : 'transparent',
                  }}
                  actions={[
                    <Popconfirm
                      key="del"
                      title="删除该会话？"
                      okText="删除"
                      cancelText="取消"
                      okButtonProps={{ danger: true }}
                      onConfirm={(e) => {
                        e?.stopPropagation();
                        handleDelete(c.id);
                      }}
                      onCancel={(e) => e?.stopPropagation()}
                    >
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </Popconfirm>,
                  ]}
                >
                  <List.Item.Meta
                    avatar={<MessageOutlined style={{ color: '#1677ff', marginTop: 4 }} />}
                    title={
                      <Text ellipsis style={{ maxWidth: 130, color: active ? '#1677ff' : undefined }}>
                        {c.title}
                      </Text>
                    }
                    description={
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {formatConversationTime(c.lastActiveAt)}
                      </Text>
                    }
                  />
                </List.Item>
              );
            }}
          />
        )}
      </div>
    </>
  );

  const loadingHistory = activeId != null && messagesQuery.isLoading;

  return (
    <div style={{ display: 'flex', height: '100%' }}>
      {!isMobile ? (
        <div
          style={{
            width: 264,
            flexShrink: 0,
            borderRight: '1px solid rgba(5,5,5,0.06)',
            display: 'flex',
            flexDirection: 'column',
            background: 'rgba(127,127,127,0.02)',
          }}
        >
          {sidebar}
        </div>
      ) : (
        <Drawer
          placement="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          width={288}
          title="会话列表"
          styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column' } }}
        >
          {sidebar}
        </Drawer>
      )}

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        {isMobile && (
          <div style={{ padding: '8px 12px', borderBottom: '1px solid rgba(5,5,5,0.06)' }}>
            <Button icon={<MenuOutlined />} onClick={() => setDrawerOpen(true)}>
              会话
            </Button>
          </div>
        )}

        <div ref={scrollRef} style={{ flex: 1, overflow: 'auto' }}>
          <div style={{ maxWidth: 900, margin: '0 auto', padding: 16 }}>
            {loadingHistory ? (
              <div style={{ textAlign: 'center', padding: 48 }}>
                <Space direction="vertical" align="center">
                  <Spin />
                  <Text type="secondary">加载历史消息…</Text>
                </Space>
              </div>
            ) : messages.length === 0 ? (
              <Welcome onPick={send} />
            ) : (
              messages.map((m) => (
                <MessageBubble
                  key={m.id}
                  message={m}
                  onFeedback={handleFeedback}
                  onCreateTicket={m.ticketHint ? handleCreateTicket : undefined}
                />
              ))
            )}
          </div>
        </div>

        <Composer streaming={streaming} onSend={send} onStop={stop} />
      </div>
    </div>
  );
}
