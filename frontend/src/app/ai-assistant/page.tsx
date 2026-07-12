"use client"

import { useEffect, useRef, useState, type KeyboardEvent } from "react"
import { useRouter } from "next/navigation"
import {
  ArrowRight,
  Bot,
  HelpCircle,
  Loader2,
  MapPin,
  Plane,
  Search,
  Send,
  Sparkles,
  Trash2,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Textarea } from "@/components/ui/textarea"
import { UserLayout } from "@/components/layout/UserLayout"
import { JourneyCard } from "@/components/journey/JourneyCard"
import { useAiChat } from "@/features/ai/hooks/useAiChat"
import { useAuth } from "@/contexts/AuthContext"

const QUICK_PROMPTS = [
  "帮我找明天上海飞北京的航班",
  "周末想看海，预算别太高",
  "推荐最便宜的直飞三亚航班",
  "怎么退票或改签？",
]

const FIELD_LABELS: Record<string, string> = {
  departureCity: "出发城市",
  arrivalCity: "目的地",
  destinationCity: "目的地",
  departureDate: "出发日期",
  departureDateStart: "出发日期范围",
  departureDateEnd: "出发日期范围",
  passengerCount: "乘机人数",
  cabinClass: "舱位",
  airlineRaw: "航司偏好",
  airlineId: "航司偏好",
  minPrice: "最低预算",
  maxPrice: "最高预算",
  departureTimeStart: "出发时间段",
  departureTimeEnd: "出发时间段",
  directOnly: "是否直飞",
  maxDurationMinutes: "最长飞行时长",
  sort: "排序偏好",
  travelDays: "行程天数",
  budget: "预算",
  preference: "旅行偏好",
}

const FIELD_HINTS: Record<string, string> = {
  departureCity: "你从哪里出发？",
  arrivalCity: "想飞到哪里？",
  destinationCity: "想去哪个城市？",
  departureDate: "哪一天出发？",
  departureDateStart: "可接受的出发日期？",
  departureDateEnd: "可接受的出发日期？",
  passengerCount: "几位乘机人？",
  cabinClass: "经济舱、商务舱或头等舱？",
  airlineRaw: "有偏好的航司吗？",
  airlineId: "有偏好的航司吗？",
  minPrice: "最低预算是多少？",
  maxPrice: "最高预算是多少？",
  departureTimeStart: "希望几点后出发？",
  departureTimeEnd: "希望几点前出发？",
  directOnly: "是否只看直飞？",
  maxDurationMinutes: "最长能接受多久？",
  sort: "按价格、时间还是余票？",
  travelDays: "计划玩几天？",
  budget: "大概预算是多少？",
  preference: "偏好海边、美食还是轻松游？",
}

const REPLY_LABELS: Record<string, string> = {
  TRAVEL_CHAT: "旅行建议",
  BOOKING_HELP: "平台帮助",
  OUT_OF_SCOPE: "旅行范围外",
  FLIGHT_RECOMMENDATION: "航班推荐",
  FOLLOW_UP: "补全信息",
  NO_RESULT: "暂无结果",
}

const MISSING_FIELD_PREFIX = /^请补充[:：]\s*(.+)$/
const TECHNICAL_FIELD_TOKEN = /^[A-Za-z][A-Za-z0-9_]*$/

function getFieldLabel(field: string) {
  return FIELD_LABELS[field] || "行程信息"
}

function getFieldHint(field: string) {
  return FIELD_HINTS[field] || "补充后我再继续处理。"
}

function uniqueFields(fields?: string[]) {
  return Array.from(new Set(fields || []))
}

function parseTechnicalMissingFields(line: string) {
  const match = line.trim().match(MISSING_FIELD_PREFIX)
  if (!match) {
    return []
  }

  const fields = match[1].split(/[,，、\s]+/).filter(Boolean)
  if (!fields.length || fields.some((field) => !TECHNICAL_FIELD_TOKEN.test(field))) {
    return []
  }

  return fields
}

function formatAssistantContent(content: string, missingFields?: string[]) {
  const technicalFields: string[] = []
  const visibleLines = content.split(/\r?\n/).filter((line) => {
    const fields = parseTechnicalMissingFields(line)
    if (fields.length) {
      technicalFields.push(...fields)
      return false
    }
    return true
  })
  const cleaned = visibleLines.join("\n").replace(/\n{3,}/g, "\n\n").trim()

  if (cleaned) {
    return cleaned
  }
  if (missingFields?.length) {
    return "我还需要一点行程信息，才能继续帮你查。"
  }
  if (technicalFields.length) {
    const labels = Array.from(new Set(technicalFields.map(getFieldLabel)))
    return `还需要补充：${labels.join("、")}`
  }
  return content
}

const isNonSearch = (replyType?: string) =>
  replyType === "TRAVEL_CHAT" || replyType === "BOOKING_HELP" || replyType === "OUT_OF_SCOPE"

const isFollowUp = (replyType?: string) => replyType === "FOLLOW_UP"

const isRecommendation = (replyType?: string) => replyType === "FLIGHT_RECOMMENDATION"

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

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
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
      <div className="mx-auto flex h-[calc(100vh-4rem)] max-w-5xl flex-col px-3 sm:px-6 lg:px-8">
        <div className="flex shrink-0 items-center justify-between border-b border-slate-200/80 py-4">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-cyan-100 bg-cyan-50">
              <Bot className="h-4 w-4 text-cyan-700" />
            </div>
            <div className="min-w-0">
              <h1 className="truncate text-lg font-semibold tracking-tight text-slate-950">AI 旅行助手</h1>
              <p className="truncate text-xs text-slate-500">航班、目的地、退改签，一起聊清楚</p>
            </div>
          </div>
          {messages.length > 0 && (
            <Button variant="ghost" size="sm" className="gap-1.5 text-slate-500 hover:text-slate-900" onClick={clearSession}>
              <Trash2 className="h-3.5 w-3.5" />
              清除对话
            </Button>
          )}
        </div>

        <ScrollArea className="min-h-0 flex-1 overflow-hidden py-5" ref={scrollRef}>
          {showWelcome ? (
            <div className="grid min-h-[54vh] place-items-center py-10 pl-1 pr-4">
              <div className="w-full max-w-2xl text-center">
                <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl border border-cyan-100 bg-white shadow-sm">
                  <Sparkles className="h-7 w-7 text-cyan-700" />
                </div>
                <h2 className="text-2xl font-semibold tracking-tight text-slate-950">今天想去哪儿？</h2>
                <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
                  把时间、城市和偏好告诉我，我们直接规划下一程。
                </p>
                <div className="mt-7 grid w-full grid-cols-1 gap-2 sm:grid-cols-2">
                  {QUICK_PROMPTS.map((prompt, index) => {
                    const Icon = [Plane, MapPin, Search, HelpCircle][index]
                    return (
                      <Button
                        key={prompt}
                        variant="outline"
                        size="lg"
                        className="h-auto justify-between rounded-xl border-slate-200 bg-white px-3 py-3 text-left text-sm shadow-sm hover:border-cyan-200 hover:bg-cyan-50/50"
                        onClick={() => sendMessage(prompt)}
                      >
                        <span className="flex min-w-0 items-center gap-2">
                          <Icon className="h-4 w-4 shrink-0 text-cyan-700" />
                          <span className="truncate">{prompt}</span>
                        </span>
                        <ArrowRight className="h-4 w-4 text-slate-400" />
                      </Button>
                    )
                  })}
                </div>
              </div>
            </div>
          ) : (
            <div className="space-y-5 pl-1 pr-4">
              {messages.map((msg) => {
                const isLegacyReply = msg.replyType === undefined
                const isNonSearchReply = isNonSearch(msg.replyType)
                const assistantContent = formatAssistantContent(msg.content, msg.missingFields)
                const missingFields = uniqueFields(msg.missingFields)
                const showFollowUpQuestion =
                  Boolean(msg.followUpQuestion) &&
                  msg.followUpQuestion !== msg.content &&
                  msg.followUpQuestion !== assistantContent &&
                  !isNonSearchReply &&
                  (isLegacyReply || isRecommendation(msg.replyType) || msg.replyType === "NO_RESULT")
                const showMissingFields =
                  Boolean(missingFields.length) &&
                  !isNonSearchReply &&
                  (isLegacyReply || isFollowUp(msg.replyType))
                const showQuickActions = Boolean(msg.quickActions?.length)
                const showSearchUrl =
                  Boolean(msg.searchUrl) &&
                  !isNonSearchReply &&
                  (isLegacyReply || isRecommendation(msg.replyType))
                const showFlights =
                  Boolean(msg.flights?.length) &&
                  !isNonSearchReply &&
                  (isLegacyReply || isRecommendation(msg.replyType))

                if (msg.role === "user") {
                  return (
                    <div key={msg.id} className="flex justify-end">
                      <div className="max-w-[88%] rounded-2xl rounded-br-md bg-slate-950 px-4 py-2.5 text-sm leading-6 text-white shadow-sm sm:max-w-[74%]">
                        <p className="whitespace-pre-wrap break-words">{msg.content}</p>
                      </div>
                    </div>
                  )
                }

                return (
                  <div key={msg.id} className="flex items-start gap-3">
                    <div className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-cyan-100 bg-cyan-50">
                      <Bot className="h-4 w-4 text-cyan-700" />
                    </div>

                    <div className="min-w-0 max-w-[92%] space-y-3 sm:max-w-[78%]">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-medium text-slate-500">SkyBooker AI</span>
                        {msg.replyType && (
                          <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] font-medium text-slate-500">
                            {REPLY_LABELS[msg.replyType] || "助手回复"}
                          </span>
                        )}
                      </div>

                      <div className="rounded-2xl rounded-tl-md border border-slate-200 bg-white px-4 py-3 text-sm leading-6 text-slate-800 shadow-sm">
                        <p className="whitespace-pre-wrap break-words">{assistantContent}</p>

                        {showFollowUpQuestion && (
                          <div className="mt-3 rounded-xl border border-cyan-100 bg-cyan-50/70 px-3 py-2 text-sm font-medium text-cyan-900">
                            {msg.followUpQuestion}
                          </div>
                        )}

                        {showMissingFields && (
                          <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50/80 p-3">
                            <div className="flex items-center gap-2 text-xs font-semibold text-amber-800">
                              <HelpCircle className="h-3.5 w-3.5" />
                              还差这些信息
                            </div>
                            <div className="mt-2 grid gap-2 sm:grid-cols-2">
                              {missingFields.map((field) => (
                                <div
                                  key={field}
                                  className="rounded-lg border border-amber-200/80 bg-white/70 px-3 py-2"
                                >
                                  <p className="text-sm font-medium text-amber-950">{getFieldLabel(field)}</p>
                                  <p className="mt-0.5 text-xs leading-5 text-amber-700">{getFieldHint(field)}</p>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {showQuickActions && (
                          <div className="mt-3 flex flex-wrap gap-2">
                            {msg.quickActions?.map((qa, i) => (
                              <Button
                                key={i}
                                variant="outline"
                                size="sm"
                                className="h-auto rounded-lg border-slate-200 bg-white px-2.5 py-1.5 text-xs"
                                onClick={() => sendMessage(qa.value)}
                              >
                                {qa.label}
                                <ArrowRight className="ml-1 h-3 w-3" />
                              </Button>
                            ))}
                          </div>
                        )}

                        {showSearchUrl && (
                          <Button
                            size="sm"
                            className="mt-3 h-8 rounded-lg bg-slate-950 px-3 text-xs text-white hover:bg-slate-800"
                            onClick={() => router.push(msg.searchUrl!)}
                          >
                            <Search className="mr-1.5 h-3.5 w-3.5" />
                            查看全部航班
                          </Button>
                        )}
                      </div>

                      {showFlights && (
                        <div className="space-y-2 border-l border-cyan-200 pl-3">
                          {msg.flights?.map((f) => (
                            <JourneyCard key={`${f.journeyType}-${f.id}`} journey={f} />
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}

              {isLoading && (
                <div className="flex items-start gap-3">
                  <div className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl border border-cyan-100 bg-cyan-50">
                    <Bot className="h-4 w-4 text-cyan-700" />
                  </div>
                  <div className="rounded-2xl rounded-tl-md border border-slate-200 bg-white px-4 py-3 shadow-sm">
                    <div className="flex items-center gap-2 text-sm text-slate-500">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      正在分析
                    </div>
                  </div>
                </div>
              )}

              {error && (
                <div className="flex justify-center">
                  <div className="rounded-lg bg-destructive/10 px-4 py-2 text-xs text-destructive">{error}</div>
                </div>
              )}
            </div>
          )}
        </ScrollArea>

        <div className="shrink-0 border-t border-slate-200/80 py-3">
          {!isAuthenticated ? (
            <div className="text-center py-3">
              <p className="text-sm text-muted-foreground mb-2">请先登录以使用 AI 助手</p>
              <Button render={<a href="/login?redirect=/ai-assistant">去登录</a>} size="sm" nativeButton={false} />
            </div>
          ) : (
            <div className="flex items-end gap-2 rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">
              <Textarea
                placeholder="例如：广州到北京明天机票，或 周末想看海预算别太高"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={2}
                className="max-h-32 min-h-11 resize-none border-0 bg-transparent px-2 py-2 text-sm shadow-none focus-visible:ring-0"
                disabled={isLoading}
              />
              <Button
                size="icon-lg"
                onClick={handleSend}
                disabled={!input.trim() || isLoading}
                className="shrink-0 rounded-xl bg-slate-950 text-white hover:bg-slate-800"
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
