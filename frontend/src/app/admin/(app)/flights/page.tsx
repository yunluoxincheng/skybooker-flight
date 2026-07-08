"use client"

import { useEffect, useState, useCallback, useMemo } from "react"
import { useForm, type Resolver } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Plus, Pencil, Eye, EyeOff, ArmchairIcon, Loader2, ChevronLeft, ChevronRight, Settings2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { FlightStatusBadge } from "@/components/common/FlightStatusBadge"
import { getCabinAvailableSeats, getFallbackCabinPrice } from "@/lib/cabin-utils"
import * as adminApi from "@/services/adminApi"
import { CABIN_CLASS_LABEL, CABIN_CLASS_ORDER, type CabinClass, type FlightVO } from "@/types/flight"
import type { AirlineVO, AirportVO, FlightCabinSettingDTO, FlightFormDTO } from "@/types/admin"
import type { ApiError } from "@/lib/request"

const selectNumberField = (label: string) =>
  z.preprocess(
    (value) => {
      if (value === "" || value === undefined || value === null) return undefined
      if (typeof value === "number") return Number.isNaN(value) ? undefined : value
      const numericValue = Number(value)
      return Number.isNaN(numericValue) ? undefined : numericValue
    },
    z.number({
      message: `请选择${label}`,
    }).min(1, `请选择${label}`)
  )

const flightSchema = z.object({
  flightNo: z.string().min(1, "请输入航班号"),
  airlineId: selectNumberField("航空公司"),
  departureAirportId: selectNumberField("出发机场"),
  arrivalAirportId: selectNumberField("到达机场"),
  departureTime: z.string().min(1, "请选择出发时间"),
  arrivalTime: z.string().min(1, "请选择到达时间"),
  durationMinutes: z.coerce.number().min(1, "请输入飞行时长（分钟）"),
  basePrice: z.coerce.number().min(1, "请输入票价"),
  totalSeats: z.coerce.number().min(1, "请输入座位数"),
  baggageAllowance: z.string().min(1, "请输入行李额"),
  directFlag: z.boolean(),
})

interface CabinFormItem extends FlightCabinSettingDTO {
  remainingSeats: number
}

function buildCabinForm(flight: FlightVO): CabinFormItem[] {
  const configuredCabins = new Map((flight.cabins ?? []).map((cabin) => [cabin.cabinClass, cabin]))

  return CABIN_CLASS_ORDER.map((cabinClass) => {
    const cabin = configuredCabins.get(cabinClass)

    return {
      cabinClass,
      price: cabin?.price ?? getFallbackCabinPrice(flight.basePrice, cabinClass),
      totalSeats: cabin?.totalSeats ?? (cabinClass === "ECONOMY" ? flight.totalSeats : 0),
      remainingSeats: cabin ? getCabinAvailableSeats(cabin) : (cabinClass === "ECONOMY" ? flight.remainingSeats : 0),
    }
  })
}

function hasValidCabinConfig(flight: FlightVO) {
  const configuredCabins = (flight.cabins ?? []).filter((cabin) => cabin.totalSeats > 0)
  const totalConfiguredSeats = configuredCabins.reduce((sum, cabin) => sum + cabin.totalSeats, 0)
  return configuredCabins.length > 0 && totalConfiguredSeats === flight.totalSeats
}

export default function AdminFlightsPage() {
  const [flights, setFlights] = useState<FlightVO[]>([])
  const [airlines, setAirlines] = useState<AirlineVO[]>([])
  const [airports, setAirports] = useState<AirportVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)
  const [refsErr, setRefsErr] = useState<string | null>(null)
  const [selectKey, setSelectKey] = useState(0)

  // 新增/编辑 Dialog
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingFlight, setEditingFlight] = useState<FlightVO | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // 舱位库存 Dialog
  const [cabinDialogOpen, setCabinDialogOpen] = useState(false)
  const [cabinFlight, setCabinFlight] = useState<FlightVO | null>(null)
  const [cabinForm, setCabinForm] = useState<CabinFormItem[]>([])
  const [cabinErr, setCabinErr] = useState<string | null>(null)
  const [isSavingCabins, setIsSavingCabins] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<z.infer<typeof flightSchema>>({
    resolver: zodResolver(flightSchema) as Resolver<z.infer<typeof flightSchema>>,
    defaultValues: { directFlag: true, basePrice: 500, totalSeats: 180, baggageAllowance: "20kg" },
  })

  useEffect(() => {
    register("airlineId")
    register("departureAirportId")
    register("arrivalAirportId")
  }, [register])

  const fetchFlights = useCallback(async () => {
    try {
      const data = await adminApi.getFlights({ page, size: 10 })
      setFlights(data.records)
      setTotal(data.total)
      setError(null)
    } catch (err) {
      setError((err as ApiError).message || "加载航班失败")
    } finally {
      setIsLoading(false)
    }
  }, [page])

  const fetchRefs = useCallback(async () => {
    try {
      const [airlineData, airportData] = await Promise.all([
        adminApi.getAirlines({ page: 1, size: 100 }),
        adminApi.getAirports({ page: 1, size: 100 }),
      ])
      setAirlines(airlineData.records)
      setAirports(airportData.records)
      setRefsErr(null)
    } catch (err) {
      setAirlines([])
      setAirports([])
      setRefsErr((err as ApiError).message || "加载航司和机场数据失败")
    }
  }, [])

  useEffect(() => { fetchFlights() }, [fetchFlights])
  useEffect(() => { fetchRefs() }, [fetchRefs])

  const selectableAirlines = useMemo(
    () => airlines.filter((airline) => airline.status === "ENABLED" || airline.id === editingFlight?.airlineId),
    [airlines, editingFlight]
  )

  const selectableDepartureAirports = useMemo(
    () => airports.filter((airport) => airport.status === "ENABLED" || airport.id === editingFlight?.departureAirportId),
    [airports, editingFlight]
  )

  const selectableArrivalAirports = useMemo(
    () => airports.filter((airport) => airport.status === "ENABLED" || airport.id === editingFlight?.arrivalAirportId),
    [airports, editingFlight]
  )

  const openAdd = () => {
    setEditingFlight(null)
    reset({ directFlag: true, basePrice: 500, totalSeats: 180, baggageAllowance: "20kg" })
    setActionErr(null)
    setSelectKey((current) => current + 1)
    setDialogOpen(true)
  }

  const openEdit = (f: FlightVO) => {
    setEditingFlight(f)
    setActionErr(null)
    setValue("flightNo", f.flightNo)
    setValue("airlineId", f.airlineId)
    setValue("departureAirportId", f.departureAirportId)
    setValue("arrivalAirportId", f.arrivalAirportId)
    setValue("departureTime", f.departureTime.slice(0, 16))
    setValue("arrivalTime", f.arrivalTime.slice(0, 16))
    setValue("durationMinutes", f.durationMinutes)
    setValue("basePrice", f.basePrice)
    setValue("totalSeats", f.totalSeats)
    setValue("baggageAllowance", f.baggageAllowance)
    setValue("directFlag", Boolean(f.directFlag))
    setSelectKey((current) => current + 1)
    setDialogOpen(true)
  }

  const onSubmit = async (data: z.infer<typeof flightSchema>) => {
    setIsSubmitting(true)
    setActionErr(null)
    try {
      const normalizeLocalDateTime = (value: string) =>
        value.length === 16 ? `${value}:00` : value

      const dto: FlightFormDTO = {
        ...data,
        departureTime: normalizeLocalDateTime(data.departureTime),
        arrivalTime: normalizeLocalDateTime(data.arrivalTime),
      }
      if (editingFlight) {
        await adminApi.updateFlight(editingFlight.id, dto)
      } else {
        await adminApi.createFlight(dto)
      }
      setDialogOpen(false)
      setIsLoading(true)
      fetchFlights()
    } catch (err) {
      setActionErr((err as ApiError).message || "操作失败")
    } finally {
      setIsSubmitting(false)
    }
  }

  const togglePublish = async (f: FlightVO) => {
    try {
      if (f.publishStatus === "PUBLISHED") {
        await adminApi.unpublishFlight(f.id)
      } else {
        await adminApi.publishFlight(f.id)
      }
      setIsLoading(true)
      fetchFlights()
    } catch (err) {
      setActionErr((err as ApiError).message || "操作失败")
    }
  }

  const doGenerateSeats = async (flight: FlightVO) => {
    if (!hasValidCabinConfig(flight)) {
      setActionErr("请先完成舱位库存设置，再生成座位")
      openCabinDialog(flight, "请先配置各舱位库存与票价，保存后再生成座位")
      return
    }

    try {
      setActionErr(null)
      await adminApi.generateSeats(flight.id)
      setIsLoading(true)
      fetchFlights()
    } catch (err) {
      setActionErr((err as ApiError).message || "生成座位失败")
    }
  }

  const openCabinDialog = (f: FlightVO, message?: string) => {
    setCabinFlight(f)
    setCabinForm(buildCabinForm(f))
    setCabinErr(message ?? null)
    setCabinDialogOpen(true)
  }

  const updateCabinField = (
    cabinClass: CabinClass,
    field: "price" | "totalSeats",
    value: number
  ) => {
    setCabinForm((current) =>
      current.map((item) =>
        item.cabinClass === cabinClass
          ? {
              ...item,
              [field]:
                field === "totalSeats"
                  ? Math.max(item.remainingSeats, Number.isFinite(value) ? value : item.remainingSeats)
                  : Math.max(0, Number.isFinite(value) ? value : 0),
            }
          : item
      )
    )
  }

  const submitCabins = async () => {
    if (!cabinFlight) return

    const invalidCabin = cabinForm.find((item) => item.totalSeats < item.remainingSeats)
    if (invalidCabin) {
      setCabinErr(`${CABIN_CLASS_LABEL[invalidCabin.cabinClass]}库存不能小于当前剩余座位数`)
      return
    }

    const configuredCabins = cabinForm
      .filter((item) => item.totalSeats > 0)
      .map(({ remainingSeats: _remainingSeats, ...item }) => item)

    const totalConfiguredSeats = configuredCabins.reduce((sum, item) => sum + item.totalSeats, 0)
    if (totalConfiguredSeats !== cabinFlight.totalSeats) {
      setCabinErr(
        `舱位总座位数必须等于航班总座位数（当前航班 ${cabinFlight.totalSeats}，当前配置 ${totalConfiguredSeats}）`
      )
      return
    }

    setIsSavingCabins(true)
    setCabinErr(null)
    try {
      await adminApi.updateFlightCabins(cabinFlight.id, configuredCabins)
      setCabinDialogOpen(false)
      fetchFlights()
    } catch (err) {
      setCabinErr((err as ApiError).message || "舱位库存设置失败")
    } finally {
      setIsSavingCabins(false)
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 10))

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">航班管理</h1>
        <Button size="sm" onClick={openAdd} className="gap-1">
          <Plus className="h-4 w-4" /> 新增航班
        </Button>
      </div>

      {actionErr && (
        <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{actionErr}</div>
      )}

      {/* Table */}
      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center text-destructive">
          {error}
        </div>
      ) : (
        <div className="rounded-xl border bg-white overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>航班号</TableHead>
                <TableHead>航司</TableHead>
                <TableHead>出发 → 到达</TableHead>
                <TableHead>出发时间</TableHead>
                <TableHead>票价</TableHead>
                <TableHead>座位</TableHead>
                <TableHead>状态</TableHead>
                <TableHead className="w-[120px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {flights.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center text-muted-foreground py-8">
                    暂无航班数据
                  </TableCell>
                </TableRow>
              ) : (
                flights.map((f) => (
                  <TableRow key={f.id}>
                    <TableCell className="font-medium">{f.flightNo}</TableCell>
                    <TableCell>{f.airlineName}</TableCell>
                    <TableCell>{f.departureCity} → {f.arrivalCity}</TableCell>
                    <TableCell className="text-sm">
                      {new Date(f.departureTime).toLocaleString("zh-CN")}
                    </TableCell>
                    <TableCell>¥{f.basePrice}</TableCell>
                    <TableCell>{f.remainingSeats}/{f.totalSeats}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1.5">
                        <FlightStatusBadge status={f.status} />
                        {f.publishStatus === "PUBLISHED" ? (
                          <Badge variant="secondary" className="text-xs">已上架</Badge>
                        ) : (
                          <Badge variant="outline" className="text-xs">未上架</Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger render={<Button variant="ghost" size="sm">操作</Button>} />
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => openEdit(f)}>
                            <Pencil className="h-3.5 w-3.5 mr-2" /> 编辑
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => openCabinDialog(f)}>
                            <Settings2 className="h-3.5 w-3.5 mr-2" /> 舱位库存设置
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => togglePublish(f)}>
                            {f.publishStatus === "PUBLISHED" ? (
                              <><EyeOff className="h-3.5 w-3.5 mr-2" /> 下架</>
                            ) : (
                              <><Eye className="h-3.5 w-3.5 mr-2" /> 上架</>
                            )}
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => doGenerateSeats(f)}>
                            <ArmchairIcon className="h-3.5 w-3.5 mr-2" /> 生成座位
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {/* 分页 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => { setIsLoading(true); setPage(page - 1) }}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground">{page} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => { setIsLoading(true); setPage(page + 1) }}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* 新增/编辑 Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editingFlight ? "编辑航班" : "新增航班"}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 mt-2">
            {actionErr && (
              <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{actionErr}</div>
            )}
            {refsErr && (
              <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
                {refsErr}
              </div>
            )}
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>航班号</Label>
                <Input {...register("flightNo")} placeholder="CA1234" />
                {errors.flightNo && <p className="text-xs text-destructive">{errors.flightNo.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>航空公司</Label>
                {selectableAirlines.length > 0 ? (
                  <Select
                    key={`airline-${selectKey}`}
                    defaultValue={editingFlight ? String(editingFlight.airlineId) : undefined}
                    onValueChange={(value) => {
                      if (!value) return
                      setValue("airlineId", Number(value), { shouldDirty: true, shouldValidate: true })
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {(value) => {
                          const airline = selectableAirlines.find((item) => String(item.id) === value)
                          return airline ? `${airline.name} (${airline.code})` : "请选择航空公司"
                        }}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {selectableAirlines.map((airline) => (
                        <SelectItem key={airline.id} value={String(airline.id)}>
                          {airline.name} ({airline.code})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <div className="flex h-8 items-center rounded-lg border border-dashed bg-muted/40 px-3 text-sm text-muted-foreground">
                    暂无可用航司，请先在航司管理中启用数据
                  </div>
                )}
                {errors.airlineId && <p className="text-xs text-destructive">{errors.airlineId.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>出发机场</Label>
                {selectableDepartureAirports.length > 0 ? (
                  <Select
                    key={`departure-airport-${selectKey}`}
                    defaultValue={editingFlight ? String(editingFlight.departureAirportId) : undefined}
                    onValueChange={(value) => {
                      if (!value) return
                      setValue("departureAirportId", Number(value), { shouldDirty: true, shouldValidate: true })
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {(value) => {
                          const airport = selectableDepartureAirports.find((item) => String(item.id) === value)
                          return airport ? `${airport.name} (${airport.code} - ${airport.city})` : "请选择出发机场"
                        }}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {selectableDepartureAirports.map((airport) => (
                        <SelectItem key={airport.id} value={String(airport.id)}>
                          {airport.name} ({airport.code} - {airport.city})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <div className="flex h-8 items-center rounded-lg border border-dashed bg-muted/40 px-3 text-sm text-muted-foreground">
                    暂无可用机场，请先在机场管理中启用数据
                  </div>
                )}
                {errors.departureAirportId && <p className="text-xs text-destructive">{errors.departureAirportId.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>到达机场</Label>
                {selectableArrivalAirports.length > 0 ? (
                  <Select
                    key={`arrival-airport-${selectKey}`}
                    defaultValue={editingFlight ? String(editingFlight.arrivalAirportId) : undefined}
                    onValueChange={(value) => {
                      if (!value) return
                      setValue("arrivalAirportId", Number(value), { shouldDirty: true, shouldValidate: true })
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {(value) => {
                          const airport = selectableArrivalAirports.find((item) => String(item.id) === value)
                          return airport ? `${airport.name} (${airport.code} - ${airport.city})` : "请选择到达机场"
                        }}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      {selectableArrivalAirports.map((airport) => (
                        <SelectItem key={airport.id} value={String(airport.id)}>
                          {airport.name} ({airport.code} - {airport.city})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (
                  <div className="flex h-8 items-center rounded-lg border border-dashed bg-muted/40 px-3 text-sm text-muted-foreground">
                    暂无可用机场，请先在机场管理中启用数据
                  </div>
                )}
                {errors.arrivalAirportId && <p className="text-xs text-destructive">{errors.arrivalAirportId.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>飞行时长（分钟）</Label>
                <Input type="number" {...register("durationMinutes")} placeholder="180" />
                {errors.durationMinutes && <p className="text-xs text-destructive">{errors.durationMinutes.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>行李额</Label>
                <Input {...register("baggageAllowance")} placeholder="20kg" />
                {errors.baggageAllowance && <p className="text-xs text-destructive">{errors.baggageAllowance.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>出发时间</Label>
                <Input type="datetime-local" {...register("departureTime")} />
                {errors.departureTime && <p className="text-xs text-destructive">{errors.departureTime.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>到达时间</Label>
                <Input type="datetime-local" {...register("arrivalTime")} />
                {errors.arrivalTime && <p className="text-xs text-destructive">{errors.arrivalTime.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>票价 (¥)</Label>
                <Input type="number" {...register("basePrice")} />
                {errors.basePrice && <p className="text-xs text-destructive">{errors.basePrice.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>总座位数</Label>
                <Input type="number" {...register("totalSeats")} />
                {errors.totalSeats && <p className="text-xs text-destructive">{errors.totalSeats.message}</p>}
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Checkbox id="directFlag" {...register("directFlag")} />
              <Label htmlFor="directFlag" className="cursor-pointer text-sm">直飞航班</Label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)}>取消</Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
                {editingFlight ? "保存" : "创建"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 舱位库存 Dialog */}
      <Dialog open={cabinDialogOpen} onOpenChange={setCabinDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              舱位库存设置{cabinFlight ? ` · ${cabinFlight.flightNo}` : ""}
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4 mt-2">
            {cabinErr && (
              <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
                {cabinErr}
              </div>
            )}

            <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-muted-foreground">
              可编辑各舱位票价与库存，总库存余量以服务端校验和返回结果为准。
            </div>

            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>舱位</TableHead>
                    <TableHead>票价 (¥)</TableHead>
                    <TableHead>库存</TableHead>
                    <TableHead>当前剩余</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {cabinForm.map((item) => (
                    <TableRow key={item.cabinClass}>
                      <TableCell className="font-medium">
                        {CABIN_CLASS_LABEL[item.cabinClass]}
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={0}
                          value={item.price}
                          onChange={(event) =>
                            updateCabinField(item.cabinClass, "price", Number(event.target.value))
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Input
                          type="number"
                          min={0}
                          value={item.totalSeats}
                          onChange={(event) =>
                            updateCabinField(item.cabinClass, "totalSeats", Number(event.target.value))
                          }
                        />
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {item.remainingSeats}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={() => setCabinDialogOpen(false)}>
                取消
              </Button>
              <Button type="button" onClick={submitCabins} disabled={isSavingCabins}>
                {isSavingCabins && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
                保存舱位配置
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
