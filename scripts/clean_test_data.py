#!/usr/bin/env python3
"""Render ownership-based cleanup SQL for generated test data."""

from __future__ import annotations

import argparse
from pathlib import Path


COMPONENT_NAMES = {"reference", "users", "flights", "orders", "refunds", "changes", "waitlists", "ai"}
ALL_OWNED_TABLES = (
    "ai_recommendation_record",
    "ai_chat_message",
    "ai_chat_session",
    "waitlist_passenger",
    "waitlist_order",
    "connecting_change_segment",
    "connecting_change_record",
    "change_record",
    "refund_record",
    "order_segment_passenger",
    "ticket_order_segment",
    "order_passenger",
    "ticket_order",
    "connecting_itinerary",
    "flight_seat",
    "flight_cabin",
    "flight",
    "passenger",
    "users",
)


def selected_components(raw: str) -> set[str]:
    names = {item.strip().lower() for item in raw.split(",") if item.strip()}
    if "all" in names:
        return set(ALL_OWNED_TABLES)
    unknown = names - COMPONENT_NAMES
    if unknown:
        raise ValueError(f"unknown components: {', '.join(sorted(unknown))}")

    tables: set[str] = set()
    if "users" in names:
        # User-owned data includes every generated child. Removing only the
        # account would violate several FKs and leave stale seat counters.
        tables.update(ALL_OWNED_TABLES)
    if "flights" in names:
        tables.update({
            "ai_recommendation_record", "ai_chat_message", "ai_chat_session",
            "waitlist_passenger", "waitlist_order", "connecting_change_segment",
            "connecting_change_record", "change_record", "refund_record",
            "order_segment_passenger", "ticket_order_segment", "order_passenger",
            "ticket_order", "connecting_itinerary", "flight_seat", "flight_cabin", "flight",
        })
    if "orders" in names:
        tables.update({
            "waitlist_passenger", "waitlist_order", "connecting_change_segment",
            "connecting_change_record", "change_record", "refund_record",
            "order_segment_passenger", "ticket_order_segment", "order_passenger", "ticket_order",
        })
    if "refunds" in names:
        tables.add("refund_record")
    if "changes" in names:
        tables.update({"connecting_change_segment", "connecting_change_record", "change_record"})
    if "waitlists" in names:
        tables.update({"waitlist_passenger", "waitlist_order"})
    if "ai" in names:
        tables.update({"ai_recommendation_record", "ai_chat_message", "ai_chat_session"})
    return tables


def render(profile: str, components: str) -> str:
    batch_key = f"skybooker:{profile}"
    tables = selected_components(components)
    statements = [
        "SET NAMES utf8mb4;",
        "SET time_zone = '+08:00';",
        "START TRANSACTION;",
        f"-- Remove only rows registered to ownership batch '{batch_key}'.",
    ]
    for table in ALL_OWNED_TABLES:
        if table in tables:
            statements.append(
                f"DELETE target FROM {table} target JOIN test_data_ownership owner "
                f"ON owner.batch_key = '{batch_key}' AND owner.table_name = '{table}' "
                "AND owner.row_id = target.id;"
            )

    # Deleting orders can turn generated seats back into ordinary available
    # seats. Never touch a seat still referenced by any remaining order.
    if tables.intersection({"ticket_order", "order_passenger", "ticket_order_segment", "order_segment_passenger"}):
        statements.extend([
            "UPDATE flight_seat s",
            "JOIN test_data_ownership owner ON owner.batch_key = '" + batch_key + "'",
            "    AND owner.table_name = 'flight_seat' AND owner.row_id = s.id",
            "SET s.status = 'AVAILABLE', s.locked_by_order_id = NULL, s.lock_expire_time = NULL",
            "WHERE NOT EXISTS (SELECT 1 FROM order_passenger op WHERE op.seat_id = s.id)",
            "  AND NOT EXISTS (SELECT 1 FROM order_segment_passenger osp WHERE osp.seat_id = s.id)",
            "  AND s.status IN ('LOCKED', 'SOLD');",
            "UPDATE flight f",
            "JOIN test_data_ownership owner ON owner.batch_key = '" + batch_key + "'",
            "    AND owner.table_name = 'flight' AND owner.row_id = f.id",
            "SET f.remaining_seats = (SELECT COUNT(*) FROM flight_seat s WHERE s.flight_id = f.id AND s.status = 'AVAILABLE');",
        ])

    if tables:
        quoted_tables = ", ".join("'" + table + "'" for table in sorted(tables))
        statements.append(
            f"DELETE FROM test_data_ownership WHERE batch_key = '{batch_key}' AND table_name IN ({quoted_tables});"
        )
    statements.extend([
        f"DELETE FROM test_data_batch WHERE batch_key = '{batch_key}' "
        "AND NOT EXISTS (SELECT 1 FROM test_data_ownership WHERE batch_key = '" + batch_key + "');",
        "COMMIT;",
        "",
    ])
    return "\n".join(statements)


def main() -> None:
    parser = argparse.ArgumentParser(description="Render ownership-scoped cleanup SQL.")
    parser.add_argument("--profile", choices=("dev", "test", "perf"), required=True)
    parser.add_argument("--components", default="all")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render(args.profile, args.components), encoding="utf-8")


if __name__ == "__main__":
    main()
