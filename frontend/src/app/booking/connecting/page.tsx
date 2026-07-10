"use client"

import { Suspense, useEffect, useMemo, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { ArrowRight, Loader2, Plane } from "lucide-react"
import { UserLayout } from "@/components/layout/UserLayout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/contexts/AuthContext"
import * as flightApi from "@/services/flightApi"
import * as orderApi from "@/services/orderApi"
import * as passengerApi from "@/services/passengerApi"
import type { ApiError } from "@/lib/request"
import type { ItineraryVO } from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"

function ConnectingBookingContent() {
  const router = useRouter(); const params = useSearchParams(); const { isAuthenticated, isLoading: authLoading } = useAuth()
  const ids = useMemo(() => (params.get("segments") || "").split(",").map(Number).filter(Number.isFinite), [params])
  const [passengers, setPassengers] = useState<PassengerVO[]>([]); const [selected, setSelected] = useState<number[]>([])
  const [quote, setQuote] = useState<ItineraryVO | null>(null); const [seats, setSeats] = useState<Record<string, number>>({})
  const [error, setError] = useState<string | null>(null); const [loading, setLoading] = useState(false)
  useEffect(() => { if (!authLoading && !isAuthenticated) router.push(`/login?redirect=${encodeURIComponent(`/booking/connecting?segments=${ids.join(",")}`)}`) }, [authLoading, isAuthenticated, ids, router])
  useEffect(() => { if (isAuthenticated) passengerApi.getMyPassengers().then(setPassengers).catch(e => setError((e as ApiError).message)) }, [isAuthenticated])
  useEffect(() => { if (selected.length) flightApi.quoteItinerary({ segmentFlightIds: ids, passengerIds: selected }).then(setQuote).catch(e => setError((e as ApiError).message)); else setQuote(null) }, [ids, selected])
  const choosePassenger = (id: number, checked: boolean) => { setSelected(v => checked ? [...v, id] : v.filter(x => x !== id)); setSeats({}) }
  const complete = quote && quote.segments.every(s => selected.every(p => seats[`${s.id}-${p}`]))
  const submit = async () => { if (!quote || !complete) return; setLoading(true); setError(null); try { const order = await orderApi.createConnectingOrder({ clientRequestId: crypto.randomUUID(), segments: quote.segments.map(s => ({ flightId: s.id, items: selected.map(p => ({ passengerId: p, seatId: seats[`${s.id}-${p}`] })) })) }); router.push(`/orders/${order.id}`) } catch(e) { setError((e as ApiError).message || "联程下单失败"); setLoading(false) } }
  if (authLoading || !isAuthenticated) return null
  return <UserLayout><main className="mx-auto max-w-5xl space-y-6 px-4 py-8"><div><Badge className="mb-2 bg-sky-700">一次中转联程</Badge><h1 className="text-2xl font-bold">为每位乘机人选择两段座位</h1><p className="text-sm text-muted-foreground">两个航段将作为一个订单原子锁定、一次支付。</p></div>
    {error && <div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}
    <Card><CardContent className="p-5"><h2 className="mb-3 font-semibold">乘机人</h2><div className="grid gap-2 sm:grid-cols-2">{passengers.map(p => <Label key={p.id} className="flex cursor-pointer items-center gap-3 rounded-lg border p-3"><Checkbox checked={selected.includes(p.id)} onCheckedChange={v => choosePassenger(p.id, v === true)}/><span>{p.name}</span></Label>)}</div></CardContent></Card>
    {quote?.segments.map((segment, index) => <Card key={segment.id}><CardContent className="p-5"><div className="mb-4 flex items-center gap-2"><Plane className="h-4 w-4 text-sky-700"/><b>第 {index + 1} 段 · {segment.flightNo}</b><span>{segment.departureAirportCode}</span><ArrowRight className="h-4 w-4"/><span>{segment.arrivalAirportCode}</span></div><div className="space-y-3">{selected.map(pid => { const p = passengers.find(x => x.id === pid); const available = quote.segmentAvailability?.find(a => a.flightId === segment.id)?.seats.filter(s => s.status === "AVAILABLE") || []; return <div key={pid} className="grid items-center gap-2 sm:grid-cols-[160px_1fr]"><Label htmlFor={`seat-${segment.id}-${pid}`}>{p?.name} 的座位</Label><select id={`seat-${segment.id}-${pid}`} className="h-10 rounded-md border bg-background px-3" value={seats[`${segment.id}-${pid}`] || ""} onChange={e => setSeats(v => ({...v, [`${segment.id}-${pid}`]: Number(e.target.value)}))}><option value="">请选择座位</option>{available.map(s => <option key={s.id} value={s.id}>{s.seatNo} · {s.cabinClass} · ¥{s.price}</option>)}</select></div> })}</div></CardContent></Card>)}
    {quote && <Card><CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between"><div><p className="text-sm text-muted-foreground">预计票价（最终以所选座位为准）</p><p className="text-2xl font-bold text-rose-600">¥{quote.estimatedAmount.toLocaleString()} / 人</p><p className="text-xs text-muted-foreground">另计每段机场费 ¥50、燃油费 ¥30；共享 15 分钟支付期限</p></div><Button onClick={submit} disabled={!complete || loading}>{loading && <Loader2 className="mr-2 h-4 w-4 animate-spin"/>}确认并锁定两段座位</Button></CardContent></Card>}
  </main></UserLayout>
}

export default function ConnectingBookingPage() {
  return <Suspense fallback={<UserLayout><div className="mx-auto max-w-5xl px-4 py-10">正在加载联程预订…</div></UserLayout>}><ConnectingBookingContent /></Suspense>
}
