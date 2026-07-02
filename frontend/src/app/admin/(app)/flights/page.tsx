"use client"

import { useEffect, useState, useCallback } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Plus, Pencil, Eye, EyeOff, ArmchairIcon, Loader2, ChevronLeft, ChevronRight, Settings2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
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
import { getFallbackCabinPrice } from "@/lib/cabin-utils"
import * as adminApi from "@/services/adminApi"
import { CABIN_CLASS_LABEL, CABIN_CLASS_ORDER, type CabinClass, type FlightVO } from "@/types/flight"
import type { FlightCabinSettingDTO, FlightFormDTO } from "@/types/admin"
import type { ApiError } from "@/lib/request"

const flightSchema = z.object({
  flightNo: z.string().min(1, "请输入航班号"),
  airlineId: z.coerce.number().min(1, "请输入航司ID"),
  departureAirportId: z.coerce.number().min(1, "请输入出发机场ID"),
  arrivalAirportId: z.coerce.number().min(1, "请输入到达机场ID"),
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
      remainingSeats: cabin?.remainingSeats ?? (cabinClass === "ECONOMY" ? flight.remainingSeats : 0),
    }
  })
}

export default function AdminFlightsPage() {
  const [flights, setFlights] = useState<FlightVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)

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
    resolver: zodResolver(flightSchema),
    defaultValues: { directFlag: true, basePrice: 500, totalSeats: 180, baggageAllowance: "20kg" },
  })

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

  useEffect(() => { fetchFlights() }, [fetchFlights])

  const openAdd = () => {
    setEditingFlight(null)
    reset({ directFlag: true, basePrice: 500, totalSeats: 180, baggageAllowance: "20kg" })
    setActionErr(null)
    setDialogOpen(true)
  }

  const openEdit = (f: FlightVO) => {
    setEditingFlight(f)
    setActionErr(null)
    setValue("flightNo", f.flightNo)
    // FlightVO 不包含 airlineId/airportId，编辑时需要手动填入
    setValue("departureTime", f.departureTime.slice(0, 16))
    setValue("arrivalTime", f.arrivalTime.slice(0, 16))
    setValue("durationMinutes", f.durationMinutes)
    setValue("basePrice", f.basePrice)
    setValue("totalSeats", f.totalSeats)
    setValue("baggageAllowance", f.baggageAllowance)
    setValue("directFlag", Boolean(f.directFlag))
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

  const doGenerateSeats = async (id: number) => {
    try {
      await adminApi.generateSeats(id)
      setIsLoading(true)
      fetchFlights()
    } catch (err) {
      setActionErr((err as ApiError).message || "生成座位失败")
    }
  }

  const openCabinDialog = (f: FlightVO) => {
    setCabinFlight(f)
    setCabinForm(buildCabinForm(f))
    setCabinErr(null)
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

    setIsSavingCabins(true)
    setCabinErr(null)
    try {
      await adminApi.updateFlightCabins(cabinFlight.id, {
        cabins: cabinForm.map(({ remainingSeats: _remainingSeats, ...item }) => item),
      })
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
                          <DropdownMenuItem onClick={() => doGenerateSeats(f.id)}>
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
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>航班号</Label>
                <Input {...register("flightNo")} placeholder="CA1234" />
                {errors.flightNo && <p className="text-xs text-destructive">{errors.flightNo.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>航司ID</Label>
                <Input type="number" {...register("airlineId")} placeholder="1" />
                {errors.airlineId && <p className="text-xs text-destructive">{errors.airlineId.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>出发机场ID</Label>
                <Input type="number" {...register("departureAirportId")} placeholder="1" />
                {errors.departureAirportId && <p className="text-xs text-destructive">{errors.departureAirportId.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>到达机场ID</Label>
                <Input type="number" {...register("arrivalAirportId")} placeholder="2" />
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
