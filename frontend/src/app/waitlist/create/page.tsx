"use client"

import { Suspense, useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Plane,
  Plus,
  UserPlus,
  CreditCard,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightPriceTag } from "@/components/common/FlightPriceTag"
import { FlightStatusBadge } from "@/components/common/FlightStatusBadge"
import { useAuth } from "@/contexts/AuthContext"
import { getCabinAvailableSeats, getFallbackCabinPrice } from "@/lib/cabin-utils"
import type { ApiError } from "@/lib/request"
import * as flightApi from "@/services/flightApi"
import * as passengerApi from "@/services/passengerApi"
import * as waitlistApi from "@/services/waitlistApi"
import type { PassengerVO } from "@/types/passenger"
import type { FlightCabinVO, FlightVO, CabinClass } from "@/types/flight"
import { CABIN_CLASS_LABEL, CABIN_CLASS_ORDER } from "@/types/flight"

const STEPS = [
  { label: "确认航班", icon: Plane },
  { label: "选择乘机人", icon: UserPlus },
  { label: "确认提交", icon: CreditCard },
]

const AIRPORT_FEE_PER_PASSENGER = 50
const FUEL_FEE_PER_PASSENGER = 30

const passengerSchema = z.object({
  name: z.string().min(1, "请输入姓名"),
  idCardNo: z.string().min(15, "请输入有效身份证号").max(18),
  passengerType: z.enum(["ADULT", "CHILD", "INFANT"]),
  phone: z.string().min(11, "请输入有效手机号").max(11),
})

type PassengerFormData = z.infer<typeof passengerSchema>

function isCabinClass(value: string | null): value is CabinClass {
  return value === "ECONOMY" || value === "BUSINESS" || value === "FIRST"
}

function WaitlistCreateContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()

  const flightId = Number(searchParams.get("flightId") || "0")
  const cabinClassParam = searchParams.get("cabinClass")

  const [step, setStep] = useState(0)
  const [flight, setFlight] = useState<FlightVO | null>(null)
  const [myPassengers, setMyPassengers] = useState<PassengerVO[]>([])
  const [selectedPassengerIds, setSelectedPassengerIds] = useState<number[]>([])
  const [selectedCabinClass, setSelectedCabinClass] = useState<CabinClass | null>(
    isCabinClass(cabinClassParam) ? cabinClassParam : null
  )
  const [isLoadingFlight, setIsLoadingFlight] = useState(true)
  const [isLoadingPassengers, setIsLoadingPassengers] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
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

  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      const redirect =
        searchParams.toString().length > 0
          ? `/waitlist/create?${searchParams.toString()}`
          : "/waitlist/create"
      router.push(`/login?redirect=${encodeURIComponent(redirect)}`)
      return
    }

    if (!flightId) {
      setError("缺少航班信息，无法创建候补")
      setIsLoadingFlight(false)
      return
    }

    if (isAuthenticated) {
      setIsLoadingFlight(true)
      setError(null)

      flightApi
        .getFlightById(flightId)
        .then((data) => setFlight(data))
        .catch((err: ApiError) => {
          setError(err.message || "加载航班信息失败")
        })
        .finally(() => setIsLoadingFlight(false))
    }
  }, [flightId, isAuthLoading, isAuthenticated, router, searchParams])

  useEffect(() => {
    if (step !== 1 || myPassengers.length > 0 || !isAuthenticated) return

    setIsLoadingPassengers(true)
    passengerApi
      .getMyPassengers()
      .then((data) => setMyPassengers(data))
      .catch((err: ApiError) => {
        setError(err.message || "加载乘机人失败")
      })
      .finally(() => setIsLoadingPassengers(false))
  }, [isAuthenticated, myPassengers.length, step])

  const cabins = useMemo<FlightCabinVO[]>(() => {
    if (!flight) return []

    if (flight.cabins && flight.cabins.length > 0) {
      return [...flight.cabins].sort(
        (a, b) => CABIN_CLASS_ORDER.indexOf(a.cabinClass) - CABIN_CLASS_ORDER.indexOf(b.cabinClass)
      )
    }

    return CABIN_CLASS_ORDER.map((cabinClass) => ({
      cabinClass,
      price: getFallbackCabinPrice(flight.basePrice, cabinClass),
      totalSeats: flight.totalSeats,
      availableSeats: cabinClass === "ECONOMY" ? flight.remainingSeats : 0,
    }))
  }, [flight])

  useEffect(() => {
    if (selectedCabinClass || cabins.length === 0) return
    setSelectedCabinClass(cabins[0].cabinClass)
  }, [cabins, selectedCabinClass])

  const selectedCabin =
    cabins.find((cabin) => cabin.cabinClass === selectedCabinClass) || null
  const selectedPassengers = myPassengers.filter((passenger) =>
    selectedPassengerIds.includes(passenger.id)
  )
  const ticketAmount = (selectedCabin?.price || 0) * selectedPassengers.length
  const airportFee = AIRPORT_FEE_PER_PASSENGER * selectedPassengers.length
  const fuelFee = FUEL_FEE_PER_PASSENGER * selectedPassengers.length
  const totalAmount = ticketAmount + airportFee + fuelFee
  const isWaitlistNeeded =
    selectedCabin && selectedPassengers.length > 0
      ? getCabinAvailableSeats(selectedCabin) < selectedPassengers.length
      : true

  const togglePassenger = (id: number) => {
    setSelectedPassengerIds((prev) =>
      prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]
    )
  }

  const handleAddPassenger = async (data: PassengerFormData) => {
    setAddError(null)
    try {
      const createdPassenger = await passengerApi.createPassenger(data)
      setMyPassengers((prev) => [...prev, createdPassenger])
      setAddDialogOpen(false)
      reset()
    } catch (err) {
      setAddError((err as ApiError).message || "添加乘机人失败")
    }
  }

  const submitWaitlist = async () => {
    if (!selectedCabinClass) {
      setError("请选择目标舱位")
      return
    }
    if (selectedPassengerIds.length === 0) {
      setError("请至少选择 1 位乘机人")
      return
    }

    setIsSubmitting(true)
    setError(null)

    try {
      const data = await waitlistApi.createWaitlist({
        flightId,
        cabinClass: selectedCabinClass,
        passengerIds: selectedPassengerIds,
      })
      router.push(`/waitlist/${data.id}`)
    } catch (err) {
      setError((err as ApiError).message || "提交候补失败")
    } finally {
      setIsSubmitting(false)
    }
  }

  const formatTime = (iso?: string) => {
    if (!iso) return "—"
    const date = new Date(iso)
    return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(
      2,
      "0"
    )}`
  }

  const formatDuration = (minutes: number) => {
    const hours = Math.floor(minutes / 60)
    const restMinutes = minutes % 60
    return `${hours}时${restMinutes > 0 ? `${restMinutes}分` : ""}`
  }

  if (isAuthLoading || (isAuthenticated && isLoadingFlight)) {
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

  if (error && !flight) {
    return (
      <UserLayout>
        <div className="mx-auto max-w-4xl px-4 py-8 text-center">
          <p className="text-destructive mb-4">{error}</p>
          <Button variant="outline" render={<Link href="/flights">返回航班列表</Link>} nativeButton={false} />
        </div>
      </UserLayout>
    )
  }

  if (!flight) return null

  return (
    <UserLayout>
      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-6">
          <Link href="/flights" className="hover:text-foreground">
            航班查询
          </Link>
          <ChevronRight className="h-4 w-4" />
          <span className="text-foreground">创建候补</span>
        </div>

        <div className="flex items-center justify-center mb-8">
          {STEPS.map((item, index) => (
            <div key={item.label} className="flex items-center">
              <div className="flex flex-col items-center">
                <div
                  className={`flex items-center justify-center h-10 w-10 rounded-full border-2 transition-colors ${
                    index <= step
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-slate-200 text-muted-foreground"
                  }`}
                >
                  <item.icon className="h-4 w-4" />
                </div>
                <span className="text-xs mt-1.5 text-muted-foreground">{item.label}</span>
              </div>

              {index < STEPS.length - 1 && (
                <div
                  className={`w-12 sm:w-20 h-0.5 mx-2 transition-colors ${
                    index < step ? "bg-primary" : "bg-slate-200"
                  }`}
                />
              )}
            </div>
          ))}
        </div>

        {error && (
          <div className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive mb-6">
            {error}
          </div>
        )}

        {step === 0 && (
          <Card>
            <CardContent className="p-6">
              <h2 className="font-semibold mb-4">确认航班与目标舱位</h2>

              <div className="flex flex-wrap items-center gap-3 mb-4">
                <Plane className="h-5 w-5 text-primary" />
                <span className="font-bold">{flight.airlineName}</span>
                <Badge variant="outline">{flight.flightNo}</Badge>
                <FlightStatusBadge status={flight.status} />
              </div>

              <div className="flex items-center gap-8 mb-6">
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

              <div className="grid gap-3 sm:grid-cols-3">
                {cabins.map((cabin) => {
                  const isSelected = cabin.cabinClass === selectedCabinClass

                  return (
                    <button
                      key={cabin.cabinClass}
                      type="button"
                      onClick={() => setSelectedCabinClass(cabin.cabinClass)}
                      className={`rounded-xl border p-4 text-left transition-colors ${
                        isSelected
                          ? "border-primary bg-primary/5 ring-1 ring-primary/20"
                          : "border-slate-200 hover:border-slate-300"
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="font-medium">{CABIN_CLASS_LABEL[cabin.cabinClass]}</p>
                          <p className="text-xs text-muted-foreground mt-1">
                            当前可售 {getCabinAvailableSeats(cabin)} / {cabin.totalSeats} 座
                          </p>
                        </div>
                        {isSelected && <Badge>已选</Badge>}
                      </div>
                      <FlightPriceTag price={cabin.price} className="mt-3 text-lg" />
                    </button>
                  )
                })}
              </div>

              {selectedCabin && (
                <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm text-muted-foreground mt-5">
                  当前目标舱位：{CABIN_CLASS_LABEL[selectedCabin.cabinClass]}，单张票价 ¥
                  {selectedCabin.price.toLocaleString()}
                </div>
              )}

              <div className="flex justify-end mt-6">
                <Button onClick={() => setStep(1)} disabled={!selectedCabinClass}>
                  下一步 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {step === 1 && (
          <Card>
            <CardContent className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="font-semibold">选择乘机人</h2>

                <Dialog open={addDialogOpen} onOpenChange={setAddDialogOpen}>
                  <DialogTrigger
                    render={
                      <Button variant="outline" size="sm" className="gap-1">
                        <Plus className="h-3.5 w-3.5" /> 新增乘机人
                      </Button>
                    }
                  />
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
                        {errors.idCardNo && (
                          <p className="text-xs text-destructive">{errors.idCardNo.message}</p>
                        )}
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="pType">乘机人类型</Label>
                        <Select
                          defaultValue="ADULT"
                          onValueChange={(value) =>
                            setValue("passengerType", value as "ADULT" | "CHILD" | "INFANT")
                          }
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

              {isLoadingPassengers ? (
                <Skeleton className="h-32 w-full rounded-xl" />
              ) : myPassengers.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-8">
                  暂无常用乘机人，请点击“新增乘机人”添加
                </p>
              ) : (
                <div className="space-y-2">
                  {myPassengers.map((passenger) => (
                    <label
                      key={passenger.id}
                      className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                        selectedPassengerIds.includes(passenger.id)
                          ? "border-primary bg-primary/5"
                          : "border-slate-200 hover:border-slate-300"
                      }`}
                    >
                      <Checkbox
                        checked={selectedPassengerIds.includes(passenger.id)}
                        onCheckedChange={() => togglePassenger(passenger.id)}
                      />
                      <div className="flex-1">
                        <p className="font-medium text-sm">{passenger.name}</p>
                        <p className="text-xs text-muted-foreground">
                          {passenger.idCardNo} ·{" "}
                          {passenger.passengerType === "ADULT"
                            ? "成人"
                            : passenger.passengerType === "CHILD"
                              ? "儿童"
                              : "婴儿"}
                        </p>
                      </div>
                    </label>
                  ))}
                </div>
              )}

              <div className="flex justify-between mt-6">
                <Button variant="ghost" onClick={() => setStep(0)}>
                  <ChevronLeft className="h-4 w-4" /> 上一步
                </Button>
                <Button onClick={() => setStep(2)} disabled={selectedPassengerIds.length === 0}>
                  下一步 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        )}

        {step === 2 && (
          <div className="space-y-6">
            <Card>
              <CardContent className="p-6">
                <h2 className="font-semibold mb-4">候补确认</h2>

                <div className="rounded-lg bg-slate-50 p-4 mb-4">
                  <div className="flex items-center gap-2 mb-2">
                    <Plane className="h-4 w-4 text-primary" />
                    <span className="font-medium">
                      {flight.airlineName} {flight.flightNo}
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    {flight.departureCity}({formatTime(flight.departureTime)}) →{" "}
                    {flight.arrivalCity}({formatTime(flight.arrivalTime)}) ·{" "}
                    {formatDuration(flight.durationMinutes)}
                  </p>
                </div>

                <div className="space-y-2 text-sm mb-4">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">目标舱位</span>
                    <span>{selectedCabinClass ? CABIN_CLASS_LABEL[selectedCabinClass] : "—"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">乘机人数</span>
                    <span>{selectedPassengers.length}</span>
                  </div>
                  {selectedCabin && (
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">当前可售</span>
                      <span>{getCabinAvailableSeats(selectedCabin)} 座</span>
                    </div>
                  )}
                </div>

                <Separator className="my-4" />

                <h3 className="text-sm font-medium mb-2">乘机人</h3>
                <div className="space-y-1 mb-4">
                  {selectedPassengers.map((passenger) => (
                    <div key={passenger.id} className="flex items-center justify-between text-sm">
                      <span>{passenger.name}</span>
                      <span className="text-muted-foreground">
                        {passenger.passengerType === "ADULT"
                          ? "成人"
                          : passenger.passengerType === "CHILD"
                            ? "儿童"
                            : "婴儿"}
                      </span>
                    </div>
                  ))}
                </div>

                <Separator className="my-4" />

                <h3 className="text-sm font-medium mb-2">费用明细</h3>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">票价</span>
                    <span>¥{ticketAmount.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">机场建设费</span>
                    <span>¥{airportFee.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">燃油费</span>
                    <span>¥{fuelFee.toLocaleString()}</span>
                  </div>
                  <Separator />
                  <div className="flex justify-between font-bold text-base">
                    <span>合计</span>
                    <FlightPriceTag price={totalAmount} className="text-base" />
                  </div>
                </div>

                {selectedCabin && !isWaitlistNeeded && (
                  <div className="rounded-lg bg-amber-50 text-amber-700 px-4 py-3 text-sm mt-4">
                    当前舱位仍可满足已选乘机人数，无需候补，可直接前往预订页面下单。
                  </div>
                )}
              </CardContent>
            </Card>

            <div className="flex justify-between">
              <Button variant="ghost" onClick={() => setStep(1)}>
                <ChevronLeft className="h-4 w-4" /> 上一步
              </Button>

              <div className="space-x-3">
                {!isWaitlistNeeded && (
                  <Button
                    variant="outline"
                    render={<Link href={`/booking/${flight.id}`}>去正常预订</Link>}
                    nativeButton={false}
                  />
                )}
                <Button
                  onClick={submitWaitlist}
                  disabled={isSubmitting || !selectedCabinClass || selectedPassengers.length === 0 || !isWaitlistNeeded}
                >
                  {isSubmitting && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
                  <Check className="h-4 w-4 mr-1" /> 提交候补
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </UserLayout>
  )
}

export default function WaitlistCreatePage() {
  return (
    <Suspense
      fallback={
        <UserLayout>
          <div className="mx-auto max-w-4xl px-4 py-8 space-y-6">
            <Skeleton className="h-8 w-64" />
            <Skeleton className="h-48 w-full rounded-xl" />
            <Skeleton className="h-32 w-full rounded-xl" />
          </div>
        </UserLayout>
      }
    >
      <WaitlistCreateContent />
    </Suspense>
  )
}
