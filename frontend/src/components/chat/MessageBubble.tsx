import { memo, useState } from 'react';
import { Alert, Avatar, Button, Space, Tag, Tooltip, Typography } from 'antd';
import {
  CopyOutlined,
  CustomerServiceOutlined,
  DislikeFilled,
  DislikeOutlined,
  LikeFilled,
  LikeOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ChatMessage } from '@/stores/chatStore';
import type { FeedbackType } from '@/api';
import { notify } from '@/utils/notify';
import StreamingText from './StreamingText';
import ReferenceList from './ReferenceList';

const { Text } = Typography;

// 单条消息气泡：用户（右侧纯色）/ 助手（左侧 + Markdown + 引用 + 操作行）。
// memo 化：流式期间仅内容变化的那条助手气泡重渲染，历史消息保持稳定不抖。

interface Props {
  message: ChatMessage;
  onFeedback?: (messageId: number, type: FeedbackType) => void;
  onCreateTicket?: () => void;
}

function MessageBubble({ message, onFeedback, onCreateTicket }: Props) {
  const [feedback, setFeedback] = useState<FeedbackType | null>(null);
  const isUser = message.role === 'user';
  const isStreaming = !!message.streaming;
  // 仅「已落库的助手消息」（numeric id + done）可反馈；流式中/临时消息禁用
  const canFeedback = !isUser && typeof message.id === 'number' && message.status === 'done';

  const handleCopy = async (): Promise<void> => {
    try {
      await navigator.clipboard.writeText(message.content);
      notify.success('已复制到剪贴板');
    } catch {
      notify.error('复制失败，请手动选择文本');
    }
  };

  const handleFeedback = (type: FeedbackType): void => {
    setFeedback(type);
    if (typeof message.id === 'number') onFeedback?.(message.id, type);
  };

  if (isUser) {
    return (
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 16 }}>
        <div
          style={{
            maxWidth: '72%',
            padding: '10px 14px',
            borderRadius: 12,
            background: '#1677ff',
            color: '#fff',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          {message.content}
        </div>
        <Avatar icon={<UserOutlined />} style={{ background: '#87d068', flexShrink: 0 }} />
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
      <Avatar icon={<RobotOutlined />} style={{ background: '#1677ff', flexShrink: 0 }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            padding: '10px 14px',
            borderRadius: 12,
            background: 'rgba(127,127,127,0.08)',
            wordBreak: 'break-word',
          }}
        >
          {message.status === 'error' ? (
            <Alert type="error" showIcon message="回答失败" description={message.errorMsg} />
          ) : (
            <StreamingText content={message.content} streaming={isStreaming} />
          )}

          <ReferenceList references={message.references} />

          {message.ticketHint && onCreateTicket && (
            <div style={{ marginTop: 10 }}>
              <Button size="small" icon={<CustomerServiceOutlined />} onClick={onCreateTicket}>
                转人工工单
              </Button>
            </div>
          )}
        </div>

        {/* 操作行：流式结束后展示 反馈 / 复制 / token 消耗 */}
        {!isStreaming && message.status !== 'error' && (
          <Space size={4} style={{ marginTop: 6 }}>
            <Tooltip title="有帮助">
              <Button
                type="text"
                size="small"
                disabled={!canFeedback}
                icon={
                  feedback === 'LIKE' ? (
                    <LikeFilled style={{ color: '#1677ff' }} />
                  ) : (
                    <LikeOutlined />
                  )
                }
                onClick={() => handleFeedback('LIKE')}
              />
            </Tooltip>
            <Tooltip title="没帮助">
              <Button
                type="text"
                size="small"
                disabled={!canFeedback}
                icon={
                  feedback === 'DISLIKE' ? (
                    <DislikeFilled style={{ color: '#ff4d4f' }} />
                  ) : (
                    <DislikeOutlined />
                  )
                }
                onClick={() => handleFeedback('DISLIKE')}
              />
            </Tooltip>
            <Tooltip title="复制回答">
              <Button type="text" size="small" icon={<CopyOutlined />} onClick={handleCopy} />
            </Tooltip>
            {typeof message.tokenCost === 'number' && (
              <Tag bordered={false} style={{ marginInlineStart: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {message.tokenCost} tokens
                </Text>
              </Tag>
            )}
          </Space>
        )}
      </div>
    </div>
  );
}

export default memo(MessageBubble);
