"use client"

import { useEffect, useMemo, useState } from "react"
import { Loader2, Plane, Route } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { getErrorMessage } from "@/lib/error-codes"
import * as adminApi from "@/services/adminApi"
import type { FlightSeatVO, ItineraryVO } from "@/types/flight"
import type { OrderVO } from "@/types/order"

interface Props { open: boolean; onOpenChange: (open: boolean) => void; order: OrderVO | null; onSuccess?: () => Promise<void> | void }

function dateOnly(value?: string) { return value?.slice(0, 10) ?? "" }
function plusDays(value: string, days: number) { const date = new Date(`${value}T00:00:00`); date.setDate(date.getDate() + days); return date.toISOString().slice(0, 10) }

export function ConnectingChangeOrderDialog({ open, onOpenChange, order, onSuccess }: Props) {
  const [startDate, setStartDate] = useState(""); const [endDate, setEndDate] = useState("")
  const [options, setOptions] = useState<ItineraryVO[]>([]); const [selected, setSelected] = useState<ItineraryVO | null>(null)
  const [seats, setSeats] = useState<Record<number, FlightSeatVO[]>>({}); const [assignments, setAssignments] = useState<Record<string, string>>({})
  const [reason, setReason] = useState(""); const [force, setForce] = useState(false); const [loading, setLoading] = useState<string | null>(null); const [error, setError] = useState<string | null>(null)
  const [autoLoaded, setAutoLoaded] = useState(false)

  useEffect(() => { if (!open || !order) return; const start = dateOnly(order.departureTime ?? order.segments?.[0]?.departureTime); setStartDate(start); setEndDate(start ? plusDays(start, 30) : ""); setOptions([]); setSelected(null); setSeats({}); setAssignments({}); setReason(""); setForce(false); setError(null); setAutoLoaded(false) }, [open, order])

  const loadOptions = async () => { if (!order || !startDate || !endDate) return; setLoading("options"); setError(null); setSelected(null); try { setOptions(await adminApi.getAdminConnectingChangeOptions(order.id, startDate, endDate)) } catch (err) { setError(getErrorMessage(err, "加载整段改签方案失败")) } finally { setLoading(null) } }
  useEffect(() => { if (open && order && startDate && endDate && !autoLoaded) { setAutoLoaded(true); void loadOptions() } }, [autoLoaded, open, order, startDate, endDate]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { if (!selected) return; let cancelled = false; setLoading("seats"); Promise.all(selected.segments.map(async (flight) => [flight.id, await adminApi.getAdminFlightSeats(flight.id)] as const)).then((rows) => { if (!cancelled) setSeats(Object.fromEntries(rows)) }).catch((err) => !cancelled && setError(getErrorMessage(err, "加载替代行程座位失败"))).finally(() => !cancelled && setLoading(null)); return () => { cancelled = true } }, [selected])

  const choices = (flightId: number, passengerId: number) => { const used = new Set(Object.entries(assignments).filter(([key, value]) => key.startsWith(`${flightId}:`) && key !== `${flightId}:${passengerId}` && value).map(([, value]) => Number(value))); return (seats[flightId] ?? []).filter((seat) => seat.status === "AVAILABLE" && !used.has(seat.id)) }
  const complete = useMemo(() => Boolean(selected && order && reason.trim() && selected.segments.every((flight) => order.passengers.every((p) => assignments[`${flight.id}:${p.passengerId}`]))), [assignments, order, reason, selected])

  const submit = async () => { if (!order || !selected || !complete) { setError("请选择完整替代行程、填写原因并完成所有航段选座"); return } setLoading("submit"); setError(null); try { await adminApi.changeAdminConnectingOrder(order.id, { clientRequestId: crypto.randomUUID(), reason: reason.trim(), force, segments: selected.segments.map((flight) => ({ flightId: flight.id, items: order.passengers.map((p) => ({ passengerId: p.passengerId, seatId: Number(assignments[`${flight.id}:${p.passengerId}`]) })) })) }); onOpenChange(false); await onSuccess?.() } catch (err) { setError(getErrorMessage(err, "整段改签失败")) } finally { setLoading(null) } }

  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-4xl"><DialogHeader><DialogTitle>整段行程改签</DialogTitle><DialogDescription>一次性替换完整行程；新航段全部锁座成功后才会释放原座位。</DialogDescription></DialogHeader>{order && <div className="space-y-5">{error && <div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}<div className="rounded-xl border bg-muted/30 p-4"><p className="font-medium">{order.orderNo}</p><div className="mt-2 space-y-1 text-sm text-muted-foreground">{order.segments?.map((segment) => <p key={segment.id}>第 {segment.segmentNo} 段 · {segment.flightNo} · {segment.departureCity} → {segment.arrivalCity}</p>)}</div></div>
    <section className="space-y-3"><Label>候选出发日期窗口（最长 31 天）</Label><div className="flex flex-wrap gap-2"><Input className="w-44" type="date" value={startDate} min={dateOnly(order.departureTime)} onChange={(e) => setStartDate(e.target.value)}/><Input className="w-44" type="date" value={endDate} min={startDate} max={startDate ? plusDays(startDate, 30) : undefined} onChange={(e) => setEndDate(e.target.value)}/><Button variant="outline" onClick={loadOptions} disabled={loading === "options"}>{loading === "options" && <Loader2 className="h-4 w-4 animate-spin"/>}重新搜索</Button></div></section>
    <section className="space-y-2">{options.map((item) => <button type="button" key={item.segments.map((f) => f.id).join("-")} onClick={() => { setSelected(item); setAssignments({}) }} className={`w-full rounded-xl border p-4 text-left ${selected === item ? "border-primary bg-primary/5 ring-1 ring-primary" : "hover:border-primary/50"}`}><div className="flex justify-between"><span className="flex items-center gap-2 font-medium">{item.journeyType === "CONNECTING" ? <Route className="h-4 w-4"/> : <Plane className="h-4 w-4"/>}{item.journeyType === "CONNECTING" ? "一次中转" : "直飞"}</span><b className="text-rose-600">¥{item.estimatedAmount.toLocaleString()} / 人起</b></div><div className="mt-2 space-y-1 text-sm text-muted-foreground">{item.segments.map((flight) => <p key={flight.id}>{flight.flightNo} · {flight.departureAirportCode} → {flight.arrivalAirportCode} · {formatDateFull(flight.departureTime)} {formatTime(flight.departureTime)}</p>)}</div></button>)}</section>
    {selected && <section className="space-y-4"><Label>分航段重新选座</Label>{selected.segments.map((flight, index) => <div key={flight.id} className="rounded-xl border p-4"><h4 className="mb-3 font-medium">第 {index + 1} 段 · {flight.flightNo}</h4><div className="grid gap-3 md:grid-cols-2">{order.passengers.map((p) => <div key={p.passengerId} className="space-y-1"><Label>{p.passengerName}</Label><Select value={assignments[`${flight.id}:${p.passengerId}`] ?? ""} onValueChange={(value) => setAssignments((current) => ({ ...current, [`${flight.id}:${p.passengerId}`]: value ?? "" }))}><SelectTrigger><SelectValue placeholder="选择座位"/></SelectTrigger><SelectContent>{choices(flight.id, p.passengerId).map((seat) => <SelectItem key={seat.id} value={String(seat.id)}>{seat.seatNo} · {seat.cabinClass} · ¥{seat.price}</SelectItem>)}</SelectContent></Select></div>)}</div></div>)}</section>}
    <section className="space-y-3"><div><Label>改签原因</Label><Textarea value={reason} onChange={(e) => setReason(e.target.value)} maxLength={255}/></div><label className="flex items-center gap-2 text-sm"><Checkbox checked={force} onCheckedChange={(value) => setForce(Boolean(value))}/>强制改签（忽略起飞前 2 小时限制）</label></section></div>}<DialogFooter><Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button><Button onClick={submit} disabled={!complete || loading === "submit"}>{loading === "submit" && <Loader2 className="h-4 w-4 animate-spin"/>}确认整段改签</Button></DialogFooter></DialogContent></Dialog>
}
