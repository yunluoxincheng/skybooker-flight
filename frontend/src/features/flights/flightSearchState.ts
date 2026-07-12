export function hasCompleteSearchCriteria(
  origin?: string | null,
  destination?: string | null,
  date?: string | null,
) {
  return Boolean(origin?.trim() && destination?.trim() && date);
}

export class LatestRequestGuard {
  private version = 0;
  next() {
    return ++this.version;
  }
  isLatest(version: number) {
    return version === this.version;
  }
}

export function cabinClassSearchParam(value?: string | null) {
  return value === "ECONOMY" || value === "BUSINESS" || value === "FIRST"
    ? value
    : undefined;
}
