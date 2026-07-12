import type { ItineraryVO } from "@/types/flight";

export function waitlistUrl(journey: ItineraryVO, cabinClass?: string) {
  if (journey.journeyType !== "DIRECT") return null;
  const params = new URLSearchParams({
    flightId: String(journey.segments[0].id),
  });
  if (cabinClass) params.set("cabinClass", cabinClass);
  return `/waitlist/create?${params.toString()}`;
}
