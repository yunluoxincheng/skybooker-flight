import { describe, expect, it } from "vitest";
import {
  applyItineraryListSearch,
  clearItineraryListSearch,
} from "./connectingItineraryListSearch";

describe("connecting itinerary list search", () => {
  it("trims searches and returns to the first page", () =>
    expect(applyItineraryListSearch(" #588 ")).toEqual({
      keyword: "#588",
      page: 1,
    }));
  it("clears conditions and returns to the first page", () =>
    expect(clearItineraryListSearch()).toEqual({ keyword: "", page: 1 }));
});
