"use client"

import { useEffect, useMemo, useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import * as flightApi from "@/services/flightApi"
import type { FareCalendarVO } from "@/types/flight"

const DAY = 86_400_000
const toDate = (value: string) => new Date(`${value}T00:00:00`)
const key = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`

export function DateFareStrip({ origin, destination, selectedDate, onSelect }: {
  origin: string; destination: string; selectedDate: string; onSelect: (date: string) => void
}) {
  const [windowStart, setWindowStart] = useState(() => new Date(toDate(selectedDate).getTime() - 3 * DAY))
  const [fares, setFares] = useState<FareCalendarVO[]>([])
  const [loading, setLoading] = useState(false)
  const dates = useMemo(() => Array.from({ length: 7 }, (_, index) => new Date(windowStart.getTime() + index * DAY)), [windowStart])

  useEffect(() => {
    setWindowStart(new Date(toDate(selectedDate).getTime() - 3 * DAY))
  }, [selectedDate])
  useEffect(() => {
    let active = true
    setLoading(true)
    flightApi.getFareCalendar({ departureCity: origin, arrivalCity: destination, startDate: key(windowStart), days: 7 })
      .then((data) => active && setFares(data)).catch(() => active && setFares([])).finally(() => active && setLoading(false))
    return () => { active = false }
  }, [destination, origin, windowStart])

  const fareByDate = new Map(fares.map((fare) => [fare.date, fare.lowestPrice]))
  return <div className="mt-4 flex items-stretch gap-1 rounded-xl border bg-white p-2 shadow-sm" aria-label="快捷日期最低价">
    <Button variant="ghost" size="icon" aria-label="前 7 天" onClick={() => setWindowStart((date) => new Date(date.getTime() - 7 * DAY))}><ChevronLeft className="h-4 w-4"/></Button>
    <div className="grid min-w-0 flex-1 grid-cols-7 gap-1">{dates.map((date) => {
      const dateKey = key(date); const price = fareByDate.get(dateKey); const active = dateKey === selectedDate
      return <button key={dateKey} type="button" onClick={() => onSelect(dateKey)} className={`min-w-0 rounded-lg px-1 py-2 text-center transition-colors ${active ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}>
        <span className="block text-xs font-medium">{String(date.getMonth() + 1).padStart(2, "0")}-{String(date.getDate()).padStart(2, "0")} 周{"日一二三四五六"[date.getDay()]}</span>
        <span className={`block truncate text-xs ${active ? "text-primary-foreground/85" : "text-muted-foreground"}`}>{loading ? "…" : price == null ? "暂无" : `¥${price}`}</span>
      </button>
    })}</div>
    <Button variant="ghost" size="icon" aria-label="后 7 天" onClick={() => setWindowStart((date) => new Date(date.getTime() + 7 * DAY))}><ChevronRight className="h-4 w-4"/></Button>
  </div>
}
