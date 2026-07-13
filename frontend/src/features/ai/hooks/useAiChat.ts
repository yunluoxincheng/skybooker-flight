"use client"

import { useState, useCallback, useEffect } from "react"
import * as aiApi from "@/services/aiApi"
import type { ChatMessage, AiChatReplyVO, QuickAction } from "@/types/ai"
import type { ApiError } from "@/lib/request"
import { normalizeAiJourneys } from "@/features/ai/normalizeAiJourneys"

const SESSION_KEY = "skybooker_ai_session"

function formatQuickActions(
  quickActions?: Array<Record<string, string> | QuickAction>
): QuickAction[] | undefined {
  if (!quickActions?.length) {
    return undefined
  }

  return quickActions.map((qa) => ({
    label: qa.label,
    value: qa.value || qa.label,
  }))
}

export function useAiChat() {
  const [sessionId, setSessionId] = useState<string | null>(() => {
    if (typeof window === "undefined") return null
    return localStorage.getItem(SESSION_KEY)
  })
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const persistSession = useCallback((sid: string) => {
    setSessionId(sid)
    localStorage.setItem(SESSION_KEY, sid)
  }, [])

  // 添加消息
  const addMessage = useCallback((msg: Omit<ChatMessage, "id" | "timestamp">) => {
    const newMsg: ChatMessage = {
      ...msg,
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      timestamp: Date.now(),
    }
    setMessages((prev) => [...prev, newMsg])
    return newMsg
  }, [])

  // 发送消息
  const sendMessage = useCallback(
    async (text: string) => {
      if (!text.trim() || isLoading) return

      // 添加用户消息
      addMessage({ role: "user", content: text })
      setIsLoading(true)
      setError(null)

      try {
        const reply: AiChatReplyVO = await aiApi.chat({
          sessionId: sessionId || undefined,
          message: text,
        })

        // 持久化 sessionId
        if (reply.sessionId && reply.sessionId !== sessionId) {
          persistSession(reply.sessionId)
        }

        // 添加 AI 回复
        addMessage({
          role: "assistant",
          content: reply.replyText,
          replyType: reply.replyType,
          intent: reply.intent,
          flights: normalizeAiJourneys(reply.flights),
          quickActions: formatQuickActions(reply.quickActions),
          missingFields: reply.missingFields,
          followUpQuestion: reply.followUpQuestion,
          searchUrl: reply.searchUrl,
          matchLevel: reply.matchLevel,
          relaxedFields: reply.relaxedFields,
          fallbackReason: reply.fallbackReason,
        })
      } catch (err) {
        setError((err as ApiError).message || "发送失败")
      } finally {
        setIsLoading(false)
      }
    },
    [sessionId, isLoading, addMessage, persistSession]
  )

  // 加载历史
  const loadHistory = useCallback(async () => {
    if (!sessionId) return
    setIsLoading(true)
    try {
      const data = await aiApi.getMessages(sessionId)
      const chatMsgs: ChatMessage[] = data.messages.map((m, i) => ({
        id: `${m.createdAt}-${m.role}-${i}`,
        role: m.role === "USER" ? "user" : "assistant",
        content: m.content,
        replyType: m.extra?.replyType,
        intent: m.extra?.intent,
        flights: normalizeAiJourneys(m.extra?.flights),
        quickActions: formatQuickActions(m.extra?.quickActions),
        missingFields: m.extra?.missingFields,
        followUpQuestion: m.extra?.followUpQuestion,
        searchUrl: m.extra?.searchUrl,
        matchLevel: m.extra?.matchLevel,
        relaxedFields: m.extra?.relaxedFields,
        fallbackReason: m.extra?.fallbackReason,
        timestamp: new Date(m.createdAt).getTime(),
      }))
      setMessages(chatMsgs)
    } catch {
      // 历史加载失败不阻断
    } finally {
      setIsLoading(false)
    }
  }, [sessionId])

  // 清除会话
  const clearSession = useCallback(async () => {
    if (sessionId) {
      try {
        await aiApi.deleteSession(sessionId)
      } catch {
        // ignore
      }
    }
    localStorage.removeItem(SESSION_KEY)
    setSessionId(null)
    setMessages([])
  }, [sessionId])

  // 恢复后加载历史
  useEffect(() => {
    if (sessionId && messages.length === 0) {
      loadHistory()
    }
  }, [loadHistory, messages.length, sessionId])

  return {
    sessionId,
    messages,
    isLoading,
    error,
    sendMessage,
    clearSession,
  }
}
