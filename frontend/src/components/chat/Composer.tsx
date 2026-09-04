import { useState, type KeyboardEvent } from 'react';
import { Button, Input } from 'antd';
import { ArrowUpOutlined, StopOutlined } from '@ant-design/icons';

const { TextArea } = Input;

// 对话输入器：多行输入（Enter 发送 / Shift+Enter 换行），流式中切换为「停止生成」。

interface Props {
  streaming: boolean;
  disabled?: boolean;
  onSend: (text: string) => void;
  onStop: () => void;
}

export default function Composer({ streaming, disabled, onSend, onStop }: Props) {
  const [value, setValue] = useState('');

  const submit = (): void => {
    const text = value.trim();
    if (!text || streaming || disabled) return;
    onSend(text);
    setValue('');
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>): void => {
    // Enter 发送、Shift+Enter 换行；避免中文输入法组词回车误发（e.nativeEvent.isComposing）
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div style={{ padding: '12px 16px', borderTop: '1px solid rgba(5,5,5,0.06)' }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', maxWidth: 900, margin: '0 auto' }}>
        <TextArea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="输入你的问题，Enter 发送 / Shift+Enter 换行"
          autoSize={{ minRows: 1, maxRows: 6 }}
          disabled={disabled}
          style={{ flex: 1, resize: 'none', borderRadius: 8 }}
        />
        {streaming ? (
          <Button danger icon={<StopOutlined />} onClick={onStop} style={{ borderRadius: 8 }}>
            停止
          </Button>
        ) : (
          <Button
            type="primary"
            icon={<ArrowUpOutlined />}
            onClick={submit}
            disabled={disabled || !value.trim()}
            style={{ borderRadius: 8 }}
          >
            发送
          </Button>
        )}
      </div>
    </div>
  );
}
