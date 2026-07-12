"use client"
import { useEffect, useState } from "react"
import { useParams, useRouter } from "next/navigation"
import { UserLayout } from "@/components/layout/UserLayout"
import { BookingWizard } from "@/features/booking/components/BookingWizard"
import { useAuth } from "@/contexts/AuthContext"
import * as flightApi from "@/services/flightApi"
import type { ItineraryVO } from "@/types/flight"
export default function ConnectingBookingPage() { const id = Number(useParams().id); const router = useRouter(); const auth = useAuth(); const [journey, setJourney] = useState<ItineraryVO | null>(null); const [error, setError] = useState(""); useEffect(() => { if (!auth.isLoading && !auth.isAuthenticated) router.push(`/login?redirect=/booking/connecting/${id}`); if (auth.isAuthenticated) flightApi.getConnectingItinerary(id).then(setJourney).catch((cause) => setError(cause.message)) }, [auth.isAuthenticated, auth.isLoading, id, router]); return <UserLayout><main className="px-4 py-8">{error ? <p className="text-center text-destructive">{error}</p> : journey ? <BookingWizard journey={journey}/> : <p className="text-center text-muted-foreground">正在加载联程预订…</p>}</main></UserLayout> }
