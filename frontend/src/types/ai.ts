/** AI 回复类型 */
export type AiReplyType = "TEXT" | "FLIGHT_RECOMMENDATION" | "MISSING_INFO" | "SEARCH_RESULT"

/** 快捷操作 */
export interface QuickAction {
  label: string
  action: string
  payload?: Record<string, string>
}

/** AI 推荐航班卡片 — 匹配后端 AIChatReplyVO.flights 的轻量 Map 结构 */
export interface AiFlightCardVO {
  flightId: number
  flightNo: string
  airlineName: string
  departureCity: string
  arrivalCity: string
  departureTime: string
  arrivalTime: string
  durationMinutes: number
  price: number
  remainingSeats: number
  status: string
  detailUrl?: string
  bookingUrl?: string
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
  flights?: AiFlightCardVO[]
  quickActions?: QuickAction[]
}

/** AI 历史消息的 extra 字段（后端扁平存储） */
export interface AiSessionMessageExtra {
  replyType?: AiReplyType
  parsedCondition?: Record<string, string>
  missingFields?: string[]
  followUpQuestion?: string
  searchUrl?: string
  flights?: AiFlightCardVO[]
  quickActions?: QuickAction[]
}

/** AI 对话消息（匹配后端 /api/ai/sessions/:id/messages 返回） */
export interface AiSessionMessageVO {
  role: "USER" | "ASSISTANT"
  content: string
  messageType: "TEXT" | "AI_REPLY"
  extra?: AiSessionMessageExtra
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
  flights?: AiFlightCardVO[]
  quickActions?: QuickAction[]
  missingFields?: string[]
  followUpQuestion?: string
  searchUrl?: string
  timestamp: number
}
