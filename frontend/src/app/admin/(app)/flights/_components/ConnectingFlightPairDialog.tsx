"use client"

import { useEffect, useState } from "react"
import { useForm, useWatch, type UseFormReturn } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { ArrowRight, Loader2, PlaneTakeoff, Route } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Combobox } from "@/components/ui/combobox"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import * as adminApi from "@/services/adminApi"
import type { AirlineVO, AirportVO, FlightFormDTO } from "@/types/admin"
import type { ApiError } from "@/lib/request"
import { toConnectingPairPayload, validateConnection } from "./connectingFlightPair"

const selectId = (label: string) => z.number().int().positive(`请选择${label}`)
const segmentSchema = z.object({
  flightNo: z.string().trim().min(1, "请输入航班号"),
  airlineId: selectId("航空公司"),
  departureAirportId: selectId("出发机场"),
  arrivalAirportId: selectId("到达机场"),
  departureTime: z.string().min(1, "请选择出发时间"),
  arrivalTime: z.string().min(1, "请选择到达时间"),
  durationMinutes: z.number().int().positive("请输入飞行时长"),
  basePrice: z.number().positive("请输入票价"),
  totalSeats: z.number().int().positive("请输入座位数"),
  baggageAllowance: z.string().trim().min(1, "请输入行李额"),
})

const pairSchema = z.object({
  firstSegment: segmentSchema,
  secondSegment: segmentSchema,
}).superRefine((value, context) => {
  const first = value.firstSegment
  const second = value.secondSegment
  if (new Date(first.arrivalTime).getTime() <= new Date(first.departureTime).getTime()) {
    context.addIssue({ code: "custom", path: ["firstSegment", "arrivalTime"], message: "到达时间必须晚于起飞时间" })
  }
  if (new Date(second.arrivalTime).getTime() <= new Date(second.departureTime).getTime()) {
    context.addIssue({ code: "custom", path: ["secondSegment", "arrivalTime"], message: "到达时间必须晚于起飞时间" })
  }
  if (first.departureAirportId === first.arrivalAirportId) {
    context.addIssue({ code: "custom", path: ["firstSegment", "arrivalAirportId"], message: "出发与到达机场不能相同" })
  }
  if (second.departureAirportId === second.arrivalAirportId) {
    context.addIssue({ code: "custom", path: ["secondSegment", "arrivalAirportId"], message: "出发与到达机场不能相同" })
  }
  if (first.arrivalAirportId !== second.departureAirportId) {
    context.addIssue({ code: "custom", path: ["secondSegment", "departureAirportId"], message: "必须与第一航段到达机场一致" })
  }
  if (first.departureAirportId === second.arrivalAirportId) {
    context.addIssue({ code: "custom", path: ["secondSegment", "arrivalAirportId"], message: "起终点不能形成环线" })
  }
  if (validateConnection(first as FlightFormDTO, second as FlightFormDTO)?.includes("中转时间")) {
    context.addIssue({ code: "custom", path: ["secondSegment", "departureTime"], message: "中转时间须为 90 分钟至 6 小时" })
  }
})

type PairForm = z.infer<typeof pairSchema>
type SegmentKey = "firstSegment" | "secondSegment"

const emptySegment = () => ({
  flightNo: "",
  airlineId: 0,
  departureAirportId: 0,
  arrivalAirportId: 0,
  departureTime: "",
  arrivalTime: "",
  durationMinutes: 120,
  basePrice: 500,
  totalSeats: 180,
  baggageAllowance: "20kg",
})

function SegmentPanel({
  form,
  segmentKey,
  title,
  accent,
  airlines,
  airports,
}: {
  form: UseFormReturn<PairForm>
  segmentKey: SegmentKey
  title: string
  accent: string
  airlines: AirlineVO[]
  airports: AirportVO[]
}) {
  const { register, setValue, control, formState: { errors } } = form
  const values = useWatch({ control, name: segmentKey })
  const segmentErrors = errors[segmentKey]

  return (
    <section className="overflow-hidden rounded-2xl border bg-white shadow-sm">
      <div className={`flex items-center gap-2 border-b px-5 py-3 ${accent}`}>
        <PlaneTakeoff className="h-4 w-4" />
        <h3 className="font-semibold">{title}</h3>
      </div>
      <div className="grid gap-4 p-5 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label>航班号</Label>
          <Input {...register(`${segmentKey}.flightNo`)} placeholder={segmentKey === "firstSegment" ? "CA1401" : "CA1402"} />
          {segmentErrors?.flightNo && <p className="text-xs text-destructive">{segmentErrors.flightNo.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>航空公司</Label>
          <Combobox
            options={airlines}
            value={values?.airlineId || null}
            onValueChange={(value) => setValue(`${segmentKey}.airlineId`, Number(value), { shouldValidate: true })}
            placeholder="选择航司"
            searchPlaceholder="搜索航司..."
            emptyMessage="暂无可用航司"
            getDisplayValue={(airline) => `${airline.name} (${airline.code})`}
            getSearchFields={(airline) => [airline.name, airline.code]}
          />
          {segmentErrors?.airlineId && <p className="text-xs text-destructive">{segmentErrors.airlineId.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>{segmentKey === "firstSegment" ? "始发机场" : "中转出发机场"}</Label>
          <Combobox
            options={airports}
            value={values?.departureAirportId || null}
            onValueChange={(value) => setValue(`${segmentKey}.departureAirportId`, Number(value), { shouldValidate: true })}
            placeholder="选择出发机场"
            searchPlaceholder="搜索机场..."
            emptyMessage="暂无可用机场"
            getDisplayValue={(airport) => `${airport.name} (${airport.code}) · ${airport.city}`}
            getSearchFields={(airport) => [airport.name, airport.code, airport.city]}
          />
          {segmentErrors?.departureAirportId && <p className="text-xs text-destructive">{segmentErrors.departureAirportId.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>{segmentKey === "firstSegment" ? "中转到达机场" : "最终到达机场"}</Label>
          <Combobox
            options={airports}
            value={values?.arrivalAirportId || null}
            onValueChange={(value) => setValue(`${segmentKey}.arrivalAirportId`, Number(value), { shouldValidate: true })}
            placeholder="选择到达机场"
            searchPlaceholder="搜索机场..."
            emptyMessage="暂无可用机场"
            getDisplayValue={(airport) => `${airport.name} (${airport.code}) · ${airport.city}`}
            getSearchFields={(airport) => [airport.name, airport.code, airport.city]}
          />
          {segmentErrors?.arrivalAirportId && <p className="text-xs text-destructive">{segmentErrors.arrivalAirportId.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>起飞时间</Label>
          <Input type="datetime-local" {...register(`${segmentKey}.departureTime`)} />
          {segmentErrors?.departureTime && <p className="text-xs text-destructive">{segmentErrors.departureTime.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>到达时间</Label>
          <Input type="datetime-local" {...register(`${segmentKey}.arrivalTime`)} />
          {segmentErrors?.arrivalTime && <p className="text-xs text-destructive">{segmentErrors.arrivalTime.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>飞行时长（分钟）</Label>
          <Input type="number" {...register(`${segmentKey}.durationMinutes`, { valueAsNumber: true })} />
          {segmentErrors?.durationMinutes && <p className="text-xs text-destructive">{segmentErrors.durationMinutes.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>基础票价（¥）</Label>
          <Input type="number" {...register(`${segmentKey}.basePrice`, { valueAsNumber: true })} />
          {segmentErrors?.basePrice && <p className="text-xs text-destructive">{segmentErrors.basePrice.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>总座位数</Label>
          <Input type="number" {...register(`${segmentKey}.totalSeats`, { valueAsNumber: true })} />
          {segmentErrors?.totalSeats && <p className="text-xs text-destructive">{segmentErrors.totalSeats.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label>行李额</Label>
          <Input {...register(`${segmentKey}.baggageAllowance`)} placeholder="20kg" />
          {segmentErrors?.baggageAllowance && <p className="text-xs text-destructive">{segmentErrors.baggageAllowance.message}</p>}
        </div>
      </div>
    </section>
  )
}

export function ConnectingFlightPairDialog({
  open,
  onOpenChange,
  airlines,
  airports,
  onCreated,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  airlines: AirlineVO[]
  airports: AirportVO[]
  onCreated: () => void
}) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const form = useForm<PairForm>({
    resolver: zodResolver(pairSchema),
    defaultValues: { firstSegment: emptySegment(), secondSegment: emptySegment() },
  })

  useEffect(() => {
    if (open) {
      form.reset({ firstSegment: emptySegment(), secondSegment: emptySegment() })
      setError(null)
    }
  }, [form, open])

  const submit = async (values: PairForm) => {
    setSubmitting(true)
    setError(null)
    try {
      await adminApi.createConnectingFlights(toConnectingPairPayload(values.firstSegment, values.secondSegment))
      onOpenChange(false)
      onCreated()
    } catch (cause) {
      setError((cause as ApiError).message || "联程航段创建失败")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-6xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-xl">
            <span className="rounded-lg bg-sky-100 p-2 text-sky-700"><Route className="h-5 w-5" /></span>
            快速创建联程航段
          </DialogTitle>
        </DialogHeader>
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          本操作会在同一事务中创建两条独立直飞航班和一个草稿联程方案；中转机场必须一致，中转时间须为 90 分钟至 6 小时。创建后请分别设置两段舱位库存、生成座位并上架，最后在“联程方案视图”上架方案。
        </div>
        {error && <div className="rounded-lg bg-destructive/10 px-4 py-2 text-sm text-destructive">{error}</div>}
        <form onSubmit={form.handleSubmit(submit)} className="space-y-5">
          <div className="grid items-start gap-3 lg:grid-cols-[1fr_auto_1fr]">
            <SegmentPanel form={form} segmentKey="firstSegment" title="第一航段 · 始发至中转" accent="bg-sky-50 text-sky-800" airlines={airlines} airports={airports} />
            <div className="hidden self-center rounded-full border bg-white p-2 text-muted-foreground shadow-sm lg:block"><ArrowRight className="h-5 w-5" /></div>
            <SegmentPanel form={form} segmentKey="secondSegment" title="第二航段 · 中转至目的地" accent="bg-emerald-50 text-emerald-800" airlines={airlines} airports={airports} />
          </div>
          <div className="flex justify-end gap-2 border-t pt-4">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={submitting || airlines.length === 0 || airports.length === 0}>
              {submitting && <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />}
              创建两条航段与草稿方案
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
