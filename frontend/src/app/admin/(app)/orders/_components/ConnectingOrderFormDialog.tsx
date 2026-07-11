"use client"

import { useEffect, useMemo, useState } from "react"
import { Loader2, Route } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { getErrorMessage } from "@/lib/error-codes"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import { getDisplayName } from "@/lib/user-utils"
import * as adminApi from "@/services/adminApi"
import type { UserAdminVO } from "@/types/admin"
import type { FlightSeatVO, ItineraryVO } from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess?: () => Promise<void> | void
}

function formatFlight(flight: ItineraryVO["segments"][number]) {
  return `${flight.flightNo} · ${flight.departureAirportCode} → ${flight.arrivalAirportCode} · ${formatDateFull(flight.departureTime)} ${formatTime(flight.departureTime)}`
}

export function ConnectingOrderFormDialog({ open, onOpenChange, onSuccess }: Props) {
  const [userKeyword, setUserKeyword] = useState("")
  const [users, setUsers] = useState<UserAdminVO[]>([])
  const [userId, setUserId] = useState("")
  const [passengers, setPassengers] = useState<PassengerVO[]>([])
  const [passengerIds, setPassengerIds] = useState<number[]>([])
  const [departureCity, setDepartureCity] = useState("")
  const [arrivalCity, setArrivalCity] = useState("")
  const [departureDate, setDepartureDate] = useState("")
  const [itineraries, setItineraries] = useState<ItineraryVO[]>([])
  const [selected, setSelected] = useState<ItineraryVO | null>(null)
  const [seatsByFlight, setSeatsByFlight] = useState<Record<number, FlightSeatVO[]>>({})
  const [assignments, setAssignments] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    setUserKeyword(""); setUsers([]); setUserId(""); setPassengers([]); setPassengerIds([])
    setDepartureCity(""); setArrivalCity(""); setDepartureDate(""); setItineraries([]); setSelected(null)
    setSeatsByFlight({}); setAssignments({}); setLoading(null); setError(null)
  }, [open])

  useEffect(() => {
    if (!userId) { setPassengers([]); return }
    let cancelled = false
    setLoading("passengers")
    adminApi.getPassengersByUser(Number(userId)).then((data) => {
      if (!cancelled) setPassengers(data)
    }).catch((err) => !cancelled && setError(getErrorMessage(err, "加载乘机人失败")))
      .finally(() => !cancelled && setLoading(null))
    return () => { cancelled = true }
  }, [userId])

  useEffect(() => {
    if (!selected) { setSeatsByFlight({}); return }
    let cancelled = false
    setLoading("seats")
    Promise.all(selected.segments.map(async (flight) => [flight.id, await adminApi.getAdminFlightSeats(flight.id)] as const))
      .then((rows) => { if (!cancelled) setSeatsByFlight(Object.fromEntries(rows)) })
      .catch((err) => !cancelled && setError(getErrorMessage(err, "加载联程座位失败")))
      .finally(() => !cancelled && setLoading(null))
    return () => { cancelled = true }
  }, [selected])

  const searchUsers = async () => {
    if (!userKeyword.trim()) return
    setLoading("users"); setError(null)
    try {
      const data = await adminApi.getUsers({ keyword: userKeyword.trim(), role: "USER", page: 1, size: 50 })
      setUsers(data.records)
    } catch (err) { setError(getErrorMessage(err, "搜索用户失败")) }
    finally { setLoading(null) }
  }

  const searchItineraries = async () => {
    if (!departureCity.trim() || !arrivalCity.trim() || !departureDate || passengerIds.length === 0) {
      setError("请先选择乘机人，并填写出发地、目的地和出发日期")
      return
    }
    setLoading("itineraries"); setError(null); setSelected(null); setAssignments({})
    try {
      const data = await adminApi.searchAdminItineraries({
        departureCity: departureCity.trim(), arrivalCity: arrivalCity.trim(), departureDate,
        directOnly: false, page: 1, size: 100,
      })
      setItineraries(data.records.filter((item) => item.journeyType === "CONNECTING" && item.availableSeats >= passengerIds.length))
    } catch (err) { setError(getErrorMessage(err, "搜索中转联程失败")) }
    finally { setLoading(null) }
  }

  const availableSeats = (flightId: number, passengerId: number) => {
    const used = new Set(Object.entries(assignments)
      .filter(([key, value]) => key.startsWith(`${flightId}:`) && key !== `${flightId}:${passengerId}` && value)
      .map(([, value]) => Number(value)))
    return (seatsByFlight[flightId] ?? []).filter((seat) => seat.status === "AVAILABLE" && !used.has(seat.id))
  }

  const canSubmit = useMemo(() => Boolean(userId && selected && passengerIds.length > 0 && selected.segments.every((flight) =>
    passengerIds.every((passengerId) => assignments[`${flight.id}:${passengerId}`]))), [assignments, passengerIds, selected, userId])

  const submit = async () => {
    if (!selected || !canSubmit) { setError("请为每位乘机人分配两个航段的座位"); return }
    setLoading("submit"); setError(null)
    try {
      await adminApi.createAdminConnectingOrder({
        userId: Number(userId), clientRequestId: crypto.randomUUID(),
        segments: selected.segments.map((flight) => ({ flightId: flight.id, items: passengerIds.map((passengerId) => ({
          passengerId, seatId: Number(assignments[`${flight.id}:${passengerId}`]),
        })) })),
      })
      onOpenChange(false); await onSuccess?.()
    } catch (err) { setError(getErrorMessage(err, "创建中转联程订单失败")) }
    finally { setLoading(null) }
  }

  return <Dialog open={open} onOpenChange={onOpenChange}>
    <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-4xl">
      <DialogHeader><DialogTitle>新增中转联程订单</DialogTitle><DialogDescription>管理员代用户搜索一次中转行程，并为两个航段分别完成选座。</DialogDescription></DialogHeader>
      <div className="space-y-5">
        {error && <div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}
        <section className="space-y-3"><Label>1. 选择用户</Label><div className="flex gap-2"><Input value={userKeyword} onChange={(e) => setUserKeyword(e.target.value)} placeholder="昵称 / 邮箱"/><Button type="button" variant="outline" onClick={searchUsers} disabled={loading === "users"}>搜索</Button></div>
          <Select value={userId} onValueChange={(value) => { setUserId(value ?? ""); setPassengerIds([]); setSelected(null) }}><SelectTrigger><SelectValue placeholder="请选择用户"/></SelectTrigger><SelectContent>{users.map((user) => <SelectItem key={user.id} value={String(user.id)}>{getDisplayName(user)} · {user.email}</SelectItem>)}</SelectContent></Select></section>
        <section className="space-y-2"><Label>2. 选择乘机人</Label>{passengers.map((p) => <label key={p.id} className="flex items-center gap-3 rounded-lg border p-3 text-sm"><Checkbox checked={passengerIds.includes(p.id)} onCheckedChange={(checked) => { setPassengerIds((ids) => checked ? [...new Set([...ids, p.id])] : ids.filter((id) => id !== p.id)); setSelected(null); setAssignments({}) }}/><span>{p.name} · {p.passengerType}</span></label>)}</section>
        <section className="space-y-3"><Label>3. 搜索一次中转行程</Label><div className="grid gap-3 md:grid-cols-3"><Input value={departureCity} onChange={(e) => setDepartureCity(e.target.value)} placeholder="出发城市"/><Input value={arrivalCity} onChange={(e) => setArrivalCity(e.target.value)} placeholder="到达城市"/><Input type="date" value={departureDate} onChange={(e) => setDepartureDate(e.target.value)}/></div><Button type="button" onClick={searchItineraries} disabled={loading === "itineraries"}>{loading === "itineraries" && <Loader2 className="h-4 w-4 animate-spin"/>}搜索联程</Button>
          <div className="space-y-2">{itineraries.map((item) => <button type="button" key={item.segments.map((f) => f.id).join("-")} onClick={() => { setSelected(item); setAssignments({}) }} className={`w-full rounded-xl border p-4 text-left ${selected === item ? "border-primary bg-primary/5 ring-1 ring-primary" : "hover:border-primary/50"}`}><div className="flex justify-between gap-3"><span className="flex items-center gap-2 font-medium"><Route className="h-4 w-4"/>{item.originCity} → {item.destinationCity}</span><span className="font-semibold text-rose-600">¥{item.estimatedAmount.toLocaleString()} / 人起</span></div><div className="mt-2 space-y-1 text-sm text-muted-foreground">{item.segments.map((flight) => <p key={flight.id}>{formatFlight(flight)}</p>)}</div></button>)}</div></section>
        {selected && <section className="space-y-4"><Label>4. 分航段选座</Label>{selected.segments.map((flight, index) => <div key={flight.id} className="rounded-xl border p-4"><h4 className="mb-3 font-medium">第 {index + 1} 段 · {formatFlight(flight)}</h4><div className="grid gap-3 md:grid-cols-2">{passengerIds.map((passengerId) => { const passenger = passengers.find((p) => p.id === passengerId); return <div key={passengerId} className="space-y-1"><Label>{passenger?.name}</Label><Select value={assignments[`${flight.id}:${passengerId}`] ?? ""} onValueChange={(value) => setAssignments((current) => ({ ...current, [`${flight.id}:${passengerId}`]: value ?? "" }))}><SelectTrigger><SelectValue placeholder="选择座位"/></SelectTrigger><SelectContent>{availableSeats(flight.id, passengerId).map((seat) => <SelectItem key={seat.id} value={String(seat.id)}>{seat.seatNo} · {seat.cabinClass} · ¥{seat.price}</SelectItem>)}</SelectContent></Select></div>})}</div></div>)}</section>}
      </div>
      <DialogFooter><Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button><Button onClick={submit} disabled={!canSubmit || loading === "submit"}>{loading === "submit" && <Loader2 className="h-4 w-4 animate-spin"/>}创建联程订单</Button></DialogFooter>
    </DialogContent>
  </Dialog>
}
