"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useSearchParams } from "next/navigation";
import * as flightApi from "@/services/flightApi";
import type { ItineraryVO } from "@/types/flight";
import type { ApiError } from "@/lib/request";
import {
  cabinClassSearchParam,
  hasCompleteSearchCriteria,
  LatestRequestGuard,
} from "../flightSearchState";

interface UseFlightSearchReturn {
  itineraries: ItineraryVO[];
  total: number;
  page: number;
  size: number;
  isLoading: boolean;
  error: string | null;
  refresh: () => void;
}

const SORT_BY_TO_ENUM: Record<string, string> = {
  price: "PRICE_ASC",
  duration: "DURATION_ASC",
  departure: "TIME_ASC",
  seats: "SEATS_DESC",
};

export function useFlightSearch(): UseFlightSearchReturn {
  const searchParams = useSearchParams();
  const [itineraries, setItineraries] = useState<ItineraryVO[]>([]);
  const [total, setTotal] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const requestGuard = useRef(new LatestRequestGuard());

  const page = Number(searchParams.get("page") || "1");
  const size = Number(searchParams.get("size") || "10");

  const fetchFlights = useCallback(async () => {
    const version = requestGuard.current.next();
    try {
      const params: Record<string, string | number | boolean | undefined> = {};
      const departureCity = searchParams.get("departureCity");
      const arrivalCity = searchParams.get("arrivalCity");
      const departureDate = searchParams.get("departureDate");
      const departureDateStart = searchParams.get("departureDateStart");
      const departureDateEnd = searchParams.get("departureDateEnd");
      const minPrice = searchParams.get("minPrice");
      const maxPrice = searchParams.get("maxPrice");
      const directOnly = searchParams.get("directOnly");
      const departureTimeRange = searchParams.get("departureTimeRange");
      const sortBy = searchParams.get("sortBy");
      const cabinClass = searchParams.get("cabinClass");

      if (
        !hasCompleteSearchCriteria(departureCity, arrivalCity, departureDate)
      ) {
        setItineraries([]);
        setTotal(0);
        setError(null);
        setIsLoading(false);
        return;
      }
      setIsLoading(true);

      if (departureCity) params.departureCity = departureCity;
      if (arrivalCity) params.arrivalCity = arrivalCity;
      if (departureDate) params.departureDate = departureDate;
      if (departureDateStart && departureDateEnd) {
        params.departureDateStart = departureDateStart;
        params.departureDateEnd = departureDateEnd;
      }
      if (minPrice) params.minPrice = Number(minPrice);
      if (maxPrice) params.maxPrice = Number(maxPrice);
      if (directOnly === "true") params.directOnly = true;
      params.cabinClass = cabinClassSearchParam(cabinClass);
      if (departureTimeRange) {
        const parts = departureTimeRange.split("-");
        if (parts.length === 2) {
          params.departureTimeStart = parts[0];
          params.departureTimeEnd = parts[1];
        }
      }
      if (sortBy && SORT_BY_TO_ENUM[sortBy]) {
        params.sort = SORT_BY_TO_ENUM[sortBy];
      }
      params.page = page;
      params.size = size;

      const data = await flightApi.searchItineraries(params);
      if (!requestGuard.current.isLatest(version)) return;
      setItineraries(data.records);
      setTotal(data.total);
      setError(null);
    } catch (err) {
      if (!requestGuard.current.isLatest(version)) return;
      const apiErr = err as ApiError;
      setError(apiErr.message || "搜索航班失败");
      setItineraries([]);
      setTotal(0);
    } finally {
      if (requestGuard.current.isLatest(version)) setIsLoading(false);
    }
  }, [searchParams, page, size]);

  useEffect(() => {
    fetchFlights();
  }, [fetchFlights]);

  return {
    itineraries,
    total,
    page,
    size,
    isLoading,
    error,
    refresh: fetchFlights,
  };
}
