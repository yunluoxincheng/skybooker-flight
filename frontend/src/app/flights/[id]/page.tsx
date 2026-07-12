"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { JourneyDetail } from "@/components/journey/JourneyDetail";
import { UserLayout } from "@/components/layout/UserLayout";
import { Skeleton } from "@/components/ui/skeleton";
import { isFlightBookable } from "@/lib/flight-utils";
import * as flightApi from "@/services/flightApi";
import type { FlightVO, ItineraryVO } from "@/types/flight";
import type { ApiError } from "@/lib/request";

function toJourney(flight: FlightVO): ItineraryVO {
  const availability = isFlightBookable(flight);
  return {
    id: flight.id,
    journeyType: "DIRECT",
    segments: [flight],
    originCity: flight.departureCity,
    destinationCity: flight.arrivalCity,
    totalDurationMinutes: flight.durationMinutes,
    estimatedAmount: flight.basePrice,
    availableSeats: flight.remainingSeats,
    sellable: availability.bookable,
    unavailableReason: availability.bookable ? undefined : availability.reason,
  };
}

export default function FlightDetailPage() {
  const id = Number(useParams().id);
  const [journey, setJourney] = useState<ItineraryVO | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (id > 0)
      flightApi
        .getFlightById(id)
        .then((flight) => setJourney(toJourney(flight)))
        .catch((cause: ApiError) =>
          setError(cause.message || "加载航班详情失败"),
        );
  }, [id]);
  return (
    <UserLayout>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <div className="mb-6 flex items-center gap-2 text-sm text-muted-foreground">
          <Link href="/">首页</Link>
          <ChevronRight className="h-4 w-4" />
          <Link href="/flights">航班查询</Link>
          <ChevronRight className="h-4 w-4" />
          <span className="text-foreground">行程详情</span>
        </div>
        {error ? (
          <p className="text-center text-destructive">{error}</p>
        ) : journey ? (
          <JourneyDetail journey={journey} />
        ) : (
          <div className="space-y-4">
            <Skeleton className="h-64" />
            <Skeleton className="h-48" />
          </div>
        )}
      </main>
    </UserLayout>
  );
}
