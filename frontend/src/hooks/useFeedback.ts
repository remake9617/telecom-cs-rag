import { useMutation } from '@tanstack/react-query';
import { feedbackApi, type FeedbackParams } from '@/api';
import { notify } from '@/utils/notify';

// 点赞 / 点踩（MVP 仅落库）。成功给轻提示即可，无需失效列表。
export function useFeedback() {
  return useMutation({
    mutationFn: (params: FeedbackParams) => feedbackApi.submit(params),
    onSuccess: () => notify.success('感谢你的反馈'),
  });
}
