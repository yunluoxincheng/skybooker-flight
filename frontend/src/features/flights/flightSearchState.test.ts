import { describe, expect, it } from "vitest";
import {
  cabinClassSearchParam,
  hasCompleteSearchCriteria,
  LatestRequestGuard,
} from "./flightSearchState";

describe("flight search state", () => {
  it("keeps the empty flights page idle until all required criteria exist", () => {
    expect(hasCompleteSearchCriteria(null, null, null)).toBe(false);
    expect(hasCompleteSearchCriteria("上海", "北京", "")).toBe(false);
    expect(hasCompleteSearchCriteria(" 上海 ", " 北京 ", "2026-07-16")).toBe(
      true,
    );
  });

  it("accepts only the latest response during rapid date changes", () => {
    const guard = new LatestRequestGuard();
    const first = guard.next();
    const second = guard.next();
    expect(guard.isLatest(first)).toBe(false);
    expect(guard.isLatest(second)).toBe(true);
  });

  it("forwards only supported cabin classes", () => {
    expect(cabinClassSearchParam("BUSINESS")).toBe("BUSINESS");
    expect(cabinClassSearchParam("PREMIUM")).toBeUndefined();
  });
});
