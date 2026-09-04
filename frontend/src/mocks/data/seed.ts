import type {
  ConversationVO,
  DocumentVO,
  HotQuestion,
  KnowledgeBaseVO,
  MessageVO,
  StatsOverview,
  TicketVO,
  TrendPoint,
  UserVO,
} from '@/types';

// Mock 种子数据：结构严格对齐 contract/rest-api.md 的 VO 速查，覆盖电信客服真实业务语料。
// 时间统一用契约格式 yyyy-MM-dd HH:mm:ss（东八区）。

export const seedUsers: UserVO[] = [
  { id: 1, username: 'admin', nickname: '系统管理员', role: 'ADMIN' },
  { id: 2, username: 'agent01', nickname: '客服小王', role: 'AGENT' },
  { id: 3, username: 'zhangsan', nickname: '张三', role: 'USER' },
  { id: 4, username: 'lisi', nickname: '李四', role: 'USER' },
  { id: 5, username: 'wangwu', nickname: '王五', role: 'USER' },
];

export const seedConversations: ConversationVO[] = [
  { id: 1, title: '5G 畅享套餐资费咨询', lastActiveAt: '2026-09-04 10:12:30' },
  { id: 2, title: '宽带报障与光猫重启', lastActiveAt: '2026-09-03 19:45:02' },
];

export const seedMessages: Record<number, MessageVO[]> = {
  1: [
    { id: 101, role: 'user', content: '5G 畅享套餐 129 档包含多少流量和通话？', createdAt: '2026-09-04 10:10:01' },
    {
      id: 102,
      role: 'assistant',
      content:
        '**5G 畅享套餐 129 元档**包含：\n\n- 国内流量：**30GB**（超出后按 3 元/GB 计费）\n- 国内通话：**500 分钟**\n- 免费来电显示、100 条短信\n\n| 档位 | 月租 | 流量 | 通话 |\n|---|---|---|---|\n| 129 | 129 元 | 30GB | 500 分钟 |\n| 159 | 159 元 | 40GB | 800 分钟 |\n\n如需办理可回复「办理」。',
      references: [
        { docTitle: '5G套餐资费', chunkText: '5G畅享套餐129元档：含国内流量30GB，国内通话500分钟，超出流量按3元/GB。', score: 0.91 },
      ],
      createdAt: '2026-09-04 10:10:08',
    },
  ],
  2: [
    { id: 201, role: 'user', content: '家里宽带突然断网了怎么办？', createdAt: '2026-09-03 19:44:10' },
    {
      id: 202,
      role: 'assistant',
      content: '宽带断网可按以下步骤排查：\n\n1. 检查光猫 **PON 灯**是否常亮（闪烁表示线路异常）。\n2. 断电重启光猫与路由器，等待 2 分钟。\n3. 若仍无法上网，可提交工单由后台核查片区线路。',
      references: [
        { docTitle: '宽带报障指引', chunkText: '光猫PON灯常亮为正常，闪烁或熄灭表示光纤线路异常，需报障处理。', score: 0.84 },
      ],
      createdAt: '2026-09-03 19:44:20',
    },
  ],
};

export const seedKbs: KnowledgeBaseVO[] = [
  { id: 1, name: '套餐资费知识库', description: '5G/4G 套餐、资费、优惠政策', embeddingModel: 'bge-m3', status: 'ACTIVE' },
  { id: 2, name: '宽带与装维知识库', description: '宽带报障、装维指引、光猫设备', embeddingModel: 'bge-m3', status: 'ACTIVE' },
];

export const seedDocuments: DocumentVO[] = [
  { id: 1, kbId: 1, title: '5G套餐资费.md', sourceType: 'FILE', fileType: 'md', chunkCount: 42, status: 'DONE', createdAt: '2026-09-01 09:20:11' },
  { id: 2, kbId: 1, title: '优惠政策FAQ.docx', sourceType: 'FILE', fileType: 'docx', chunkCount: 18, status: 'DONE', createdAt: '2026-09-01 11:02:45' },
  { id: 3, kbId: 2, title: '宽带报障指引.pdf', sourceType: 'FILE', fileType: 'pdf', chunkCount: 27, status: 'DONE', createdAt: '2026-09-02 14:31:09' },
  { id: 4, kbId: 2, title: '中国电信宽带装维页', sourceType: 'URL', fileType: 'url', chunkCount: 0, status: 'PROCESSING', createdAt: '2026-09-04 08:55:30' },
  { id: 5, kbId: 1, title: '校园套餐说明.md', sourceType: 'FILE', fileType: 'md', chunkCount: 0, status: 'FAILED', createdAt: '2026-09-03 16:10:02' },
];

export const seedTickets: TicketVO[] = [
  { id: 1, question: '我的宽带频繁掉线，重启光猫也无效', aiReason: '检索置信度低且用户明确要求人工', status: 'REPLIED', reply: '已安排片区装维师傅明日 9:00-11:00 上门检测线路，请保持电话畅通。', handlerId: 1, createdAt: '2026-09-03 20:01:15', repliedAt: '2026-09-03 20:30:40' },
  { id: 2, question: '套餐变更次月生效还是即时生效？', aiReason: '涉及账务规则，转人工确认', status: 'OPEN', createdAt: '2026-09-04 09:12:00' },
  { id: 3, question: '投诉：营业厅办理业务排队过久', aiReason: '用户投诉类，需人工跟进', status: 'CLOSED', reply: '感谢反馈，已优化营业厅叫号流程。', handlerId: 2, createdAt: '2026-09-01 15:22:10', repliedAt: '2026-09-02 10:05:00' },
];

export const seedOverview: StatsOverview = {
  askCount: 1284,
  resolveRate: 0.863,
  ticketRate: 0.137,
  userCount: 5,
  docCount: 5,
  kbCount: 2,
  avgTokenCost: 812,
};

export const seedHot: HotQuestion[] = [
  { question: '5G 畅享套餐包含多少流量', count: 186 },
  { question: '宽带断网如何报障', count: 154 },
  { question: '套餐如何变更', count: 132 },
  { question: '话费账单查询', count: 121 },
  { question: '携号转网流程', count: 98 },
  { question: '国际漫游资费', count: 76 },
  { question: '副卡如何办理', count: 64 },
  { question: '光猫 PON 灯闪烁', count: 51 },
];

export const seedTrend: TrendPoint[] = [
  { date: '2026-08-29', askCount: 152, resolveCount: 130 },
  { date: '2026-08-30', askCount: 178, resolveCount: 156 },
  { date: '2026-08-31', askCount: 143, resolveCount: 120 },
  { date: '2026-09-01', askCount: 190, resolveCount: 168 },
  { date: '2026-09-02', askCount: 205, resolveCount: 180 },
  { date: '2026-09-03', askCount: 187, resolveCount: 161 },
  { date: '2026-09-04', askCount: 229, resolveCount: 201 },
];
