"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Search, ArrowRightLeft, Calendar } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

interface FlightSearchCardProps {
  defaultDepartureCity?: string
  defaultArrivalCity?: string
  defaultDepartureDate?: string
  compact?: boolean
  onSearch?: () => void
}

export function FlightSearchCard({
  defaultDepartureCity = "",
  defaultArrivalCity = "",
  defaultDepartureDate = "",
  compact = false,
  onSearch,
}: FlightSearchCardProps) {
  const router = useRouter()
  const [departureCity, setDepartureCity] = useState(defaultDepartureCity)
  const [arrivalCity, setArrivalCity] = useState(defaultArrivalCity)
  const [departureDate, setDepartureDate] = useState(defaultDepartureDate)

  const handleSearch = () => {
    const params = new URLSearchParams()
    if (departureCity) params.set("departureCity", departureCity)
    if (arrivalCity) params.set("arrivalCity", arrivalCity)
    if (departureDate) params.set("departureDate", departureDate)
    onSearch?.()
    router.push(`/flights?${params.toString()}`)
  }

  const swapCities = () => {
    setDepartureCity(arrivalCity)
    setArrivalCity(departureCity)
  }

  const today = new Date().toISOString().split("T")[0]

  return (
    <div
      className={`rounded-2xl border border-slate-200 bg-white shadow-sm ${
        compact ? "p-4" : "p-6"
      }`}
    >
      <div className={`grid gap-4 ${compact ? "grid-cols-1 sm:grid-cols-3" : "md:grid-cols-[1fr_auto_1fr_1fr_auto]"} items-end`}>
        {/* 出发城市 */}
        <div className="space-y-1.5">
          <Label htmlFor="depCity" className="text-xs text-muted-foreground">
            出发城市
          </Label>
          <Input
            id="depCity"
            placeholder={compact ? "出发城市" : "例如：上海"}
            value={departureCity}
            onChange={(e) => setDepartureCity(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
        </div>

        {/* 交换按钮 */}
        <div className="flex justify-center">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={swapCities}
            className="rounded-full"
            title="交换出发和到达城市"
          >
            <ArrowRightLeft className="h-4 w-4" />
          </Button>
        </div>

        {/* 到达城市 */}
        <div className="space-y-1.5">
          <Label htmlFor="arrCity" className="text-xs text-muted-foreground">
            到达城市
          </Label>
          <Input
            id="arrCity"
            placeholder={compact ? "到达城市" : "例如：北京"}
            value={arrivalCity}
            onChange={(e) => setArrivalCity(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
        </div>

        {/* 出发日期 */}
        <div className="space-y-1.5">
          <Label htmlFor="depDate" className="text-xs text-muted-foreground">
            出发日期
          </Label>
          <div className="relative">
            <Input
              id="depDate"
              type="date"
              min={today}
              value={departureDate}
              onChange={(e) => setDepartureDate(e.target.value)}
              className="pl-9"
            />
            <Calendar className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
          </div>
        </div>

        {/* 搜索按钮 */}
        <Button onClick={handleSearch} size={compact ? "default" : "lg"} className="gap-2">
          <Search className="h-4 w-4" />
          搜索航班
        </Button>
      </div>
    </div>
  )
}
