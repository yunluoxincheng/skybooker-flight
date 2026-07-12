#!/usr/bin/env python3
"""Render a safe, profile-scoped cleanup transaction for generated test data."""

from __future__ import annotations

import argparse
from pathlib import Path


RANGES = {
    "dev": (100_000, 199_999),
    "test": (200_000, 1_199_999),
    "perf": (2_000_000, 11_999_999),
}


def selected_components(raw: str) -> set[str]:
    names = {item.strip().lower() for item in raw.split(",") if item.strip()}
    if "all" in names:
        return {"users", "flights", "orders", "refunds", "changes", "waitlists", "ai"}
    unknown = names - {"reference", "users", "flights", "orders", "refunds", "changes", "waitlists", "ai"}
    if unknown:
        raise ValueError(f"unknown components: {', '.join(sorted(unknown))}")
    # Deleting a parent must also remove every generated child that can hold an FK.
    if "flights" in names:
        names.update({"orders", "refunds", "changes", "waitlists"})
    if "orders" in names:
        names.update({"refunds", "changes", "waitlists"})
    return names


def render(profile: str, components: str) -> str:
    start, end = RANGES[profile]
    selected = selected_components(components)
    statements = [
        "SET NAMES utf8mb4;",
        "SET time_zone = '+08:00';",
        "START TRANSACTION;",
        f"-- Clean only generated rows in profile range {start}-{end}; reference rows are retained.",
    ]
    if "ai" in selected:
        statements.extend([
            f"DELETE FROM ai_recommendation_record WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM ai_chat_message WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM ai_chat_session WHERE id BETWEEN {start} AND {end};",
        ])
    if "waitlists" in selected:
        statements.extend([
            f"DELETE FROM waitlist_passenger WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM waitlist_order WHERE id BETWEEN {start} AND {end};",
        ])
    if "changes" in selected:
        statements.extend([
            f"DELETE FROM connecting_change_segment WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM connecting_change_record WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM change_record WHERE id BETWEEN {start} AND {end};",
        ])
    if "refunds" in selected:
        statements.append(f"DELETE FROM refund_record WHERE id BETWEEN {start} AND {end};")
    if "orders" in selected:
        statements.extend([
            f"DELETE FROM order_segment_passenger WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM ticket_order_segment WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM order_passenger WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM ticket_order WHERE id BETWEEN {start} AND {end};",
        ])
    if "flights" in selected:
        statements.extend([
            f"DELETE FROM connecting_itinerary WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM flight_seat WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM flight_cabin WHERE flight_id BETWEEN {start} AND {end};",
            f"DELETE FROM flight WHERE id BETWEEN {start} AND {end};",
        ])
    if "users" in selected:
        statements.extend([
            f"DELETE FROM passenger WHERE id BETWEEN {start} AND {end};",
            f"DELETE FROM users WHERE id BETWEEN {start} AND {end};",
        ])
    statements.extend(["COMMIT;", ""])
    return "\n".join(statements)


def main() -> None:
    parser = argparse.ArgumentParser(description="Render profile-scoped cleanup SQL.")
    parser.add_argument("--profile", choices=sorted(RANGES), required=True)
    parser.add_argument("--components", default="all")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render(args.profile, args.components), encoding="utf-8")


if __name__ == "__main__":
    main()
