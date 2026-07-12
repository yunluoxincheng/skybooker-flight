"use client"

import { useCallback, useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import { Loader2, Plane, Route } from "lucide-react"
import { UserLayout } from "@/components/layout/UserLayout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Textarea } from "@/components/ui/textarea"
import { useAuth } from "@/contexts/AuthContext"
import * as flightApi from "@/services/flightApi"
import * as orderApi from "@/services/orderApi"
import type { ApiError } from "@/lib/request"
import type { ItineraryVO } from "@/types/flight"
import type { OrderVO } from "@/types/order"

export default function ConnectingChangePage() {
  const { id: rawId } = useParams<{ id: string }>(); const id = Number(rawId); const router = useRouter(); const auth = useAuth()
  const [order,setOrder]=useState<OrderVO|null>(null); const [options,setOptions]=useState<ItineraryVO[]>([]); const [error,setError]=useState<string|null>(null)
  const [selected,setSelected]=useState<ItineraryVO|null>(null); const [reason,setReason]=useState(""); const [submitting,setSubmitting]=useState(false)
  const load=useCallback(async()=>{try{const o=await orderApi.getOrderById(id);if(!o.segments?.length){router.replace(`/orders/${id}/change`);return}const list=await orderApi.getConnectingChangeOptions(id);setOrder(o);setOptions(list)}catch(e){setError((e as ApiError).message||"加载整段改签方案失败")}},[id,router])
  useEffect(()=>{if(!auth.isLoading&&!auth.isAuthenticated)router.push(`/login?redirect=/orders/${id}/connecting-change`);else if(auth.isAuthenticated)load()},[auth.isAuthenticated,auth.isLoading,id,load,router])
  const submit=async()=>{if(!order||!selected)return;setSubmitting(true);setError(null);try{const segments=[];for(const flight of selected.segments){const available=(await flightApi.getFlightSeats(flight.id)).filter(s=>s.status==="AVAILABLE");if(available.length<order.passengers.length)throw new Error("替代航段座位不足，请刷新方案");segments.push({flightId:flight.id,items:order.passengers.map((p,i)=>({passengerId:p.passengerId,seatId:available[i].id}))})}await orderApi.changeConnectingOrder(id,{clientRequestId:crypto.randomUUID(),segments,reason});router.push(`/orders/${id}`)}catch(e){setError((e as ApiError).message||"整段改签失败");setSubmitting(false)}}
  if(auth.isLoading||!auth.isAuthenticated||!order)return <UserLayout><div className="mx-auto max-w-5xl px-4 py-10">{error||"正在加载整段改签方案…"}</div></UserLayout>
  return <UserLayout><main className="mx-auto max-w-5xl space-y-6 px-4 py-8"><div><Badge className="mb-2 bg-sky-700">整段行程改签</Badge><h1 className="text-2xl font-bold">选择完整替代行程</h1><p className="text-sm text-muted-foreground">系统会先原子锁定新行程的全部座位，再释放原行程；不支持只改其中一段。</p></div>{error&&<div className="rounded-lg bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}
    <Card><CardContent className="p-5"><h2 className="mb-3 font-semibold">当前行程</h2><div className="space-y-2">{order.segments?.map(s=><p key={s.id} className="text-sm">第 {s.segmentNo} 段 · {s.flightNo} · {s.departureAirportCode} → {s.arrivalAirportCode}</p>)}</div></CardContent></Card>
    <div className="space-y-3"><h2 className="font-semibold">可用完整方案</h2>{options.map(option=><button key={option.segments.map(s=>s.id).join("-")} onClick={()=>setSelected(option)} className={`w-full rounded-xl border p-4 text-left transition ${selected===option?"border-sky-600 bg-sky-50 ring-2 ring-sky-100":"bg-white hover:border-sky-300"}`}><div className="flex items-center justify-between"><span className="flex items-center gap-2 font-medium">{option.journeyType==="CONNECTING"?<Route className="h-4 w-4"/>:<Plane className="h-4 w-4"/>}{option.journeyType==="CONNECTING"?"一次中转":"直飞"}</span><b className="text-rose-600">¥{option.estimatedAmount.toLocaleString()} / 人起</b></div><div className="mt-3 flex flex-wrap gap-2">{option.segments.map(s=><span key={s.id} className="rounded-lg bg-slate-100 px-3 py-2 text-sm">{s.flightNo} · {s.departureAirportCode} → {s.arrivalAirportCode}</span>)}</div></button>)}</div>
    {selected&&<Card><CardContent className="space-y-3 p-5"><label className="text-sm font-medium" htmlFor="change-reason">改签原因</label><Textarea id="change-reason" value={reason} onChange={e=>setReason(e.target.value)} maxLength={255}/><div className="flex justify-end"><Button onClick={submit} disabled={submitting}>{submitting&&<Loader2 className="mr-2 h-4 w-4 animate-spin"/>}确认整段改签</Button></div></CardContent></Card>}
  </main></UserLayout>
}
