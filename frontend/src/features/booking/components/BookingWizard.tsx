"use client"

import { useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { Armchair, Check, ChevronLeft, ChevronRight, CreditCard, Loader2, Plane, Plus, UserPlus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { SeatMap } from "@/features/booking/components/SeatMap"
import { CABIN_CLASS_LABEL, CABIN_CLASS_ORDER, type CabinClass, type FlightSeatVO, type ItineraryVO } from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"
import type { OrderVO, PassengerType } from "@/types/order"
import * as flightApi from "@/services/flightApi"
import * as passengerApi from "@/services/passengerApi"
import * as orderApi from "@/services/orderApi"
import type { ApiError } from "@/lib/request"

const STEPS = [{ label: "确认行程", icon: Plane }, { label: "选择乘机人", icon: UserPlus }, { label: "选择座位", icon: Armchair }, { label: "确认支付", icon: CreditCard }]
type Selection = { cabin: CabinClass | null; seatIds: number[] }
const duration = (minutes: number) => `${Math.floor(minutes / 60)}时${minutes % 60 ? `${minutes % 60}分` : ""}`

export function BookingWizard({ journey }: { journey: ItineraryVO }) {
  const router = useRouter()
  const [step, setStep] = useState(0); const [passengers, setPassengers] = useState<PassengerVO[]>([])
  const [passengerIds, setPassengerIds] = useState<number[]>([]); const [seatData, setSeatData] = useState<Record<number, FlightSeatVO[]>>({})
  const [selection, setSelection] = useState<Record<number, Selection>>({}); const [activeSegment, setActiveSegment] = useState(0)
  const [created, setCreated] = useState<OrderVO | null>(null); const [submitting, setSubmitting] = useState(false); const [error, setError] = useState("")
  const [addOpen, setAddOpen] = useState(false); const [form, setForm] = useState({ name: "", idCardNo: "", phone: "", passengerType: "ADULT" as PassengerType })
  const requestId = useRef(crypto.randomUUID())

  const loadPassengers = () => passengerApi.getMyPassengers().then(setPassengers).catch((cause: ApiError) => setError(cause.message))
  useEffect(() => {
    loadPassengers()
    Promise.all(journey.segments.map(async (segment) => [segment.id, await flightApi.getFlightSeats(segment.id)] as const))
      .then((entries) => {
        setSeatData(Object.fromEntries(entries))
        setSelection(Object.fromEntries(entries.map(([id, seats]) => [id, {
          cabin: CABIN_CLASS_ORDER.find((cabin) => seats.some((seat) => seat.cabinClass === cabin && seat.status === "AVAILABLE")) || null,
          seatIds: [],
        }])))
      })
      .catch((cause: ApiError) => setError(cause.message))
  }, [journey])
  const selectedPassengers = passengers.filter((passenger) => passengerIds.includes(passenger.id))
  const complete = journey.segments.every((segment) => selection[segment.id]?.seatIds.length === passengerIds.length) && passengerIds.length > 0
  const selectedSeats = useMemo(() => journey.segments.flatMap((segment) => (selection[segment.id]?.seatIds || []).map((id) => seatData[segment.id]?.find((seat) => seat.id === id)).filter(Boolean) as FlightSeatVO[]), [journey, seatData, selection])
  const total = selectedSeats.reduce((sum, seat) => sum + seat.price, 0)
  const togglePassenger = (id: number) => setPassengerIds((current) => { const next = current.includes(id) ? current.filter((value) => value !== id) : [...current, id]; setSelection((all) => Object.fromEntries(Object.entries(all).map(([flightId, value]) => [flightId, { ...value, seatIds: value.seatIds.slice(0, next.length) }]))); return next })
  const changeCabin = (flightId: number, cabin: CabinClass) => setSelection((all) => ({ ...all, [flightId]: { cabin, seatIds: [] } }))
  const toggleSeat = (flightId: number, seatId: number) => setSelection((all) => { const current = all[flightId]; const exists = current.seatIds.includes(seatId); const ids = exists ? current.seatIds.filter((id) => id !== seatId) : [...current.seatIds.slice(Math.max(0, current.seatIds.length - passengerIds.length + 1)), seatId]; return { ...all, [flightId]: { ...current, seatIds: ids } } })
  const addPassenger = async () => { setSubmitting(true); try { await passengerApi.createPassenger(form); setAddOpen(false); setForm({ name: "", idCardNo: "", phone: "", passengerType: "ADULT" }); await loadPassengers() } catch (cause) { setError((cause as ApiError).message) } finally { setSubmitting(false) } }
  const submit = async () => { if (!complete) return; setSubmitting(true); setError(""); try { const segments = journey.segments.map((segment) => ({ flightId: segment.id, items: passengerIds.map((passengerId, index) => ({ passengerId, seatId: selection[segment.id].seatIds[index] })) })); const order = journey.journeyType === "DIRECT" ? await orderApi.createOrder(segments[0]) : await orderApi.createConnectingOrder({ clientRequestId: requestId.current, segments }); setCreated(order) } catch (cause) { setError((cause as ApiError).message || "创建订单失败") } finally { setSubmitting(false) } }
  const pay = async () => { if (!created) return; setSubmitting(true); try { await orderApi.payOrder(created.id); router.push(`/orders/${created.id}`) } catch (cause) { setError((cause as ApiError).message); setSubmitting(false) } }
  const segment = journey.segments[activeSegment]; const current = selection[segment.id]; const seats = seatData[segment.id] || []; const cabins = CABIN_CLASS_ORDER.filter((cabin) => seats.some((seat) => seat.cabinClass === cabin)); const filteredSeats = current?.cabin ? seats.filter((seat) => seat.cabinClass === current.cabin) : seats

  return <div className="mx-auto max-w-4xl space-y-6">
    <div className="flex items-center justify-center">{STEPS.map((item, index) => <div key={item.label} className="flex items-center"><div className="flex flex-col items-center"><div className={`flex h-10 w-10 items-center justify-center rounded-full border-2 ${index <= step ? "border-primary bg-primary text-primary-foreground" : "border-slate-200 text-muted-foreground"}`}><item.icon className="h-4 w-4"/></div><span className="mt-1 text-xs text-muted-foreground">{item.label}</span></div>{index < 3 && <div className={`mx-2 h-0.5 w-12 sm:w-20 ${index < step ? "bg-primary" : "bg-slate-200"}`}/>}</div>)}</div>
    {error && <div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}
    {step === 0 && <Card><CardContent className="space-y-4 p-6"><h2 className="font-semibold">确认行程信息</h2>{journey.segments.map((item, index) => <div key={item.id} className="grid grid-cols-[1fr_auto_1fr] items-center gap-4 rounded-lg border p-4"><div><b>{item.departureCity}</b><p className="text-sm text-muted-foreground">{new Date(item.departureTime).toLocaleString("zh-CN")}</p></div><div className="text-center text-xs text-muted-foreground"><Badge variant="outline">第 {index + 1} 段 · {item.flightNo}</Badge><p className="mt-1">{duration(item.durationMinutes)}</p></div><div className="text-right"><b>{item.arrivalCity}</b><p className="text-sm text-muted-foreground">{new Date(item.arrivalTime).toLocaleString("zh-CN")}</p></div></div>)}<div className="flex justify-end"><Button onClick={() => setStep(1)}>下一步<ChevronRight className="h-4 w-4"/></Button></div></CardContent></Card>}
    {step === 1 && <Card><CardContent className="p-6"><div className="mb-4 flex items-center justify-between"><h2 className="font-semibold">选择乘机人</h2><Button variant="outline" size="sm" onClick={() => setAddOpen(true)}><Plus className="mr-1 h-4 w-4"/>新增乘机人</Button></div><div className="space-y-2">{passengers.map((passenger) => <label key={passenger.id} className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 ${passengerIds.includes(passenger.id) ? "border-primary bg-primary/5" : ""}`}><Checkbox checked={passengerIds.includes(passenger.id)} onCheckedChange={() => togglePassenger(passenger.id)}/><span>{passenger.name}</span><span className="text-xs text-muted-foreground">{passenger.idCardNo}</span></label>)}</div><Nav back={() => setStep(0)} next={() => setStep(2)} disabled={!passengerIds.length}/></CardContent></Card>}
    {step === 2 && <Card><CardContent className="p-6"><h2 className="font-semibold">选择座位</h2><div className="my-4 flex gap-2">{journey.segments.map((item, index) => <Button key={item.id} variant={activeSegment === index ? "default" : "outline"} onClick={() => setActiveSegment(index)}>第 {index + 1} 段 {item.flightNo}{selection[item.id]?.seatIds.length === passengerIds.length && <Check className="ml-1 h-4 w-4"/>}</Button>)}</div><div className="mb-4 grid gap-2 sm:grid-cols-3">{cabins.map((cabin) => <button key={cabin} onClick={() => changeCabin(segment.id, cabin)} className={`rounded-lg border p-3 text-left ${current?.cabin === cabin ? "border-primary bg-primary/5" : ""}`}><b>{CABIN_CLASS_LABEL[cabin]}</b><p className="text-xs text-muted-foreground">可选 {seats.filter((seat) => seat.cabinClass === cabin && seat.status === "AVAILABLE").length} 座</p></button>)}</div><SeatMap seats={filteredSeats} selectedSeatIds={current?.seatIds || []} maxSelect={passengerIds.length} onToggleSeat={(id) => toggleSeat(segment.id, id)}/><Nav back={() => setStep(1)} next={() => setStep(3)} disabled={!complete}/></CardContent></Card>}
    {step === 3 && <Card><CardContent className="p-6"><h2 className="mb-4 font-semibold">订单确认</h2>{journey.segments.map((item) => <div key={item.id} className="mb-4 rounded-lg bg-slate-50 p-4"><b>{item.flightNo} · {item.departureCity} → {item.arrivalCity}</b>{selectedPassengers.map((passenger, index) => { const seat = seatData[item.id]?.find((value) => value.id === selection[item.id].seatIds[index]); return <div key={passenger.id} className="mt-2 flex justify-between text-sm"><span>{passenger.name}</span><span>{seat ? `${CABIN_CLASS_LABEL[seat.cabinClass]} ${seat.seatNo} · ¥${seat.price}` : "-"}</span></div> })}</div>)}<Separator/><div className="my-4 flex justify-between font-bold"><span>机票合计</span><span>¥{total.toLocaleString()}</span></div><div className="flex justify-between"><Button variant="ghost" onClick={() => setStep(2)}><ChevronLeft className="h-4 w-4"/>上一步</Button><div className="flex gap-2">{!created && <Button onClick={submit} disabled={submitting}>{submitting && <Loader2 className="mr-1 h-4 w-4 animate-spin"/>}提交订单</Button>}{created && <Button onClick={pay} disabled={submitting}><CreditCard className="mr-1 h-4 w-4"/>模拟支付</Button>}</div></div></CardContent></Card>}
    <Dialog open={addOpen} onOpenChange={setAddOpen}><DialogContent><DialogHeader><DialogTitle>新增乘机人</DialogTitle></DialogHeader><div className="space-y-3"><Label>姓名<Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })}/></Label><Label>身份证号<Input value={form.idCardNo} onChange={(event) => setForm({ ...form, idCardNo: event.target.value })}/></Label><Label>手机号<Input value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })}/></Label><Label>乘机人类型<Select value={form.passengerType} onValueChange={(value) => setForm({ ...form, passengerType: value as PassengerType })}><SelectTrigger><SelectValue/></SelectTrigger><SelectContent><SelectItem value="ADULT">成人</SelectItem><SelectItem value="CHILD">儿童</SelectItem><SelectItem value="INFANT">婴儿</SelectItem></SelectContent></Select></Label><Button className="w-full" onClick={addPassenger} disabled={submitting || !form.name || !form.idCardNo || !form.phone}>确认添加</Button></div></DialogContent></Dialog>
  </div>
}

function Nav({ back, next, disabled }: { back: () => void; next: () => void; disabled?: boolean }) { return <div className="mt-6 flex justify-between"><Button variant="ghost" onClick={back}><ChevronLeft className="h-4 w-4"/>上一步</Button><Button onClick={next} disabled={disabled}>下一步<ChevronRight className="h-4 w-4"/></Button></div> }
