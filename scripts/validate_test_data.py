#!/usr/bin/env python3
"""Lightweight static checks for generated SkyBooker seed SQL."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


PROFILE_BOUNDS = {
    "dev": {
        "airports": (20, 40),
        "airlines": (8, 15),
        "routes": (80, 150),
        "users": (20, 50),
        "orders": (50, 200),
    },
    "test": {
        "airports": (50, 100),
        "airlines": (15, 30),
        "routes": (300, 800),
        "users": (200, 1000),
        "orders": (2000, 10000),
    },
    "perf": {
        "airports": (100, 120),
        "airlines": (30, 40),
        "routes": (1000, 1500),
        "users": (5000, 6000),
        "orders": (50000, 60000),
    },
}

REQUIRED_TABLES = [
    "airline",
    "airport",
    "users",
    "passenger",
    "flight",
    "flight_cabin",
    "ticket_order",
    "flight_seat",
    "order_passenger",
    "refund_record",
    "change_record",
    "waitlist_order",
    "waitlist_passenger",
    "ai_chat_session",
    "ai_chat_message",
    "ai_recommendation_record",
]

REQUIRED_ORDER_STATUSES = [
    "PENDING_PAYMENT",
    "ISSUED",
    "CANCELLED",
    "REFUNDED",
    "CHANGE_PENDING",
    "CHANGED",
]

REQUIRED_SEAT_STATUSES = ["AVAILABLE", "LOCKED", "SOLD", "DISABLED"]
REQUIRED_WAITLIST_STATUSES = ["PENDING_PAYMENT", "WAITING", "SUCCESS", "FAILED", "CANCELLED", "REFUNDED"]


def extract_summary(sql: str) -> dict:
    match = re.search(
        r"-- SKYBOOKER_SEED_SUMMARY_BEGIN\s*\n-- (?P<json>\{.*?\})\s*\n-- SKYBOOKER_SEED_SUMMARY_END",
        sql,
        re.DOTALL,
    )
    if not match:
        raise ValueError("missing SKYBOOKER_SEED_SUMMARY block")
    return json.loads(match.group("json"))


def count_literal(sql: str, literal: str) -> int:
    return len(re.findall(rf"'{re.escape(literal)}'", sql))


def validate(sql: str) -> tuple[dict, list[str]]:
    errors: list[str] = []
    summary = extract_summary(sql)
    profile = summary.get("profile")
    range_match = re.search(r"-- seed_id_range: (?P<start>\d+)-(?P<end>\d+)", sql)

    if "START TRANSACTION;" not in sql or "COMMIT;" not in sql:
        errors.append("seed SQL must wrap changes in START TRANSACTION/COMMIT")
    if "None" in sql or "'None'" in sql:
        errors.append("seed SQL contains Python None text")
    if "INSERT INTO flight(" not in sql or "INSERT INTO flight_seat(" not in sql:
        errors.append("flight and flight_seat inserts are required")

    for table in REQUIRED_TABLES:
        if f"INSERT INTO {table}" not in sql:
            errors.append(f"missing INSERT for {table}")

    validation = summary.get("validation", {})
    for name, passed in validation.items():
        if passed is not True:
            errors.append(f"summary validation flag is not true: {name}")

    if range_match:
        start_id = int(range_match.group("start"))
        end_id = int(range_match.group("end"))
        generated_min = summary.get("generatedIdMin")
        generated_max = summary.get("generatedIdMax")
        if not isinstance(generated_min, int) or not isinstance(generated_max, int):
            errors.append("summary must include integer generatedIdMin/generatedIdMax")
        elif generated_min < start_id or generated_max > end_id:
            errors.append(
                f"generated IDs {generated_min}-{generated_max} outside seed range {start_id}-{end_id}"
            )
    else:
        errors.append("missing seed_id_range comment")

    bounds = PROFILE_BOUNDS.get(profile)
    if bounds:
        for key, (minimum, maximum) in bounds.items():
            value = summary.get(key)
            if value is None or not (minimum <= value <= maximum):
                errors.append(f"{profile}.{key}={value} outside expected range {minimum}-{maximum}")

    for status in REQUIRED_ORDER_STATUSES:
        if count_literal(sql, status) == 0:
            errors.append(f"missing order status literal {status}")
    for status in REQUIRED_SEAT_STATUSES:
        if count_literal(sql, status) == 0:
            errors.append(f"missing seat status literal {status}")
    for status in REQUIRED_WAITLIST_STATUSES:
        if count_literal(sql, status) == 0:
            errors.append(f"missing waitlist status literal {status}")

    return summary, errors


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate generated SkyBooker seed SQL.")
    parser.add_argument("--file", required=True, help="Path to seed SQL file.")
    args = parser.parse_args()

    sql = Path(args.file).read_text(encoding="utf-8")
    try:
        summary, errors = validate(sql)
    except Exception as exc:
        print(f"Validation failed: {exc}", file=sys.stderr)
        sys.exit(1)

    print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    if errors:
        print("\nErrors:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        sys.exit(1)
    print(f"\nOK: {args.file}")


if __name__ == "__main__":
    main()
