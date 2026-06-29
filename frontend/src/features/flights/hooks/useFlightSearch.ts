"use client"

import { useEffect, useState, useCallback } from "react"
import { useSearchParams } from "next/navigation"
import * as flightApi from "@/services/flightApi"
import type { FlightVO } from "@/types/flight"
import type { ApiError } from "@/lib/request"

interface UseFlightSearchReturn {
  flights: FlightVO[]
  total: number
  page: number
  size: number
  isLoading: boolean
  error: string | null
  refresh: () => void
}

export function useFlightSearch(): UseFlightSearchReturn {
  const searchParams = useSearchParams()
  const [flights, setFlights] = useState<FlightVO[]>([])
  const [total, setTotal] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const page = Number(searchParams.get("page") || "1")
  const size = Number(searchParams.get("size") || "10")

  const fetchFlights = useCallback(async () => {
    try {
      const params: Record<string, string | number | boolean | undefined> = {}
      const departureCity = searchParams.get("departureCity")
      const arrivalCity = searchParams.get("arrivalCity")
      const departureDate = searchParams.get("departureDate")
      const minPrice = searchParams.get("minPrice")
      const maxPrice = searchParams.get("maxPrice")
      const directOnly = searchParams.get("directOnly")
      const departureTimeRange = searchParams.get("departureTimeRange")
      const sortBy = searchParams.get("sortBy")
      const sortOrder = searchParams.get("sortOrder")
      const airlineCode = searchParams.get("airlineCode")

      if (departureCity) params.departureCity = departureCity
      if (arrivalCity) params.arrivalCity = arrivalCity
      if (departureDate) params.departureDate = departureDate
      if (minPrice) params.minPrice = Number(minPrice)
      if (maxPrice) params.maxPrice = Number(maxPrice)
      if (directOnly === "true") params.directOnly = true
      if (departureTimeRange) {
        const parts = departureTimeRange.split("-")
        if (parts.length === 2) {
          params.departureTimeStart = parts[0]
          params.departureTimeEnd = parts[1]
        }
      }
      if (sortBy) {
        const order = sortOrder || "asc"
        params.sort = `${sortBy}_${order}`
      }
      if (airlineCode) params.airlineId = Number(airlineCode)
      params.page = page
      params.size = size

      const data = await flightApi.searchFlights(params)
      setFlights(data.records)
      setTotal(data.total)
      setError(null)
    } catch (err) {
      const apiErr = err as ApiError
      setError(apiErr.message || "搜索航班失败")
      setFlights([])
      setTotal(0)
    } finally {
      setIsLoading(false)
    }
  }, [searchParams, page, size])

  useEffect(() => {
    fetchFlights()
  }, [fetchFlights])

  return { flights, total, page, size, isLoading, error, refresh: fetchFlights }
}
