import { describe, expect, it } from "vitest";
import { waitlistUrl } from "./journeyActions";

const journey = (journeyType: "DIRECT" | "CONNECTING") =>
  ({ journeyType, segments: [{ id: 42 }] }) as never;

describe("journey waitlist actions", () => {
  it("keeps direct cabin and whole-flight waitlist routes", () => {
    expect(waitlistUrl(journey("DIRECT"), "BUSINESS")).toBe(
      "/waitlist/create?flightId=42&cabinClass=BUSINESS",
    );
    expect(waitlistUrl(journey("DIRECT"))).toBe("/waitlist/create?flightId=42");
  });
  it("does not offer connecting waitlists", () =>
    expect(waitlistUrl(journey("CONNECTING"))).toBeNull());
});
