import { JourneyCard } from "@/components/journey/JourneyCard"
import type { CabinClass, ItineraryVO } from "@/types/flight"

export function ItineraryCard({ itinerary }: { itinerary: ItineraryVO; cabinClass?: CabinClass }) {
  return <JourneyCard journey={itinerary} />
}
