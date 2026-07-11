"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { Eye, EyeOff, Loader2, Pencil, Plus, Route } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Combobox } from "@/components/ui/combobox"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import * as adminApi from "@/services/adminApi"
import type { ApiError } from "@/lib/request"
import type { ConnectingItineraryAdminVO } from "@/types/admin"
import type { FlightVO } from "@/types/flight"

const flightLabel = (flight: FlightVO) =>
  `${flight.flightNo} · ${flight.departureCity} → ${flight.arrivalCity} · ${new Date(flight.departureTime).toLocaleString("zh-CN")}`

export function ConnectingItineraryManagerDialog({ open, onOpenChange }: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [items, setItems] = useState<ConnectingItineraryAdminVO[]>([])
  const [flights, setFlights] = useState<FlightVO[]>([])
  const [firstId, setFirstId] = useState<number | null>(null)
  const [secondId, setSecondId] = useState<number | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [schemes, flightPage] = await Promise.all([
        adminApi.getConnectingItineraries(),
        adminApi.getFlights({ page: 1, size: 100 }),
      ])
      setItems(schemes)
      setFlights(flightPage.records.filter((flight) => Boolean(flight.directFlag)))
      setError(null)
    } catch (cause) {
      setError((cause as ApiError).message || "加载联程方案失败")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { if (open) load() }, [load, open])

  const first = flights.find((flight) => flight.id === firstId)
  const secondOptions = useMemo(() => {
    if (!first) return flights
    const earliest = new Date(first.arrivalTime).getTime() + 90 * 60_000
    const latest = new Date(first.arrivalTime).getTime() + 360 * 60_000
    return flights.filter((flight) => flight.id !== first.id
      && flight.departureAirportId === first.arrivalAirportId
      && flight.arrivalAirportId !== first.departureAirportId
      && new Date(flight.departureTime).getTime() >= earliest
      && new Date(flight.departureTime).getTime() <= latest)
  }, [first, flights])

  const resetForm = () => { setEditingId(null); setFirstId(null); setSecondId(null) }
  const edit = (item: ConnectingItineraryAdminVO) => {
    setEditingId(item.id); setFirstId(item.firstSegment.id); setSecondId(item.secondSegment.id); setError(null)
  }
  const save = async () => {
    if (!firstId || !secondId) { setError("请选择完整的两段航班"); return }
    setSaving(true)
    try {
      const payload = { firstFlightId: firstId, secondFlightId: secondId }
      if (editingId) await adminApi.updateConnectingItinerary(editingId, payload)
      else await adminApi.createConnectingItinerary(payload)
      resetForm(); await load()
    } catch (cause) {
      setError((cause as ApiError).message || "保存联程方案失败")
    } finally { setSaving(false) }
  }
  const toggle = async (item: ConnectingItineraryAdminVO) => {
    setSaving(true)
    try {
      if (item.publishStatus === "PUBLISHED") await adminApi.unpublishConnectingItinerary(item.id)
      else await adminApi.publishConnectingItinerary(item.id)
      await load()
    } catch (cause) { setError((cause as ApiError).message || "更新方案状态失败") }
    finally { setSaving(false) }
  }

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-6xl">
      <DialogHeader><DialogTitle className="flex items-center gap-2"><Route className="h-5 w-5" />联程方案管理</DialogTitle></DialogHeader>
      <div className="rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-900">
        方案只引用两条独立航班，不复制库存。只有方案和两段航班均已上架时，用户端才会展示该联程。
      </div>
      {error && <div className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</div>}
      <section className="rounded-xl border p-4 space-y-3">
        <div className="font-medium">{editingId ? `编辑方案 #${editingId}` : "从既有航班创建方案"}</div>
        <div className="grid gap-3 md:grid-cols-2">
          <div className="space-y-1.5"><Label>第一航段</Label><Combobox options={flights} value={firstId} onValueChange={(v) => { setFirstId(Number(v)); setSecondId(null) }} placeholder="选择第一航段" searchPlaceholder="搜索航班..." emptyMessage="暂无航班" getDisplayValue={flightLabel} getSearchFields={(f) => [f.flightNo, f.departureCity, f.arrivalCity]} /></div>
          <div className="space-y-1.5"><Label>第二航段（已按机场与中转时间过滤）</Label><Combobox options={secondOptions} value={secondId} onValueChange={(v) => setSecondId(Number(v))} placeholder="选择第二航段" searchPlaceholder="搜索航班..." emptyMessage="暂无符合条件的航班" getDisplayValue={flightLabel} getSearchFields={(f) => [f.flightNo, f.departureCity, f.arrivalCity]} /></div>
        </div>
        <div className="flex justify-end gap-2">{editingId && <Button variant="ghost" onClick={resetForm}>取消编辑</Button>}<Button onClick={save} disabled={saving || !firstId || !secondId}>{saving ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Plus className="mr-1 h-4 w-4" />}{editingId ? "保存替换" : "创建草稿方案"}</Button></div>
      </section>
      {loading ? <div className="py-10 text-center text-muted-foreground">加载中…</div> : <div className="rounded-xl border overflow-hidden"><Table><TableHeader><TableRow><TableHead>方案</TableHead><TableHead>第一航段</TableHead><TableHead>中转</TableHead><TableHead>第二航段</TableHead><TableHead>状态</TableHead><TableHead className="w-40">操作</TableHead></TableRow></TableHeader><TableBody>
        {items.length === 0 ? <TableRow><TableCell colSpan={6} className="py-8 text-center text-muted-foreground">暂无联程方案</TableCell></TableRow> : items.map((item) => <TableRow key={item.id}><TableCell>#{item.id}</TableCell><TableCell>{flightLabel(item.firstSegment)}</TableCell><TableCell>{item.firstSegment.arrivalCity}<br/><span className="text-xs text-muted-foreground">{item.transferMinutes} 分钟</span></TableCell><TableCell>{flightLabel(item.secondSegment)}</TableCell><TableCell><Badge variant={item.publishStatus === "PUBLISHED" ? "secondary" : "outline"}>{item.publishStatus === "PUBLISHED" ? "已上架" : "草稿"}</Badge></TableCell><TableCell><div className="flex gap-1"><Button size="sm" variant="ghost" disabled={item.publishStatus === "PUBLISHED" || saving} onClick={() => edit(item)}><Pencil className="mr-1 h-3.5 w-3.5"/>替换航段</Button><Button size="sm" variant="ghost" disabled={saving} onClick={() => toggle(item)}>{item.publishStatus === "PUBLISHED" ? <EyeOff className="mr-1 h-3.5 w-3.5"/> : <Eye className="mr-1 h-3.5 w-3.5"/>}{item.publishStatus === "PUBLISHED" ? "下架" : "上架"}</Button></div></TableCell></TableRow>)}
      </TableBody></Table></div>}
    </DialogContent>
  </Dialog>
}
