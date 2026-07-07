function safeParse(iso?: string | null) {
  if (!iso) return null

  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return null

  return date
}

export function formatTime(iso?: string | null) {
  const date = safeParse(iso)
  if (!date) return "--"

  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`
}

export function formatDate(iso?: string | null) {
  const date = safeParse(iso)
  if (!date) return "--"

  return `${date.getMonth() + 1}月${date.getDate()}日`
}

export function formatDateFull(iso?: string | null) {
  const date = safeParse(iso)
  if (!date) return "--"

  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

export function isCrossDay(departureIso?: string | null, arrivalIso?: string | null) {
  const departure = safeParse(departureIso)
  const arrival = safeParse(arrivalIso)
  if (!departure || !arrival) return false

  const departureDay = new Date(
    departure.getFullYear(),
    departure.getMonth(),
    departure.getDate()
  )
  const arrivalDay = new Date(arrival.getFullYear(), arrival.getMonth(), arrival.getDate())

  return arrivalDay.getTime() > departureDay.getTime()
}

export function getCrossDayLabel(departureIso?: string | null, arrivalIso?: string | null) {
  const departure = safeParse(departureIso)
  const arrival = safeParse(arrivalIso)
  if (!departure || !arrival) return ""

  const departureDay = new Date(
    departure.getFullYear(),
    departure.getMonth(),
    departure.getDate()
  )
  const arrivalDay = new Date(arrival.getFullYear(), arrival.getMonth(), arrival.getDate())
  const diffDays = Math.round(
    (arrivalDay.getTime() - departureDay.getTime()) / (24 * 60 * 60 * 1000)
  )

  return diffDays > 0 ? `+${diffDays}天` : ""
}
