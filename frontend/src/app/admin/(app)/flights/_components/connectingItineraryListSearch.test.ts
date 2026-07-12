import { describe, expect, it } from "vitest";
import {
  applyItineraryListSearch,
  clearItineraryListSearch,
  isSegmentScope,
  SEGMENT_SCOPE_LABELS,
} from "./connectingItineraryListSearch";

describe("connecting itinerary list search", () => {
  it("trims searches and returns to the first page", () =>
    expect(applyItineraryListSearch(" #588 ")).toEqual({
      keyword: "#588",
      page: 1,
    }));
  it("clears conditions and returns to the first page", () =>
    expect(clearItineraryListSearch()).toEqual({ keyword: "", page: 1 }));
  it("provides Chinese labels for every segment scope", () => {
    expect(SEGMENT_SCOPE_LABELS).toEqual({
      ALL: "全部航段",
      FIRST: "第一航段",
      SECOND: "第二航段",
    });
    expect(isSegmentScope("FIRST")).toBe(true);
    expect(isSegmentScope("UNKNOWN")).toBe(false);
  });
});
