import { memo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import 'highlight.js/styles/github-dark.css';

// Markdown 渲染 + GFM（表格 / 删除线 / 任务列表）+ 代码高亮（highlight.js，github-dark 主题）。
// memo 化：流式高频 delta 更新时，只有 content 变化才重渲染，配合消息列表稳定 key 降低开销。
// 外链统一新窗口打开并加 noopener，避免 tab-nabbing。

interface Props {
  content: string;
}

const MarkdownRenderer = memo(function MarkdownRenderer({ content }: Props) {
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeHighlight]}
        components={{
          // node 由 react-markdown 注入，但不应落到 DOM <a>；解构剔除后再展开其余 props，故 node 刻意不使用。
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          a: ({ node, ...props }) => <a {...props} target="_blank" rel="noreferrer noopener" />,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
});

export default MarkdownRenderer;
