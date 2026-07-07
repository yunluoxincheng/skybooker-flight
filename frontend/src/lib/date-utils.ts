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

/**
 * 计算目标时间距离当前时刻还剩多少小时。
 *
 * @param iso ISO 格式的日期时间字符串；为空或无法解析时返回 `NaN`。
 * @returns 剩余小时数；`NaN` 表示解析失败，负数表示目标时间已过去。
 */
export function getHoursUntil(iso?: string | null) {
  const date = safeParse(iso)
  if (!date) return Number.NaN

  return (date.getTime() - Date.now()) / (1000 * 60 * 60)
}
