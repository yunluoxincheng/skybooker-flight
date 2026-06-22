"use client"

import { useState, useCallback } from "react"
import { useRouter } from "next/navigation"
import * as flightApi from "@/services/flightApi"
import * as passengerApi from "@/services/passengerApi"
import * as orderApi from "@/services/orderApi"
import type { FlightVO, FlightSeatVO } from "@/types/flight"
import type { PassengerVO } from "@/types/passenger"
import type { OrderVO } from "@/types/order"
import type { ApiError } from "@/lib/request"

export type BookingStep = 0 | 1 | 2 | 3

interface BookingState {
  step: BookingStep
  flight: FlightVO | null
  seats: FlightSeatVO[]
  myPassengers: PassengerVO[]
  selectedPassengerIds: number[]
  selectedSeatIds: number[]
  createdOrder: OrderVO | null
  isLoadingFlight: boolean
  isLoadingPassengers: boolean
  isSubmitting: boolean
  error: string | null
}

export function useBooking(flightId: number) {
  const router = useRouter()
  const [state, setState] = useState<BookingState>({
    step: 0,
    flight: null,
    seats: [],
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
      setPartial({ flight, seats, isLoadingFlight: false })
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
      const next = Math.min(s.step + 1, 3) as BookingStep
      return { ...s, step: next }
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
      return {
        ...s,
        selectedPassengerIds: exists
          ? s.selectedPassengerIds.filter((id) => id !== pId)
          : [...s.selectedPassengerIds, pId],
      }
    })
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
        passengerIds: state.selectedPassengerIds,
        seatIds: state.selectedSeatIds,
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

  return {
    ...state,
    loadFlight,
    loadPassengers,
    goToStep,
    nextStep,
    prevStep,
    togglePassenger,
    toggleSeat,
    submitOrder,
    payOrder,
    setPartial,
  }
}
