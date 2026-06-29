import type { FlightVO } from "./flight"

/** AI 回复类型 */
export type AiReplyType = "TEXT" | "FLIGHT_RECOMMENDATION" | "MISSING_INFO" | "SEARCH_RESULT"

/** 快捷操作 */
export interface QuickAction {
  label: string
  action: string
  payload?: Record<string, string>
}

/** AI 聊天回复 */
export interface AiChatReplyVO {
  sessionId: string
  replyType: AiReplyType
  replyText: string
  parsedCondition?: Record<string, string>
  missingFields?: string[]
  followUpQuestion?: string
  searchUrl?: string
  flights?: FlightVO[]
  quickActions?: QuickAction[]
}

/** AI 对话消息 */
export interface AiSessionMessageVO {
  id: number
  sessionId: string
  role: "USER" | "ASSISTANT"
  content: string
  replyType?: AiReplyType
  flights?: FlightVO[]
  quickActions?: QuickAction[]
  createdAt: string
}

/** AI 会话消息列表（后端包装对象） */
export interface AiSessionMessagesVO {
  sessionId: string
  status: string
  messages: AiSessionMessageVO[]
}

/** 聊天消息（前端本地状态） */
export interface ChatMessage {
  id: string
  role: "user" | "assistant"
  content: string
  replyType?: AiReplyType
  flights?: FlightVO[]
  quickActions?: QuickAction[]
  missingFields?: string[]
  followUpQuestion?: string
  searchUrl?: string
  timestamp: number
}
