import { authHandlers } from './auth';
import { qaHandlers } from './qa';
import { kbHandlers } from './kb';
import { ticketHandlers } from './ticket';
import { feedbackHandlers } from './feedback';
import { statsHandlers } from './stats';
import { systemHandlers } from './system';

// 汇总所有域的 handlers，交给 browser worker 注册。
export const handlers = [
  ...authHandlers,
  ...qaHandlers,
  ...kbHandlers,
  ...ticketHandlers,
  ...feedbackHandlers,
  ...statsHandlers,
  ...systemHandlers,
];
