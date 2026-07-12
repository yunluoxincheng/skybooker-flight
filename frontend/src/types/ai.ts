import type { FlightStatus } from "@/types/flight"

/** AI 回复类型 */
export type AiReplyType =
  | "TRAVEL_CHAT"
  | "BOOKING_HELP"
  | "OUT_OF_SCOPE"
  | "FLIGHT_RECOMMENDATION"
  | "FOLLOW_UP"
  | "NO_RESULT"

/** 快捷操作 */
export interface QuickAction {
  label: string
  value: string
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
  status: FlightStatus
  detailUrl?: string
  bookingUrl?: string
}

/** AI 聊天回复 */
export interface AiChatReplyVO {
  sessionId: string
  replyType: AiReplyType
  intent?: string
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
  intent?: string
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
  intent?: string
  flights?: AiFlightCardVO[]
  quickActions?: QuickAction[]
  missingFields?: string[]
  followUpQuestion?: string
  searchUrl?: string
  timestamp: number
}
