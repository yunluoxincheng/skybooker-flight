"use client";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { UserLayout } from "@/components/layout/UserLayout";
import { BookingWizard } from "@/features/booking/components/BookingWizard";
import { useAuth } from "@/contexts/AuthContext";
import * as flightApi from "@/services/flightApi";
import type { FlightVO, ItineraryVO } from "@/types/flight";
import { isFlightBookable } from "@/lib/flight-utils";

const directJourney = (flight: FlightVO): ItineraryVO => {
  const result = isFlightBookable(flight);
  return {
    id: flight.id,
    journeyType: "DIRECT",
    segments: [flight],
    originCity: flight.departureCity,
    destinationCity: flight.arrivalCity,
    totalDurationMinutes: flight.durationMinutes,
    estimatedAmount: flight.basePrice,
    availableSeats: flight.remainingSeats,
    sellable: result.bookable,
    unavailableReason: result.bookable ? undefined : result.reason,
  };
};
export default function BookingPage() {
  const id = Number(useParams().flightId);
  const router = useRouter();
  const auth = useAuth();
  const [journey, setJourney] = useState<ItineraryVO | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated)
      router.push(`/login?redirect=/booking/${id}`);
    if (auth.isAuthenticated)
      flightApi
        .getFlightById(id)
        .then((flight) => setJourney(directJourney(flight)))
        .catch((cause) => setError(cause.message));
  }, [auth.isAuthenticated, auth.isLoading, id, router]);
  return (
    <UserLayout>
      <main className="px-4 py-8">
        {error ? (
          <p className="text-center text-destructive">{error}</p>
        ) : journey ? (
          <BookingWizard journey={journey} />
        ) : (
          <p className="text-center text-muted-foreground">正在加载预订信息…</p>
        )}
      </main>
    </UserLayout>
  );
}
