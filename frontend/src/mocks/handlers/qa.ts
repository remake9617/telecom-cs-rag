import { http, HttpResponse } from 'msw';
import { nowStr, ok, sleep } from './_helpers';
import { db, nextId } from '../db';
import type { MessageVO, Reference } from '@/types';

// 问答 Mock：会话列表 / 历史 / 删除 / SSE 流式问答。
// SSE 帧格式严格对齐 rest-api.md：`event: <name>\ndata: <json>\n\n`。

interface Answer {
  text: string;
  refs: Reference[];
  hint: boolean;
}

/** 依据问题关键词返回不同答案，覆盖 Markdown 表格/列表/代码块/引用与转人工兜底等渲染场景 */
function buildAnswer(question: string): Answer {
  if (/人工|投诉|转接|客服/.test(question)) {
    return {
      text: '抱歉，这个问题我暂时无法给出准确答案。你可以**点击下方「转人工工单」**，客服会尽快为你处理。',
      refs: [],
      hint: true,
    };
  }
  if (/5G|套餐|资费|流量|月租/.test(question)) {
    return {
      text: [
        '**5G 畅享套餐**常见档位如下：',
        '',
        '| 档位 | 月租 | 流量 | 通话 |',
        '|---|---|---|---|',
        '| 129 | 129 元 | 30GB | 500 分钟 |',
        '| 159 | 159 元 | 40GB | 800 分钟 |',
        '| 199 | 199 元 | 60GB | 1000 分钟 |',
        '',
        '> 超出流量按 3 元/GB 计费，当月有效。',
        '',
        '如需办理，可携带身份证到营业厅，或直接回复「办理」。',
      ].join('\n'),
      refs: [
        { docTitle: '5G套餐资费', chunkText: '5G畅享套餐129元档：含国内流量30GB，国内通话500分钟，超出流量按3元/GB。', score: 0.91 },
        { docTitle: '优惠政策FAQ', chunkText: '新用户办理5G套餐首月立减20元，合约期12个月。', score: 0.78 },
      ],
      hint: false,
    };
  }
  if (/宽带|断网|光猫|掉线|报障/.test(question)) {
    return {
      text: [
        '宽带故障排查步骤：',
        '',
        '1. 检查光猫 **PON 灯**：常亮为正常，闪烁/熄灭表示线路异常。',
        '2. 断电重启光猫与路由器，等待约 2 分钟。',
        '3. 仍无法上网则提交工单，由后台核查片区线路。',
        '',
        '```bash',
        '# 可选：在电脑上测试网关连通性',
        'ping 192.168.1.1',
        '```',
      ].join('\n'),
      refs: [
        { docTitle: '宽带报障指引', chunkText: '光猫PON灯常亮为正常，闪烁或熄灭表示光纤线路异常，需报障处理。', score: 0.86 },
      ],
      hint: false,
    };
  }
  return {
    text: [
      '你好，我是电信智能客服，可以帮你解答 **套餐资费**、**宽带报障**、**账单规则** 等问题。',
      '',
      '不妨试试问我：',
      '- 「5G 畅享套餐 129 档包含多少流量？」',
      '- 「宽带断网了怎么办？」',
    ].join('\n'),
    refs: [],
    hint: false,
  };
}

export const qaHandlers = [
  http.get('*/api/qa/conversations', () => ok(db.conversations)),

  http.get('*/api/qa/conversations/:id/messages', ({ params }) => {
    const id = Number(params.id);
    return ok(db.messages[id] ?? []);
  }),

  http.delete('*/api/qa/conversations/:id', ({ params }) => {
    const id = Number(params.id);
    db.conversations = db.conversations.filter((c) => c.id !== id);
    delete db.messages[id];
    return ok(null);
  }),

  // 流式问答：POST + text/event-stream，用 ReadableStream 逐帧推送
  http.post('*/api/qa/chat/stream', async ({ request }) => {
    const { conversationId, question } = (await request.json()) as {
      conversationId?: number;
      question: string;
    };
    const { text, refs, hint } = buildAnswer(question ?? '');

    // 会话：无 id 则新建并置顶；有 id 则刷新活跃时间
    let cid = conversationId;
    if (cid == null) {
      cid = nextId();
      db.conversations = [
        { id: cid, title: (question ?? '新会话').slice(0, 20), lastActiveAt: nowStr() },
        ...db.conversations,
      ];
      db.messages[cid] = [];
    } else {
      db.conversations = db.conversations.map((c) =>
        c.id === cid ? { ...c, lastActiveAt: nowStr() } : c,
      );
    }
    const finalCid = cid;

    // 落库用户消息
    const userMsgId = nextId();
    db.messages[finalCid] = [
      ...(db.messages[finalCid] ?? []),
      { id: userMsgId, role: 'user', content: question ?? '', createdAt: nowStr() },
    ];

    const encoder = new TextEncoder();
    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        const send = (event: string, data?: unknown): void => {
          const dataLine = data === undefined ? '' : `data: ${JSON.stringify(data)}\n`;
          controller.enqueue(encoder.encode(`event: ${event}\n${dataLine}\n`));
        };

        // 正文分片：每 2 字符一帧，模拟逐字打字机
        for (let i = 0; i < text.length; i += 2) {
          send('message', { delta: text.slice(i, i + 2) });
          await sleep(18);
        }
        if (refs.length) send('reference', { references: refs });
        if (hint) send('ticket_hint');

        // 落库助手消息，并回传 done（messageId/conversationId/tokenCost）
        const assistantMsgId = nextId();
        const assistantMsg: MessageVO = {
          id: assistantMsgId,
          role: 'assistant',
          content: text,
          references: refs.length ? refs : undefined,
          createdAt: nowStr(),
        };
        db.messages[finalCid] = [...(db.messages[finalCid] ?? []), assistantMsg];

        send('done', {
          messageId: assistantMsgId,
          conversationId: finalCid,
          tokenCost: 600 + text.length,
        });
        controller.close();
      },
    });

    return new HttpResponse(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
      },
    });
  }),
];
