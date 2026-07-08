"use client"

import { useState, useCallback } from "react"
import { useRouter } from "next/navigation"
import * as flightApi from "@/services/flightApi"
import * as passengerApi from "@/services/passengerApi"
import * as orderApi from "@/services/orderApi"
import { getCabinAvailableSeats, getFallbackCabinPrice } from "@/lib/cabin-utils"
import { isFlightBookable } from "@/lib/flight-utils"
import {
  CABIN_CLASS_ORDER,
  type CabinClass,
  type FlightCabinVO,
  type FlightSeatVO,
  type FlightVO,
} from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"
import type { OrderVO } from "@/types/order"
import type { ApiError } from "@/lib/request"

export type BookingStep = 0 | 1 | 2 | 3

interface BookingState {
  step: BookingStep
  flight: FlightVO | null
  seats: FlightSeatVO[]
  selectedCabinClass: CabinClass | null
  myPassengers: PassengerVO[]
  selectedPassengerIds: number[]
  selectedSeatIds: number[]
  createdOrder: OrderVO | null
  isLoadingFlight: boolean
  isLoadingPassengers: boolean
  isSubmitting: boolean
  error: string | null
}

function deriveFlightCabins(flight: FlightVO, seats: FlightSeatVO[]): FlightCabinVO[] {
  const configuredCabins = new Map((flight.cabins ?? []).map((cabin) => [cabin.cabinClass, cabin]))

  return CABIN_CLASS_ORDER
    .flatMap((cabinClass): FlightCabinVO[] => {
      const configured = configuredCabins.get(cabinClass)
      const cabinSeats = seats.filter((seat) => seat.cabinClass === cabinClass)

      if (!configured && cabinSeats.length === 0) {
        return []
      }

      const seatPrice = cabinSeats[0]?.price
      const availableSeats = cabinSeats.filter((seat) => seat.status === "AVAILABLE").length

      return [{
        cabinClass,
        price: configured?.price ?? seatPrice ?? getFallbackCabinPrice(flight.basePrice, cabinClass),
        totalSeats: configured?.totalSeats ?? cabinSeats.length,
        availableSeats: configured ? getCabinAvailableSeats(configured) : availableSeats,
      }]
    })
}

function getDefaultCabinClass(flight: FlightVO, seats: FlightSeatVO[]): CabinClass | null {
  const cabins = deriveFlightCabins(flight, seats)
  return cabins.find((cabin) => getCabinAvailableSeats(cabin) > 0)?.cabinClass ?? cabins[0]?.cabinClass ?? null
}

export function useBooking(flightId: number) {
  const router = useRouter()
  const [state, setState] = useState<BookingState>({
    step: 0,
    flight: null,
    seats: [],
    selectedCabinClass: null,
    myPassengers: [],
    selectedPassengerIds: [],
    selectedSeatIds: [],
    createdOrder: null,
    isLoadingFlight: true,
    isLoadingPassengers: false,
    isSubmitting: false,
    error: null,
  })

  const setPartial = useCallback(
    (partial: Partial<BookingState>) => setState((s) => ({ ...s, ...partial })),
    []
  )

  // 加载航班数据
  const loadFlight = useCallback(async () => {
    setPartial({ isLoadingFlight: true, error: null })
    try {
      const [flight, seats] = await Promise.all([
        flightApi.getFlightById(flightId),
        flightApi.getFlightSeats(flightId),
      ])
      setPartial({
        flight,
        seats,
        selectedCabinClass: getDefaultCabinClass(flight, seats),
        isLoadingFlight: false,
      })
    } catch (err) {
      setPartial({ error: (err as ApiError).message || "加载航班失败", isLoadingFlight: false })
    }
  }, [flightId, setPartial])

  // 加载乘机人
  const loadPassengers = useCallback(async () => {
    setPartial({ isLoadingPassengers: true, error: null })
    try {
      const myPassengers = await passengerApi.getMyPassengers()
      setPartial({ myPassengers, isLoadingPassengers: false })
    } catch (err) {
      setPartial({ error: (err as ApiError).message || "加载乘机人失败", isLoadingPassengers: false })
    }
  }, [setPartial])

  // 步骤导航
  const goToStep = useCallback(
    (step: BookingStep) => {
      if (step === 0) {
        setPartial({ step, selectedPassengerIds: [], selectedSeatIds: [] })
      } else {
        setPartial({ step })
      }
    },
    [setPartial]
  )

  const nextStep = useCallback(() => {
    setState((s) => {
      if (s.step === 0 && s.flight) {
        const bookableResult = isFlightBookable(s.flight)
        if (!bookableResult.bookable) {
          return {
            ...s,
            error: bookableResult.reason || "该航班当前不可预订",
          }
        }
      }

      const next = Math.min(s.step + 1, 3) as BookingStep
      return { ...s, step: next, error: null }
    })
  }, [])

  const prevStep = useCallback(() => {
    setState((s) => {
      const prev = Math.max(s.step - 1, 0) as BookingStep
      return { ...s, step: prev }
    })
  }, [])

  // 乘机人选择
  const togglePassenger = useCallback((pId: number) => {
    setState((s) => {
      const exists = s.selectedPassengerIds.includes(pId)
      const nextPassengerIds = exists
        ? s.selectedPassengerIds.filter((id) => id !== pId)
        : [...s.selectedPassengerIds, pId]
      const maxSeats = nextPassengerIds.length || 1

      return {
        ...s,
        selectedPassengerIds: nextPassengerIds,
        selectedSeatIds: s.selectedSeatIds.slice(0, maxSeats),
      }
    })
  }, [])

  const selectCabin = useCallback((cabinClass: CabinClass) => {
    setState((s) => ({
      ...s,
      selectedCabinClass: cabinClass,
      selectedSeatIds: s.selectedSeatIds.filter((seatId) => {
        const seat = s.seats.find((item) => item.id === seatId)
        return seat?.cabinClass === cabinClass
      }),
    }))
  }, [])

  // 座位选择
  const toggleSeat = useCallback(
    (seatId: number) => {
      setState((s) => {
        const exists = s.selectedSeatIds.includes(seatId)
        const maxSeats = s.selectedPassengerIds.length || 1
        if (!exists && s.selectedSeatIds.length >= maxSeats) {
          // 超出可选数量，取消选择第一个再加入新的
          return {
            ...s,
            selectedSeatIds: [...s.selectedSeatIds.slice(1), seatId],
          }
        }
        return {
          ...s,
          selectedSeatIds: exists
            ? s.selectedSeatIds.filter((id) => id !== seatId)
            : [...s.selectedSeatIds, seatId],
        }
      })
    },
    []
  )

  // 创建订单
  const submitOrder = useCallback(async () => {
    if (state.selectedPassengerIds.length === 0) {
      setPartial({ error: "请选择乘机人" })
      return
    }
    if (state.selectedSeatIds.length !== state.selectedPassengerIds.length) {
      setPartial({ error: "座位数量必须等于乘机人数" })
      return
    }

    setPartial({ isSubmitting: true, error: null })
    try {
      const order = await orderApi.createOrder({
        flightId,
        items: state.selectedPassengerIds.map((pId, i) => ({
          passengerId: pId,
          seatId: state.selectedSeatIds[i],
        })),
      })
      setPartial({ createdOrder: order, isSubmitting: false })
      nextStep()
    } catch (err) {
      setPartial({ error: (err as ApiError).message || "创建订单失败", isSubmitting: false })
    }
  }, [flightId, state.selectedPassengerIds, state.selectedSeatIds, setPartial, nextStep])

  // 支付
  const payOrder = useCallback(async () => {
    if (!state.createdOrder) return
    setPartial({ isSubmitting: true, error: null })
    try {
      await orderApi.payOrder(state.createdOrder.id)
      router.push(`/orders/${state.createdOrder.id}`)
    } catch (err) {
      setPartial({ error: (err as ApiError).message || "支付失败", isSubmitting: false })
    }
  }, [state.createdOrder, setPartial, router])

  const cabins = state.flight ? deriveFlightCabins(state.flight, state.seats) : []
  const filteredSeats = state.selectedCabinClass
    ? state.seats.filter((seat) => seat.cabinClass === state.selectedCabinClass)
    : state.seats
  const bookableResult = state.flight ? isFlightBookable(state.flight) : { bookable: false as const }

  return {
    ...state,
    cabins,
    filteredSeats,
    flightBookable: bookableResult.bookable,
    unbookableReason: bookableResult.bookable ? null : bookableResult.reason || "该航班当前不可预订",
    loadFlight,
    loadPassengers,
    goToStep,
    nextStep,
    prevStep,
    togglePassenger,
    selectCabin,
    toggleSeat,
    submitOrder,
    payOrder,
    setPartial,
  }
}
