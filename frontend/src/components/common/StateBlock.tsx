import type { ReactNode } from 'react';
import { Alert, Button, Empty, Spin } from 'antd';

// 统一的「加载 / 错误 / 空」状态块：把三态收敛到一处，保证全站一致的反馈体验。

interface StateBlockProps {
  loading?: boolean;
  error?: Error | null;
  empty?: boolean;
  emptyText?: string;
  onRetry?: () => void;
  children: ReactNode;
}

export default function StateBlock({
  loading,
  error,
  empty,
  emptyText = '暂无数据',
  onRetry,
  children,
}: StateBlockProps) {
  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message="加载失败"
        description={error.message}
        action={
          onRetry ? (
            <Button size="small" danger onClick={onRetry}>
              重试
            </Button>
          ) : undefined
        }
      />
    );
  }
  if (empty) {
    return <Empty description={emptyText} style={{ padding: 48 }} />;
  }
  return <>{children}</>;
}
