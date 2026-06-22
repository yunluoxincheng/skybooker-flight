"use client"

import { useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Plane,
  Plus,
  UserPlus,
  ArmchairIcon,
  CreditCard,
  Loader2,
} from "lucide-react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightStatusBadge } from "@/components/common/FlightStatusBadge"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { SeatMap } from "@/features/booking/components/SeatMap"
import { useBooking } from "@/features/booking/hooks/useBooking"
import { useAuth } from "@/contexts/AuthContext"
import * as passengerApi from "@/services/passengerApi"
import type { ApiError } from "@/lib/request"

const STEPS = [
  { label: "确认航班", icon: Plane },
  { label: "选择乘机人", icon: UserPlus },
  { label: "选择座位", icon: ArmchairIcon },
  { label: "确认支付", icon: CreditCard },
]

const passengerSchema = z.object({
  name: z.string().min(1, "请输入姓名"),
  idCardNo: z.string().min(15, "请输入有效身份证号").max(18),
  passengerType: z.enum(["ADULT", "CHILD", "INFANT"]),
  phone: z.string().min(11, "请输入有效手机号").max(11),
})

type PassengerFormData = z.infer<typeof passengerSchema>

export default function BookingPage() {
  const params = useParams()
  const router = useRouter()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const flightId = Number(params.flightId)
  const booking = useBooking(flightId)
  const [addDialogOpen, setAddDialogOpen] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting: isAdding },
  } = useForm<PassengerFormData>({
    resolver: zodResolver(passengerSchema),
    defaultValues: { passengerType: "ADULT" },
  })

  // 初始加载
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      router.push(`/login?redirect=/booking/${flightId}`)
      return
    }
    if (isAuthenticated) {
      booking.loadFlight()
    }
  }, [isAuthenticated, isAuthLoading, flightId])

  // 进入乘机人步骤时加载
  useEffect(() => {
    if (booking.step === 1 && booking.myPassengers.length === 0) {
      booking.loadPassengers()
    }
  }, [booking.step])

  const handleAddPassenger = async (data: PassengerFormData) => {
    setAddError(null)
    try {
      await passengerApi.createPassenger(data)
      setAddDialogOpen(false)
      reset()
      booking.loadPassengers()
    } catch (err) {
      setAddError((err as ApiError).message || "添加失败")
    }
  }

  const formatTime = (iso: string) => {
    if (!iso) return ""
    const d = new Date(iso)
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`
  }

  const formatDuration = (minutes: number) => {
    const h = Math.floor(minutes / 60)
    const m = minutes % 60
    return `${h}时${m > 0 ? `${m}分` : ""}`
  }

  const selectedSeats = booking.seats.filter((s) => booking.selectedSeatIds.includes(s.id))
  const selectedPassengers = booking.myPassengers.filter((p) =>
    booking.selectedPassengerIds.includes(p.id)
  )
  const totalPrice = selectedSeats.reduce((sum, s) => sum + s.price, 0)

  // Auth loading
  if (isAuthLoading || (isAuthenticated && booking.isLoadingFlight)) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 space-y-6">
          <Skeleton className="h-8 w-64" />
          <Skeleton className="h-48 w-full rounded-xl" />
          <Skeleton className="h-32 w-full rounded-xl" />
        </div>
      </UserLayout>
    )
  }

  if (!isAuthenticated) return null
  if (!booking.flight) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 text-center">
          <p className="text-destructive mb-4">{booking.error || "航班不存在"}</p>
          <Button variant="outline" onClick={() => router.back()}>返回</Button>
        </div>
      </UserLayout>
    )
  }

  const flight = booking.flight

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        {/* 步骤条 */}
        <div className="flex items-center justify-center mb-8">
          {STEPS.map((s, i) => (
            <div key={s.label} className="flex items-center">
              <div className="flex flex-col items-center">
                <div
                  className={`flex items-center justify-center h-10 w-10 rounded-full border-2 transition-colors ${
                    i <= booking.step
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-slate-200 text-muted-foreground"
                  }`}
                >
                  <s.icon className="h-4 w-4" />
                </div>
                <span className="text-xs mt-1.5 text-muted-foreground">{s.label}</span>
              </div>
              {i < STEPS.length - 1 && (
                <div
                  className={`w-12 sm:w-20 h-0.5 mx-2 transition-colors ${
                    i < booking.step ? "bg-primary" : "bg-slate-200"
                  }`}
                />
              )}
            </div>
          ))}
        </div>

        {/* Error */}
        {booking.error && (
          <div className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive mb-6">
            {booking.error}
          </div>
        )}

        {/* Step 0: 确认航班 */}
        {booking.step === 0 && (
          <Card>
            <CardContent className="p-6">
              <h2 className="font-semibold mb-4">确认航班信息</h2>
              <div className="flex flex-wrap items-center gap-3 mb-4">
                <Plane className="h-5 w-5 text-primary" />
                <span className="font-bold">{flight.airlineName}</span>
                <Badge variant="outline">{flight.flightNo}</Badge>
                <FlightStatusBadge status={flight.status} />
              </div>
              <div className="flex items-center gap-8">
                <div className="text-center">
                  <p className="text-2xl font-bold">{formatTime(flight.departureTime)}</p>
                  <p className="font-medium">{flight.departureCity}</p>
                </div>
                <div className="flex-1 text-center text-sm text-muted-foreground">
                  <p>{formatDuration(flight.durationMinutes)}</p>
                  <p>{flight.directFlag ? "直飞" : "经停"}</p>
                </div>
                <div className="text-center">
                  <p className="text-2xl font-bold">{formatTime(flight.arrivalTime)}</p>
                  <p className="font-medium">{flight.arrivalCity}</p>
                </div>
              </div>
              <div className="flex justify-end mt-6">
                <Button onClick={booking.nextStep}>
                  下一步 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Step 1: 选择乘机人 */}
        {booking.step === 1 && (
          <Card>
            <CardContent className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="font-semibold">选择乘机人</h2>
                <Dialog open={addDialogOpen} onOpenChange={setAddDialogOpen}>
                  <DialogTrigger asChild>
                    <Button variant="outline" size="sm" className="gap-1">
                      <Plus className="h-3.5 w-3.5" /> 新增乘机人
                    </Button>
                  </DialogTrigger>
                  <DialogContent>
                    <DialogHeader>
                      <DialogTitle>新增乘机人</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleSubmit(handleAddPassenger)} className="space-y-4 mt-2">
                      {addError && (
                        <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">
                          {addError}
                        </div>
                      )}
                      <div className="space-y-2">
                        <Label htmlFor="pName">姓名</Label>
                        <Input id="pName" placeholder="请输入姓名" {...register("name")} />
                        {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="pIdCard">身份证号</Label>
                        <Input id="pIdCard" placeholder="请输入身份证号" {...register("idCardNo")} />
                        {errors.idCardNo && <p className="text-xs text-destructive">{errors.idCardNo.message}</p>}
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="pType">乘机人类型</Label>
                        <Select
                          defaultValue="ADULT"
                          onValueChange={(v) => setValue("passengerType", v as "ADULT" | "CHILD" | "INFANT")}
                        >
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="ADULT">成人</SelectItem>
                            <SelectItem value="CHILD">儿童</SelectItem>
                            <SelectItem value="INFANT">婴儿</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="pPhone">手机号</Label>
                        <Input id="pPhone" placeholder="请输入手机号" {...register("phone")} />
                        {errors.phone && <p className="text-xs text-destructive">{errors.phone.message}</p>}
                      </div>
                      <Button type="submit" className="w-full" disabled={isAdding}>
                        {isAdding ? "添加中..." : "确认添加"}
                      </Button>
                    </form>
                  </DialogContent>
                </Dialog>
              </div>

              {booking.isLoadingPassengers ? (
                <Skeleton className="h-32 w-full rounded-xl" />
              ) : booking.myPassengers.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-8">
                  暂无常用乘机人，请点击"新增乘机人"添加
                </p>
              ) : (
                <div className="space-y-2">
                  {booking.myPassengers.map((p) => (
                    <label
                      key={p.id}
                      className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                        booking.selectedPassengerIds.includes(p.id)
                          ? "border-primary bg-primary/5"
                          : "border-slate-200 hover:border-slate-300"
                      }`}
                    >
                      <Checkbox
                        checked={booking.selectedPassengerIds.includes(p.id)}
                        onCheckedChange={() => booking.togglePassenger(p.id)}
                      />
                      <div className="flex-1">
                        <p className="font-medium text-sm">{p.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {p.idCardNo} · {p.passengerType === "ADULT" ? "成人" : p.passengerType === "CHILD" ? "儿童" : "婴儿"}
                        </p>
                      </div>
                    </label>
                  ))}
                </div>
              )}

              <div className="flex justify-between mt-6">
                <Button variant="ghost" onClick={booking.prevStep}>
                  <ChevronLeft className="h-4 w-4" /> 上一步
                </Button>
                <Button
                  onClick={booking.nextStep}
                  disabled={booking.selectedPassengerIds.length === 0}
                >
                  下一步 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Step 2: 选择座位 */}
        {booking.step === 2 && (
          <Card>
            <CardContent className="p-6">
              <h2 className="font-semibold mb-1">选择座位</h2>
              <p className="text-sm text-muted-foreground mb-4">
                已选 {selectedPassengers.length} 位乘机人，请选择 {selectedPassengers.length} 个座位
                （当前已选 {booking.selectedSeatIds.length} 个）
              </p>

              <SeatMap
                seats={booking.seats}
                selectedSeatIds={booking.selectedSeatIds}
                maxSelect={selectedPassengers.length || 1}
                onToggleSeat={booking.toggleSeat}
              />

              <div className="flex justify-between mt-6">
                <Button variant="ghost" onClick={booking.prevStep}>
                  <ChevronLeft className="h-4 w-4" /> 上一步
                </Button>
                <Button
                  onClick={booking.nextStep}
                  disabled={booking.selectedSeatIds.length !== (selectedPassengers.length || 1)}
                >
                  下一步 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Step 3: 确认 & 支付 */}
        {booking.step === 3 && (
          <div className="space-y-6">
            <Card>
              <CardContent className="p-6">
                <h2 className="font-semibold mb-4">订单确认</h2>

                {/* 航班摘要 */}
                <div className="rounded-lg bg-slate-50 p-4 mb-4">
                  <div className="flex items-center gap-2 mb-2">
                    <Plane className="h-4 w-4 text-primary" />
                    <span className="font-medium">{flight.airlineName} {flight.flightNo}</span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {flight.departureCity}({formatTime(flight.departureTime)}) →{" "}
                    {flight.arrivalCity}({formatTime(flight.arrivalTime)})
                    {" · "}{formatDuration(flight.durationMinutes)}
                  </p>
                </div>

                {/* 乘机人 */}
                <h3 className="text-sm font-medium mb-2">乘机人</h3>
                <div className="space-y-1 mb-4">
                  {selectedPassengers.map((p, i) => (
                    <div key={p.id} className="flex items-center justify-between text-sm">
                      <span>{p.name}</span>
                      <span className="text-muted-foreground">
                        座位 {selectedSeats[i]?.seatNo || "-"}
                      </span>
                    </div>
                  ))}
                </div>

                <Separator className="my-4" />

                {/* 价格明细 */}
                <h3 className="text-sm font-medium mb-2">价格明细</h3>
                <div className="space-y-2 text-sm">
                  {selectedSeats.map((s, i) => (
                    <div key={i} className="flex justify-between">
                      <span className="text-muted-foreground">
                        机票 ({selectedPassengers[i]?.name} · {s.seatNo})
                      </span>
                      <span>¥{s.price.toLocaleString()}</span>
                    </div>
                  ))}
                  <Separator />
                  <div className="flex justify-between font-bold text-base">
                    <span>合计</span>
                    <FlightPriceTag price={totalPrice} className="text-base" />
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* 支付操作 */}
            <div className="flex justify-between">
              <Button variant="ghost" onClick={booking.prevStep}>
                <ChevronLeft className="h-4 w-4" /> 上一步
              </Button>
              <div className="space-x-3">
                <Button
                  variant="outline"
                  onClick={booking.submitOrder}
                  disabled={booking.isSubmitting}
                >
                  {booking.isSubmitting && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
                  提交订单
                </Button>
                {booking.createdOrder && (
                  <Button onClick={booking.payOrder} disabled={booking.isSubmitting}>
                    {booking.isSubmitting && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
                    <Check className="h-4 w-4 mr-1" /> 模拟支付
                  </Button>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </UserLayout>
  )
}
