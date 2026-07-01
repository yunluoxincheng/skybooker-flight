"use client"

import { useEffect, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { Bot, Send, Sparkles, Trash2, Loader2, ArrowRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import { UserLayout } from "@/components/layout/UserLayout"
import { AiFlightCard } from "@/components/common/AiFlightCard"
import { useAiChat } from "@/features/ai/hooks/useAiChat"
import { useAuth } from "@/contexts/AuthContext"

const QUICK_PROMPTS = [
  "帮我找明天上海飞北京的航班",
  "下周五成都飞深圳，2000以内",
  "推荐最便宜的直飞三亚航班",
  "下周一到杭州，上午出发",
]

export default function AiAssistantPage() {
  const router = useRouter()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const { messages, isLoading, error, sendMessage, clearSession } = useAiChat()
  const [input, setInput] = useState("")
  const scrollRef = useRef<HTMLDivElement>(null)

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages])

  const handleSend = () => {
    if (!input.trim()) return
    sendMessage(input.trim())
    setInput("")
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  if (isAuthLoading) {
    return (
      <UserLayout>
        <div className="flex items-center justify-center h-[calc(100vh-8rem)]">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      </UserLayout>
    )
  }

  const showWelcome = messages.length === 0

  return (
    <UserLayout>
      <div className="flex flex-col mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 h-[calc(100vh-4rem)]">
        {/* 头部 */}
        <div className="flex items-center justify-between py-4 border-b border-slate-200 shrink-0">
          <div className="flex items-center gap-2">
            <div className="flex items-center justify-center h-8 w-8 rounded-lg bg-primary/10">
              <Bot className="h-4 w-4 text-primary" />
            </div>
            <div>
              <h1 className="font-bold text-lg">AI 智能购票助手</h1>
              <p className="text-xs text-muted-foreground">用自然语言描述需求，智能推荐最优航班</p>
            </div>
          </div>
          {messages.length > 0 && (
            <Button variant="ghost" size="sm" className="gap-1" onClick={clearSession}>
              <Trash2 className="h-3.5 w-3.5" /> 清除对话
            </Button>
          )}
        </div>

        {/* 消息区域 */}
        <ScrollArea className="flex-1 py-4" ref={scrollRef}>
          {showWelcome ? (
            /* 欢迎界面 */
            <div className="flex flex-col items-center justify-center min-h-[50vh]">
              <div className="flex items-center justify-center h-16 w-16 rounded-2xl bg-primary/10 mb-4">
                <Sparkles className="h-8 w-8 text-primary" />
              </div>
              <h2 className="text-xl font-bold mb-2">你好，我是你的 AI 购票助手 ✈️</h2>
              <p className="text-muted-foreground mb-6 text-center max-w-sm">
                试着用自然语言告诉我你的出行需求吧
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full max-w-md">
                {QUICK_PROMPTS.map((prompt) => (
                  <Button
                    key={prompt}
                    variant="outline"
                    size="sm"
                    className="justify-start h-auto py-2 px-3 text-xs text-left"
                    onClick={() => sendMessage(prompt)}
                  >
                    {prompt}
                  </Button>
                ))}
              </div>
            </div>
          ) : (
            /* 消息列表 */
            <div className="space-y-4">
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
                >
                  <div className={`max-w-[85%] ${msg.role === "user" ? "order-1" : ""}`}>
                    {/* 用户消息 */}
                    {msg.role === "user" && (
                      <div className="rounded-2xl rounded-br-md bg-primary text-primary-foreground px-4 py-2.5 text-sm">
                        {msg.content}
                      </div>
                    )}

                    {/* AI 消息 */}
                    {msg.role === "assistant" && (
                      <div className="space-y-3">
                        {/* 文字回复 */}
                        <div className="rounded-2xl rounded-bl-md border border-slate-200 bg-white px-4 py-2.5 text-sm shadow-sm">
                          <p className="whitespace-pre-wrap">{msg.content}</p>

                          {/* 追问 */}
                          {msg.followUpQuestion && (
                            <p className="mt-2 text-primary font-medium text-sm">{msg.followUpQuestion}</p>
                          )}

                          {/* 缺失信息 */}
                          {msg.missingFields && msg.missingFields.length > 0 && (
                            <div className="mt-2 flex flex-wrap gap-1">
                              {msg.missingFields.map((f) => (
                                <span key={f} className="text-xs bg-amber-50 text-amber-600 px-2 py-0.5 rounded-full border border-amber-200">
                                  请补充：{f}
                                </span>
                              ))}
                            </div>
                          )}

                          {/* 快捷操作 */}
                          {msg.quickActions && msg.quickActions.length > 0 && (
                            <div className="mt-2 flex flex-wrap gap-1.5">
                              {msg.quickActions.map((qa, i) => (
                                <Button
                                  key={i}
                                  variant="outline"
                                  size="sm"
                                  className="h-7 text-xs"
                                  onClick={() => {
                                    if (qa.action === "search_flights" && qa.payload?.params) {
                                      router.push(`/flights?${qa.payload.params}`)
                                    } else {
                                      sendMessage(qa.label)
                                    }
                                  }}
                                >
                                  {qa.label} <ArrowRight className="h-3 w-3 ml-1" />
                                </Button>
                              ))}
                            </div>
                          )}

                          {/* 搜索链接 */}
                          {msg.searchUrl && (
                            <Button
                              variant="link"
                              size="sm"
                              className="mt-1 h-auto p-0 text-xs"
                              onClick={() => router.push(msg.searchUrl!)}
                            >
                              查看全部搜索结果 →
                            </Button>
                          )}
                        </div>

                        {/* 航班推荐卡片 */}
                        {msg.flights && msg.flights.length > 0 && (
                          <div className="space-y-2 pl-4 border-l-2 border-primary/30">
                            {msg.flights.map((f) => (
                              <AiFlightCard
                                key={f.flightId}
                                flight={f}
                                className="shadow-none border-slate-200"
                              />
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {/* Loading */}
              {isLoading && (
                <div className="flex justify-start">
                  <div className="rounded-2xl rounded-bl-md border border-slate-200 bg-white px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-2">
                      <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                      <span className="text-sm text-muted-foreground">AI 正在分析...</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Error */}
              {error && (
                <div className="flex justify-center">
                  <div className="rounded-lg bg-destructive/10 px-4 py-2 text-xs text-destructive">{error}</div>
                </div>
              )}
            </div>
          )}
        </ScrollArea>

        {/* 输入区域 */}
        <div className="py-3 border-t border-slate-200 shrink-0">
          {!isAuthenticated ? (
            <div className="text-center py-3">
              <p className="text-sm text-muted-foreground mb-2">请先登录以使用 AI 助手</p>
              <Button render={<a href="/login?redirect=/ai-assistant">去登录</a>} size="sm" nativeButton={false} />
            </div>
          ) : (
            <div className="flex items-end gap-2">
              <Textarea
                placeholder="描述你的出行需求，如：明天上海去北京，下午出发..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={2}
                className="min-h-0 resize-none"
                disabled={isLoading}
              />
              <Button
                size="icon"
                onClick={handleSend}
                disabled={!input.trim() || isLoading}
                className="shrink-0"
              >
                {isLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Send className="h-4 w-4" />
                )}
              </Button>
            </div>
          )}
        </div>
      </div>
    </UserLayout>
  )
}
