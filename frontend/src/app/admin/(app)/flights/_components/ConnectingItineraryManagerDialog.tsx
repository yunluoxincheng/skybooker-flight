"use client"

import { useCallback, useEffect, useState } from "react"
import { ChevronDown, ChevronLeft, ChevronRight, Eye, EyeOff, Loader2, Pencil, Plus, Route, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Combobox } from "@/components/ui/combobox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import * as adminApi from "@/services/adminApi"
import type { ApiError } from "@/lib/request"
import type { AirportVO, ConnectingItinerarySummaryVO } from "@/types/admin"
import type { FlightVO } from "@/types/flight"
import { buildCandidateQuery, changesFirstFlight } from "./connectingItineraryCandidates"

const PAGE_SIZE = 10
const CANDIDATE_SIZE = 20
const flightLabel = (flight: FlightVO) =>
  `${flight.flightNo} · ${flight.departureCity} → ${flight.arrivalCity} · ${new Date(flight.departureTime).toLocaleString("zh-CN")}`

function summaryFlight(item: ConnectingItinerarySummaryVO, first: boolean): FlightVO {
  return {
    id: first ? item.firstFlightId : item.secondFlightId,
    flightNo: first ? item.firstFlightNo : item.secondFlightNo,
    departureCity: first ? item.firstDepartureCity : item.secondDepartureCity,
    arrivalCity: first ? item.firstArrivalCity : item.secondArrivalCity,
    departureTime: first ? item.firstDepartureTime : item.secondDepartureTime,
    arrivalTime: first ? item.firstArrivalTime : item.secondArrivalTime,
    remainingSeats: first ? item.firstRemainingSeats : item.secondRemainingSeats,
  } as FlightVO
}

export function ConnectingItineraryManagerDialog({ open, onOpenChange, airports }: {
  open: boolean
  onOpenChange: (open: boolean) => void
  airports: AirportVO[]
}) {
  const [items, setItems] = useState<ConnectingItinerarySummaryVO[]>([])
  const [schemePage, setSchemePage] = useState(1)
  const [schemeTotal, setSchemeTotal] = useState(0)
  const [firstOptions, setFirstOptions] = useState<FlightVO[]>([])
  const [secondOptions, setSecondOptions] = useState<FlightVO[]>([])
  const [firstSelected, setFirstSelected] = useState<FlightVO | null>(null)
  const [secondSelected, setSecondSelected] = useState<FlightVO | null>(null)
  const [firstSearch, setFirstSearch] = useState("")
  const [secondSearch, setSecondSearch] = useState("")
  const [firstPage, setFirstPage] = useState(1)
  const [secondPage, setSecondPage] = useState(1)
  const [firstTotal, setFirstTotal] = useState(0)
  const [secondTotal, setSecondTotal] = useState(0)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [startDate, setStartDate] = useState("")
  const [endDate, setEndDate] = useState("")
  const [departureAirportId, setDepartureAirportId] = useState<number | null>(null)
  const [arrivalAirportId, setArrivalAirportId] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [candidateLoading, setCandidateLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [deleting, setDeleting] = useState<ConnectingItinerarySummaryVO | null>(null)
  const [error, setError] = useState<string | null>(null)

  const loadSchemes = useCallback(async (page = schemePage) => {
    setLoading(true)
    try {
      const data = await adminApi.getConnectingItineraries({ page, size: PAGE_SIZE })
      setItems(data.records); setSchemeTotal(data.total); setError(null)
    } catch (cause) { setError((cause as ApiError).message || "加载联程方案失败") }
    finally { setLoading(false) }
  }, [schemePage])

  const loadFirst = useCallback(async () => {
    setCandidateLoading(true)
    try {
      const data = await adminApi.getConnectingFlightCandidates(buildCandidateQuery(firstPage, firstSearch, CANDIDATE_SIZE,
        { startDate, endDate, departureAirportId, arrivalAirportId }))
      setFirstOptions(data.records); setFirstTotal(data.total)
    } catch (cause) { setError((cause as ApiError).message || "加载第一航段候选失败") }
    finally { setCandidateLoading(false) }
  }, [arrivalAirportId, departureAirportId, endDate, firstPage, firstSearch, startDate])

  const loadSecond = useCallback(async () => {
    if (!firstSelected) { setSecondOptions([]); setSecondTotal(0); return }
    setCandidateLoading(true)
    try {
      const data = await adminApi.getSecondConnectingFlightCandidates(firstSelected.id,
        buildCandidateQuery(secondPage, secondSearch, CANDIDATE_SIZE))
      setSecondOptions(data.records); setSecondTotal(data.total)
    } catch (cause) { setError((cause as ApiError).message || "加载第二航段候选失败") }
    finally { setCandidateLoading(false) }
  }, [firstSelected, secondPage, secondSearch])

  useEffect(() => { if (open) loadSchemes() }, [loadSchemes, open])
  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(loadFirst, 250)
    return () => window.clearTimeout(timer)
  }, [loadFirst, open])
  useEffect(() => {
    if (!open) return
    const timer = window.setTimeout(loadSecond, 250)
    return () => window.clearTimeout(timer)
  }, [loadSecond, open])

  const resetForm = () => {
    setEditingId(null); setFirstSelected(null); setSecondSelected(null)
    setFirstSearch(""); setSecondSearch(""); setFirstPage(1); setSecondPage(1)
  }
  const chooseFirst = (value: string) => {
    const next = firstOptions.find((flight) => flight.id === Number(value)) ?? null
    if (next && changesFirstFlight(firstSelected?.id ?? null, next.id)) {
      setSecondSelected(null); setSecondSearch(""); setSecondPage(1)
    }
    setFirstSelected(next)
  }
  const edit = (item: ConnectingItinerarySummaryVO) => {
    setEditingId(item.id); setFirstSelected(summaryFlight(item, true)); setSecondSelected(summaryFlight(item, false))
    setSecondPage(1); setError(null)
  }
  const save = async () => {
    if (!firstSelected || !secondSelected) { setError("请选择完整的两段航班"); return }
    setSaving(true)
    try {
      const payload = { firstFlightId: firstSelected.id, secondFlightId: secondSelected.id }
      if (editingId) await adminApi.updateConnectingItinerary(editingId, payload)
      else await adminApi.createConnectingItinerary(payload)
      resetForm(); setSchemePage(1); await loadSchemes(1)
    } catch (cause) { setError((cause as ApiError).message || "保存联程方案失败") }
    finally { setSaving(false) }
  }
  const toggle = async (item: ConnectingItinerarySummaryVO) => {
    setSaving(true)
    try {
      if (item.publishStatus === "PUBLISHED") await adminApi.unpublishConnectingItinerary(item.id)
      else await adminApi.publishConnectingItinerary(item.id)
      await loadSchemes()
    } catch (cause) { setError((cause as ApiError).message || "更新方案状态失败") }
    finally { setSaving(false) }
  }
  const remove = async () => {
    if (!deleting) return
    setSaving(true)
    try {
      await adminApi.deleteConnectingItinerary(deleting.id)
      const nextTotal = Math.max(0, schemeTotal - 1)
      const nextPages = Math.max(1, Math.ceil(nextTotal / PAGE_SIZE))
      const nextPage = Math.min(schemePage, nextPages)
      setDeleting(null); setSchemePage(nextPage); await loadSchemes(nextPage)
    } catch (cause) { setError((cause as ApiError).message || "删除联程方案失败") }
    finally { setSaving(false) }
  }

  const firstPages = Math.max(1, Math.ceil(firstTotal / CANDIDATE_SIZE))
  const secondPages = Math.max(1, Math.ceil(secondTotal / CANDIDATE_SIZE))
  const schemePages = Math.max(1, Math.ceil(schemeTotal / PAGE_SIZE))

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-6xl">
      <DialogHeader><DialogTitle className="flex items-center gap-2"><Route className="h-5 w-5" />联程方案管理</DialogTitle></DialogHeader>
      <div className="rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-900">
        方案只引用两条独立航班，不复制库存。首次上架要求两段均已上架、未来可售且至少各有一张可用座位；后续售罄会保留上架状态并显示原因。
      </div>
      {error && <div className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</div>}
      <section className="rounded-xl border p-4 space-y-3">
        <div className="font-medium">{editingId ? `编辑方案 #${editingId}` : "从既有航班创建方案"}</div>
        <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={() => setFiltersOpen((value) => !value)}>
          <ChevronDown className={`mr-1 h-4 w-4 transition-transform ${filtersOpen ? "rotate-180" : ""}`} />高级筛选航班
        </Button>
        {filtersOpen && <div className="grid gap-3 rounded-lg bg-muted/40 p-3 sm:grid-cols-4">
          <div className="space-y-1"><Label>出发日期从</Label><Input type="date" value={startDate} onChange={(event) => { setStartDate(event.target.value); setFirstPage(1) }} /></div>
          <div className="space-y-1"><Label>出发日期至</Label><Input type="date" value={endDate} onChange={(event) => { setEndDate(event.target.value); setFirstPage(1) }} /></div>
          <div className="space-y-1"><Label>出发机场</Label><Combobox options={airports} value={departureAirportId} onValueChange={(value) => { setDepartureAirportId(Number(value)); setFirstPage(1) }} placeholder="全部出发机场" searchPlaceholder="搜索机场..." emptyMessage="暂无机场" getDisplayValue={(airport) => `${airport.name} (${airport.code})`} getSearchFields={(airport) => [airport.name, airport.code, airport.city]} /></div>
          <div className="space-y-1"><Label>到达/中转机场</Label><Combobox options={airports} value={arrivalAirportId} onValueChange={(value) => { setArrivalAirportId(Number(value)); setFirstPage(1) }} placeholder="全部到达机场" searchPlaceholder="搜索机场..." emptyMessage="暂无机场" getDisplayValue={(airport) => `${airport.name} (${airport.code})`} getSearchFields={(airport) => [airport.name, airport.code, airport.city]} /></div>
        </div>}
        {filtersOpen && (startDate || endDate || departureAirportId || arrivalAirportId) && <div className="flex justify-end"><Button type="button" size="sm" variant="ghost" onClick={() => { setStartDate(""); setEndDate(""); setDepartureAirportId(null); setArrivalAirportId(null); setFirstPage(1) }}>清除候选筛选</Button></div>}
        <div className="grid gap-3 md:grid-cols-2">
          <CandidateField label="第一航段" options={firstOptions} selected={firstSelected} page={firstPage} pages={firstPages}
            loading={candidateLoading} onSearch={(value) => { setFirstSearch(value); setFirstPage(1) }}
            onSelect={chooseFirst} onPage={setFirstPage} />
          <CandidateField label="第二航段（服务端按机场与 90–360 分钟过滤）" options={secondOptions} selected={secondSelected}
            page={secondPage} pages={secondPages} loading={candidateLoading} disabled={!firstSelected}
            onSearch={(value) => { setSecondSearch(value); setSecondPage(1) }}
            onSelect={(value) => setSecondSelected(secondOptions.find((flight) => flight.id === Number(value)) ?? null)} onPage={setSecondPage} />
        </div>
        {firstSelected && <div className="grid gap-3 rounded-lg border bg-slate-50 p-3 text-sm sm:grid-cols-2">
          <div><span className="text-muted-foreground">已推导第一段：</span>{firstSelected.departureAirportCode} → {firstSelected.arrivalAirportCode}<br/><span className="text-xs text-muted-foreground">{new Date(firstSelected.departureTime).toLocaleString("zh-CN")} 起飞，{new Date(firstSelected.arrivalTime).toLocaleString("zh-CN")} 到达中转机场</span></div>
          <div>{secondSelected ? <><span className="text-muted-foreground">完整路线：</span>{firstSelected.departureCity} → {firstSelected.arrivalCity} → {secondSelected.arrivalCity}<br/><span className="text-xs text-muted-foreground">第二段 {new Date(secondSelected.departureTime).toLocaleString("zh-CN")} 起飞 · 共享余票 {Math.min(firstSelected.remainingSeats, secondSelected.remainingSeats)}</span></> : <span className="text-muted-foreground">请选择服务端匹配的第二航段</span>}</div>
        </div>}
        <div className="flex justify-end gap-2">{editingId && <Button variant="ghost" onClick={resetForm}>取消编辑</Button>}<Button onClick={save} disabled={saving || !firstSelected || !secondSelected}>{saving ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Plus className="mr-1 h-4 w-4" />}{editingId ? "保存替换" : "创建草稿方案"}</Button></div>
      </section>
      {loading ? <div className="py-10 text-center text-muted-foreground">加载中…</div> : <div className="rounded-xl border overflow-hidden"><Table><TableHeader><TableRow><TableHead>方案</TableHead><TableHead>第一航段</TableHead><TableHead>中转</TableHead><TableHead>第二航段</TableHead><TableHead>共享余票</TableHead><TableHead>状态</TableHead><TableHead className="w-40">操作</TableHead></TableRow></TableHeader><TableBody>
        {items.length === 0 ? <TableRow><TableCell colSpan={7} className="py-8 text-center text-muted-foreground">暂无联程方案</TableCell></TableRow> : items.map((item) => <TableRow key={item.id}>
          <TableCell>#{item.id}</TableCell>
          <TableCell>{item.firstFlightNo} · {item.firstDepartureCity} → {item.firstArrivalCity}<br/><span className="text-xs text-muted-foreground">{new Date(item.firstDepartureTime).toLocaleString("zh-CN")} → {new Date(item.firstArrivalTime).toLocaleString("zh-CN")} · 余票 {item.firstRemainingSeats}</span></TableCell>
          <TableCell>{item.firstArrivalCity}<br/><span className="text-xs text-muted-foreground">{item.transferMinutes} 分钟</span></TableCell>
          <TableCell>{item.secondFlightNo} · {item.secondDepartureCity} → {item.secondArrivalCity}<br/><span className="text-xs text-muted-foreground">{new Date(item.secondDepartureTime).toLocaleString("zh-CN")} → {new Date(item.secondArrivalTime).toLocaleString("zh-CN")} · 余票 {item.secondRemainingSeats}</span></TableCell>
          <TableCell className="font-medium">{item.availableSeats}</TableCell>
          <TableCell><div className="space-y-1"><Badge variant={item.publishStatus === "PUBLISHED" ? "secondary" : "outline"}>{item.publishStatus === "PUBLISHED" ? "已上架" : "草稿"}</Badge>{item.publishStatus === "PUBLISHED" && !item.sellable && <div className="text-xs text-amber-700">暂不可售：{item.unavailableReason}</div>}</div></TableCell>
          <TableCell><div className="flex flex-wrap gap-1"><Button size="sm" variant="ghost" disabled={item.publishStatus === "PUBLISHED" || saving} onClick={() => edit(item)}><Pencil className="mr-1 h-3.5 w-3.5"/>替换</Button><Button size="sm" variant="ghost" disabled={saving} onClick={() => toggle(item)}>{item.publishStatus === "PUBLISHED" ? <EyeOff className="mr-1 h-3.5 w-3.5"/> : <Eye className="mr-1 h-3.5 w-3.5"/>}{item.publishStatus === "PUBLISHED" ? "下架" : "上架"}</Button>{item.publishStatus === "DRAFT" && <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" disabled={saving} onClick={() => setDeleting(item)}><Trash2 className="mr-1 h-3.5 w-3.5"/>删除</Button>}</div></TableCell>
        </TableRow>)}
      </TableBody></Table></div>}
      {schemePages > 1 && <Pager page={schemePage} pages={schemePages} onPage={setSchemePage} />}
      <Dialog open={Boolean(deleting)} onOpenChange={(value) => !value && setDeleting(null)}><DialogContent><DialogHeader><DialogTitle>删除联程方案？</DialogTitle><DialogDescription>将只删除联程关系，不会删除两条底层航班、座位或历史订单。删除后可使用相同航段重新创建方案。</DialogDescription></DialogHeader><DialogFooter><Button variant="outline" onClick={() => setDeleting(null)}>取消</Button><Button variant="destructive" onClick={remove} disabled={saving}>{saving && <Loader2 className="mr-1 h-4 w-4 animate-spin"/>}删除方案</Button></DialogFooter></DialogContent></Dialog>
    </DialogContent>
  </Dialog>
}

function CandidateField({ label, options, selected, page, pages, loading, disabled, onSearch, onSelect, onPage }: {
  label: string; options: FlightVO[]; selected: FlightVO | null; page: number; pages: number; loading: boolean; disabled?: boolean
  onSearch: (value: string) => void; onSelect: (value: string) => void; onPage: (page: number) => void
}) {
  return <div className="space-y-1.5"><Label>{label}</Label><Combobox options={options} value={selected?.id ?? null} selectedOption={selected}
    onValueChange={onSelect} onSearch={onSearch} loading={loading} disabled={disabled} placeholder="选择航段"
    searchPlaceholder="按航班号、航司、机场或城市搜索..." emptyMessage="暂无符合条件的航班"
    getDisplayValue={flightLabel} getSearchFields={(flight) => [flight.flightNo, flight.airlineName, flight.airlineCode, flight.departureCity, flight.arrivalCity, flight.departureAirportCode, flight.departureAirportName, flight.arrivalAirportCode, flight.arrivalAirportName]} />
    {pages > 1 && <Pager page={page} pages={pages} onPage={onPage} compact />}</div>
}

function Pager({ page, pages, onPage, compact = false }: { page: number; pages: number; onPage: (page: number) => void; compact?: boolean }) {
  return <div className={`flex items-center justify-center gap-2 ${compact ? "pt-1" : "pt-3"}`}>
    <Button type="button" size="sm" variant="outline" disabled={page <= 1} onClick={() => onPage(page - 1)}><ChevronLeft className="h-3.5 w-3.5"/></Button>
    <span className="text-xs text-muted-foreground">{page} / {pages}</span>
    <Button type="button" size="sm" variant="outline" disabled={page >= pages} onClick={() => onPage(page + 1)}><ChevronRight className="h-3.5 w-3.5"/></Button>
  </div>
}
