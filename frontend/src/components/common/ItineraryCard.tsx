import { JourneyCard } from "@/components/journey/JourneyCard";
import type { ItineraryVO } from "@/types/flight";

export function ItineraryCard({ itinerary }: { itinerary: ItineraryVO }) {
  return <JourneyCard journey={itinerary} />;
}
