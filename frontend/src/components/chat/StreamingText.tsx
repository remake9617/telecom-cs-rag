import { Typography } from 'antd';
import MarkdownRenderer from './MarkdownRenderer';

// 流式文本：渲染 Markdown，并在流式进行中于末尾附打字机光标（.streaming-caret）。
// 内容为空且正在流式时，展示「正在思考…」占位，避免空气泡。

const { Text } = Typography;

interface Props {
  content: string;
  streaming?: boolean;
}

export default function StreamingText({ content, streaming }: Props) {
  if (!content) {
    return streaming ? <Text type="secondary">正在思考…</Text> : null;
  }
  return (
    <div className={streaming ? 'streaming-caret' : undefined}>
      <MarkdownRenderer content={content} />
    </div>
  );
}
