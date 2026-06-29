"use client"

import type { FlightSeatVO } from "@/types/flight"

interface SeatMapProps {
  seats: FlightSeatVO[]
  selectedSeatIds: number[]
  maxSelect: number
  onToggleSeat: (seatId: number) => void
}

const COLUMNS = ["A", "B", "C", "D", "E", "F"]

const SEAT_TYPE_LABEL: Record<string, string> = {
  WINDOW: "窗",
  AISLE: "道",
  MIDDLE: "中",
}

function getColor(seat: FlightSeatVO, isSelected: boolean): string {
  if (isSelected) return "bg-primary text-primary-foreground border-primary hover:bg-primary/90"
  if (seat.status === "OCCUPIED") return "bg-slate-100 text-slate-300 border-slate-200 cursor-not-allowed"
  if (seat.status === "LOCKED") return "bg-amber-50 text-amber-400 border-amber-200 cursor-not-allowed"
  return "bg-white text-slate-700 border-emerald-300 hover:border-emerald-500 hover:bg-emerald-50 cursor-pointer"
}

export function SeatMap({ seats, selectedSeatIds, maxSelect: _maxSelect, onToggleSeat }: SeatMapProps) {
  // 按行号分组
  const rowMap = new Map<number, Map<string, FlightSeatVO>>()
  for (const seat of seats) {
    const match = seat.seatNo.match(/^(\d+)([A-Z])$/)
    if (!match) continue
    const row = Number(match[1])
    const col = match[2]
    if (!rowMap.has(row)) rowMap.set(row, new Map())
    rowMap.get(row)!.set(col, seat)
  }

  const rows = Array.from(rowMap.keys()).sort((a, b) => a - b)
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground text-center py-8">暂无座位信息</p>
  }

  return (
    <div>
      {/* 图例 */}
      <div className="flex flex-wrap gap-4 mb-4 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-1">
          <span className="inline-block h-3 w-3 rounded border border-emerald-300 bg-white" /> 可选
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="inline-block h-3 w-3 rounded bg-primary" /> 已选
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="inline-block h-3 w-3 rounded bg-slate-100 border border-slate-200" /> 已售
        </span>
        <span className="inline-flex items-center gap-1">
          <span className="inline-block h-3 w-3 rounded bg-amber-50 border border-amber-200" /> 锁定
        </span>
      </div>

      {/* 列标 */}
      <div className="grid gap-1 mb-1" style={{ gridTemplateColumns: `40px repeat(${COLUMNS.length}, 1fr)` }}>
        <span className="text-xs text-center text-muted-foreground">排</span>
        {COLUMNS.map((col) => (
          <span key={col} className="text-xs text-center text-muted-foreground">
            {col}
          </span>
        ))}
      </div>

      {/* 座位网格 */}
      <div className="space-y-1 max-h-[420px] overflow-y-auto">
        {rows.map((row) => (
          <div
            key={row}
            className="grid gap-1"
            style={{ gridTemplateColumns: `40px repeat(${COLUMNS.length}, 1fr)` }}
          >
            <span className="text-xs text-center text-muted-foreground self-center">{row}</span>
            {COLUMNS.map((col) => {
              const seat = rowMap.get(row)?.get(col)
              if (!seat) return <div key={`${row}${col}`} className="h-9" />

              const isSelected = selectedSeatIds.includes(seat.id)
              const isDisabled = seat.status !== "AVAILABLE"
              const canSelect = !isDisabled || isSelected

              return (
                <button
                  key={seat.id}
                  type="button"
                  disabled={!canSelect}
                  onClick={() => onToggleSeat(seat.id)}
                  className={`h-9 rounded border text-[10px] leading-tight flex flex-col items-center justify-center transition-colors ${getColor(seat, isSelected)}`}
                  title={`${seat.seatNo} ${SEAT_TYPE_LABEL[seat.seatType] || ""} ¥${seat.price}`}
                >
                  <span className="font-medium">{seat.seatNo}</span>
                  <span className="opacity-70">{SEAT_TYPE_LABEL[seat.seatType]}</span>
                </button>
              )
            })}
          </div>
        ))}
      </div>
    </div>
  )
}
