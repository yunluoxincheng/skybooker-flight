import { get, post, del } from "@/lib/request"
import type { AiChatReplyVO, AiSessionMessagesVO } from "@/types/ai"

/** 发送 AI 聊天消息 */
export function chat(data: { sessionId?: string; message: string }) {
  return post<AiChatReplyVO>("/ai/chat", data, { auth: "user" })
}

/** 获取会话历史消息 */
export function getMessages(sessionId: string) {
  return get<AiSessionMessagesVO>(`/ai/sessions/${sessionId}/messages`, undefined, { auth: "user" })
}

/** 删除会话 */
export function deleteSession(sessionId: string) {
  return del<null>(`/ai/sessions/${sessionId}`, { auth: "user" })
}
