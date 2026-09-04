import type { Reference, StreamDoneInfo, StreamErrorInfo } from '@/types';
import { getToken } from '@/utils/token';
import { API_BASE_URL } from '@/config';

// SSE 流式问答：POST /api/qa/chat/stream，Content-Type: text/event-stream。
// 为什么不用原生 EventSource：EventSource 只支持 GET、无法携带请求体，而本接口是 POST + JSON body，
// 因此用 fetch + ReadableStream + TextDecoder 手动解析 event-stream（见 CONVENTIONS 与项目踩坑记录）。
//
// 事件序列（严格对齐 rest-api.md）：
//   event: message   -> data:{delta}                     正文分片，多次
//   event: reference -> data:{references:[{docTitle,chunkText,score}]}
//   event: done      -> data:{messageId,conversationId,tokenCost}
//   event: error     -> data:{code,message}               出错时替代 done
//   event: ticket_hint                                     兜底：提示可转人工

export interface StreamHandlers {
  onDelta: (delta: string) => void;
  onReference: (refs: Reference[]) => void;
  onDone: (info: StreamDoneInfo) => void;
  onError: (err: StreamErrorInfo) => void;
  onTicketHint?: () => void;
}

export interface ChatStreamPayload {
  conversationId?: number;
  question: string;
}

/** 解析单个事件块（可能含多行 data）为 {event, data}；遵循 SSE 规范去掉字段值前导空格 */
function parseEvent(raw: string): { event: string; data: string } {
  let event = 'message'; // SSE 默认事件名
  const dataLines: string[] = [];
  for (const line of raw.split('\n')) {
    if (!line || line.startsWith(':')) continue; // 空行或以 : 开头的注释行跳过
    const idx = line.indexOf(':');
    const field = idx === -1 ? line : line.slice(0, idx);
    let value = idx === -1 ? '' : line.slice(idx + 1);
    if (value.startsWith(' ')) value = value.slice(1); // 规范：冒号后单个前导空格需去除
    if (field === 'event') event = value;
    else if (field === 'data') dataLines.push(value);
  }
  return { event, data: dataLines.join('\n') }; // 多行 data 以 \n 拼接
}

/** 按事件类型分发；data 为 JSON 字符串 */
function dispatch(event: string, dataStr: string, h: StreamHandlers): void {
  if (!dataStr) {
    // ticket_hint 等可能无 data
    if (event === 'ticket_hint') h.onTicketHint?.();
    return;
  }
  let data: any;
  try {
    data = JSON.parse(dataStr);
  } catch {
    // data 非 JSON（极少见）：message 事件当纯文本兜底
    if (event === 'message') h.onDelta(dataStr);
    return;
  }
  switch (event) {
    case 'message':
      h.onDelta(data.delta ?? '');
      break;
    case 'reference':
      h.onReference((data.references ?? []) as Reference[]);
      break;
    case 'done':
      h.onDone(data as StreamDoneInfo);
      break;
    case 'error':
      h.onError(data as StreamErrorInfo);
      break;
    case 'ticket_hint':
      h.onTicketHint?.();
      break;
    default:
      break; // 忽略未知事件，向前兼容后端扩展
  }
}

/**
 * 发起流式问答。delta/reference/done/error/ticket_hint 通过 handlers 回调。
 * @param signal 传入 AbortSignal 可中途「停止生成」；被中断时 read() 抛 AbortError，由调用方决定语义。
 */
export async function chatStream(
  payload: ChatStreamPayload,
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken();

  const res = await fetch(`${API_BASE_URL}/api/qa/chat/stream`, {
    method: 'POST',
    signal,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    // 尝试从后端 R<T> 错误体取 message，失败则用 HTTP 状态兜底
    let msg = `请求失败（HTTP ${res.status}）`;
    try {
      const body = await res.json();
      if (body?.message) msg = body.message;
    } catch {
      /* 响应体非 JSON，忽略 */
    }
    handlers.onError({ code: res.status, message: msg });
    return;
  }
  if (!res.body) {
    handlers.onError({ code: -1, message: '当前浏览器不支持流式响应（ReadableStream）' });
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    // 归一化换行：SSE 行分隔可能是 \r\n / \r / \n，统一去掉 \r 后按 \n\n 切事件。
    // \r 是单字节 ASCII，不会跨 chunk 截断；JSON 字符串内的回车会被转义为 \\r，不受影响。
    buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '');
    // 只处理「完整事件」（以空行 \n\n 结束），跨包半行留在 buffer 等待后续 chunk
    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const { event, data } = parseEvent(rawEvent);
      dispatch(event, data, handlers);
    }
  }

  // flush 解码器与残余 buffer（最后一个事件可能未以 \n\n 收尾）
  buffer += decoder.decode();
  if (buffer.trim()) {
    const { event, data } = parseEvent(buffer);
    dispatch(event, data, handlers);
  }
}
