import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result } from 'antd';

// 全局错误边界：兜底渲染期异常，避免白屏；提供刷新恢复入口。

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // 生产可接入前端监控；这里先打印，保证可诊断
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <Result
          status="error"
          title="页面渲染出错"
          subTitle={this.state.error?.message}
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              刷新页面
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}
