#!/usr/bin/env python3
"""Validate SkyBooker test data SQL statically or against a MySQL database."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path


PROFILE_BOUNDS = {
    "dev": {"airports": (20, 40), "airlines": (8, 15), "routes": (80, 200), "users": (20, 50), "orders": (50, 220)},
    "test": {"airports": (50, 100), "airlines": (15, 30), "routes": (300, 900), "users": (200, 1000), "orders": (2000, 10000)},
    "perf": {"airports": (100, 120), "airlines": (30, 40), "routes": (1000, 1500), "users": (5000, 6000), "orders": (50000, 60000)},
}

TABLES_BY_COMPONENT = {
    "reference": ("airline", "airport"),
    "users": ("users", "passenger"),
    "flights": ("flight", "flight_cabin", "flight_seat"),
    "orders": ("ticket_order", "order_passenger"),
    "refunds": ("refund_record",),
    "changes": ("change_record",),
    "waitlists": ("waitlist_order", "waitlist_passenger"),
    "ai": ("ai_chat_session", "ai_chat_message", "ai_recommendation_record"),
}

REQUIRED_WAITLIST_STATUSES = ["PENDING_PAYMENT", "WAITING", "SUCCESS", "FAILED", "CANCELLED", "REFUNDED"]

SCENARIO_COMPONENT_DEPENDENCIES = {
    "direct": {"flights"},
    "connecting": {"flights"},
    "payment": {"orders"},
    "cancel": {"orders"},
    "refund": {"orders", "refunds"},
    "change": {"orders", "changes"},
    "waitlist": {"orders", "waitlists"},
    "sold-out": {"flights"},
    "delayed": {"flights"},
    "near-departure": {"flights"},
}


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


def selected_tables(summary: dict) -> set[str]:
    tables: set[str] = set()
    components = set(summary.get("components", []))
    if "reference" in components:
        tables.update(TABLES_BY_COMPONENT["reference"])
    if "users" in components and summary.get("users", 0) > 0:
        tables.update(TABLES_BY_COMPONENT["users"])
    if "flights" in components and summary.get("flights", 0) > 0:
        tables.update(TABLES_BY_COMPONENT["flights"])
    if "orders" in components and summary.get("orders", 0) > 0:
        tables.add("ticket_order")
        if summary.get("orders", 0) > summary.get("connectingOrders", 0):
            tables.add("order_passenger")
    if "refunds" in components and summary.get("refunds", 0) > 0:
        tables.add("refund_record")
    if "changes" in components and summary.get("changes", 0) > 0:
        tables.add("change_record")
    if "waitlists" in components and summary.get("waitlists", 0) > 0:
        tables.update(TABLES_BY_COMPONENT["waitlists"])
    if "ai" in components and summary.get("aiSessions", 0) > 0:
        tables.update(TABLES_BY_COMPONENT["ai"])
    if summary.get("connectingItineraries", 0) > 0:
        tables.add("connecting_itinerary")
    if summary.get("connectingOrders", 0) > 0:
        tables.update({"ticket_order_segment", "order_segment_passenger"})
    if summary.get("connectingChanges", 0) > 0:
        tables.update({"connecting_change_record", "connecting_change_segment"})
    return tables


def validate(sql: str) -> tuple[dict, list[str]]:
    errors: list[str] = []
    summary = extract_summary(sql)
    profile = summary.get("profile")
    components = set(summary.get("components", []))
    scenarios = set(summary.get("scenarios", []))
    range_match = re.search(r"-- seed_id_range: (?P<start>\d+)-(?P<end>\d+)", sql)

    if "START TRANSACTION;" not in sql or "COMMIT;" not in sql:
        errors.append("seed SQL must wrap changes in START TRANSACTION/COMMIT")
    if "test_data_batch" not in sql or "test_data_ownership" not in sql:
        errors.append("seed SQL must register ownership in the V18 test-data tables")
    if re.search(r"\bNone\b|'None'", sql):
        errors.append("seed SQL contains Python None text")
    if re.search(r"\breal_name\b", sql, re.IGNORECASE):
        errors.append("seed SQL must not contain the removed users.real_name field")

    expected_batch = f"skybooker:{profile}" if profile else ""
    if summary.get("batchKey") != expected_batch:
        errors.append("summary batchKey does not match profile")
    if not isinstance(summary.get("seed"), int) or isinstance(summary.get("seed"), bool):
        errors.append("summary seed must be an integer")
    source_ref = summary.get("sourceRef")
    if source_ref is not None and (
        not isinstance(source_ref, str) or not re.fullmatch(r"[0-9a-fA-F]{40}", source_ref)
    ):
        errors.append("summary sourceRef must be null or a 40-character commit SHA")

    if summary.get("scenariosExplicit", True):
        for scenario in sorted(scenarios):
            missing = SCENARIO_COMPONENT_DEPENDENCIES.get(scenario, set()) - components
            if missing:
                errors.append(
                    f"scenario {scenario} requires components: {', '.join(sorted(missing))}"
                )

    for table in sorted(selected_tables(summary)):
        if f"INSERT INTO {table}" not in sql:
            errors.append(f"missing INSERT for selected table {table}")

    if "connecting" in scenarios and "flights" in components:
        if summary.get("connectingItineraries", 0) < 1:
            errors.append("connecting scenario requires at least one managed itinerary")
        if "INSERT INTO connecting_itinerary" not in sql:
            errors.append("connecting scenario requires connecting_itinerary inserts")
    if summary.get("connectingOrders", 0) > 0 and "orders" in components:
        if "CONNECTING" not in sql or "ticket_order_segment" not in sql or "order_segment_passenger" not in sql:
            errors.append("connecting orders require journey_type and both segment snapshot tables")
    if summary.get("connectingChanges", 0) > 0 and "changes" in components:
        if count_literal(sql, "OLD") == 0 or count_literal(sql, "NEW") == 0:
            errors.append("connecting changes require OLD and NEW snapshots")

    validation = summary.get("validation", {})
    for name, passed in validation.items():
        if passed is not True:
            errors.append(f"summary validation flag is not true: {name}")

    if range_match:
        start_id = int(range_match.group("start"))
        end_id = int(range_match.group("end"))
        generated_min = summary.get("generatedIdMin")
        generated_max = summary.get("generatedIdMax")
        if generated_min is None and generated_max is None:
            pass
        elif not isinstance(generated_min, int) or not isinstance(generated_max, int):
            errors.append("summary must include integer generatedIdMin/generatedIdMax")
        elif generated_min < start_id or generated_max > end_id:
            errors.append(f"generated IDs {generated_min}-{generated_max} outside seed range {start_id}-{end_id}")
    else:
        errors.append("missing seed_id_range comment")

    bounds = PROFILE_BOUNDS.get(profile)
    bound_components = {"airports": "reference", "airlines": "reference", "routes": "flights", "users": "users", "orders": "orders"}
    direct_scenarios = {
        "direct", "payment", "cancel", "refund", "change", "waitlist", "sold-out",
        "delayed", "near-departure",
    }
    for key, (minimum, maximum) in (bounds or {}).items():
        if bound_components.get(key) not in components:
            continue
        if key == "orders" and "direct" not in scenarios:
            continue
        if key in {"routes", "users"} and not scenarios.intersection(direct_scenarios):
            continue
        value = summary.get(key)
        if value is None or not minimum <= value <= maximum:
            errors.append(f"{profile}.{key}={value} outside expected range {minimum}-{maximum}")

    if "orders" in components:
        required_order_statuses = set()
        if "connecting" in scenarios and summary.get("connectingOrders", 0) > 0:
            required_order_statuses.update({"PENDING_PAYMENT", "ISSUED", "CANCELLED", "REFUNDED", "CHANGED"})
        if summary.get("orders", 0) > 0 and scenarios.intersection({"direct", "sold-out", "waitlist"}):
            required_order_statuses.add("ISSUED")
        if summary.get("orders", 0) > 0 and "payment" in scenarios:
            required_order_statuses.add("PENDING_PAYMENT")
        if summary.get("orders", 0) > 0 and "cancel" in scenarios:
            required_order_statuses.add("CANCELLED")
        if summary.get("orders", 0) > 0 and "refunds" in components and "refund" in scenarios:
            required_order_statuses.add("REFUNDED")
        if summary.get("orders", 0) > 0 and "changes" in components and "change" in scenarios:
            required_order_statuses.update({"CHANGE_PENDING", "CHANGED"})
        for status in sorted(required_order_statuses):
            if count_literal(sql, status) == 0:
                errors.append(f"missing order status literal {status}")
    if "flights" in components:
        required_seat_statuses = {"AVAILABLE"}
        if summary.get("orders", 0) > 0 and ("payment" in scenarios or "connecting" in scenarios):
            required_seat_statuses.add("LOCKED")
        if summary.get("orders", 0) > 0 and scenarios.intersection({"direct", "sold-out", "waitlist", "change", "connecting"}):
            required_seat_statuses.add("SOLD")
        for status in sorted(required_seat_statuses):
            if count_literal(sql, status) == 0:
                errors.append(f"missing seat status literal {status}")
    if "waitlists" in components and summary.get("waitlists", 0) > 0:
        for status in REQUIRED_WAITLIST_STATUSES:
            if count_literal(sql, status) == 0:
                errors.append(f"missing waitlist status literal {status}")
    return summary, errors


DATABASE_CHECKS = {
    "flight_remaining_matches_available_seats": """
        SELECT COUNT(*) FROM flight f
        WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id)
          AND f.remaining_seats <> (SELECT COUNT(*) FROM flight_seat s WHERE s.flight_id=f.id AND s.status='AVAILABLE')
    """,
    "flight_cabin_totals_match": """
        SELECT COUNT(*) FROM flight f
        WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='{batch}' AND o.table_name='flight' AND o.row_id=f.id)
          AND f.total_seats <> (SELECT COALESCE(SUM(c.total_seats),0) FROM flight_cabin c WHERE c.flight_id=f.id)
    """,
    "seat_prices_match_cabin": """
        SELECT COUNT(*) FROM flight_seat s JOIN flight_cabin c ON c.flight_id=s.flight_id AND c.cabin_class=s.cabin_class
        WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='{batch}' AND o.table_name='flight_seat' AND o.row_id=s.id)
          AND s.price <> c.price
    """,
    "sold_seats_have_snapshot": """
        SELECT COUNT(*) FROM flight_seat s
        LEFT JOIN order_passenger op ON op.seat_id=s.id
        LEFT JOIN order_segment_passenger osp ON osp.seat_id=s.id
        WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='{batch}' AND o.table_name='flight_seat' AND o.row_id=s.id)
          AND s.status='SOLD' AND op.id IS NULL AND osp.id IS NULL
    """,
    "locked_seats_have_order_and_expiry": """
        SELECT COUNT(*) FROM flight_seat s
        WHERE EXISTS (SELECT 1 FROM test_data_ownership o WHERE o.batch_key='{batch}' AND o.table_name='flight_seat' AND o.row_id=s.id)
          AND s.status='LOCKED'
          AND (s.locked_by_order_id IS NULL OR s.lock_expire_time IS NULL)
    """,
    "connecting_orders_have_two_segments": """
        SELECT COUNT(*) FROM ticket_order o
        WHERE EXISTS (SELECT 1 FROM test_data_ownership own WHERE own.batch_key='{batch}' AND own.table_name='ticket_order' AND own.row_id=o.id)
          AND o.journey_type='CONNECTING'
          AND (SELECT COUNT(*) FROM ticket_order_segment s WHERE s.order_id=o.id) <> 2
    """,
    "connecting_passengers_match": """
        SELECT COUNT(*) FROM ticket_order o
        WHERE EXISTS (SELECT 1 FROM test_data_ownership own WHERE own.batch_key='{batch}' AND own.table_name='ticket_order' AND own.row_id=o.id)
          AND o.journey_type='CONNECTING'
          AND (SELECT GROUP_CONCAT(DISTINCT p.passenger_id ORDER BY p.passenger_id)
               FROM order_segment_passenger p JOIN ticket_order_segment s ON s.id=p.order_segment_id
               WHERE s.order_id=o.id AND s.segment_no=1)
            <> (SELECT GROUP_CONCAT(DISTINCT p.passenger_id ORDER BY p.passenger_id)
                FROM order_segment_passenger p JOIN ticket_order_segment s ON s.id=p.order_segment_id
                WHERE s.order_id=o.id AND s.segment_no=2)
    """,
    "connecting_change_snapshots_complete": """
        SELECT COUNT(*) FROM connecting_change_record c
        WHERE EXISTS (SELECT 1 FROM test_data_ownership own WHERE own.batch_key='{batch}' AND own.table_name='connecting_change_record' AND own.row_id=c.id)
          AND (SELECT COUNT(*) FROM connecting_change_segment s WHERE s.change_record_id=c.id) <> 4
    """,
}


def database_metadata_query(summary: dict) -> str:
    profile = summary.get("profile")
    batch = summary.get("batchKey")
    seed = summary.get("seed")
    source_ref = summary.get("sourceRef")
    if profile not in {"dev", "test", "perf"} or not isinstance(batch, str) or not isinstance(seed, int) or isinstance(seed, bool):
        raise ValueError("invalid summary metadata")
    if not re.fullmatch(r"skybooker:(dev|test|perf)", batch) or batch != f"skybooker:{profile}":
        raise ValueError("invalid summary batchKey")
    if source_ref is None:
        source_expression = "b.source_ref <=> NULL"
    elif isinstance(source_ref, str) and re.fullmatch(r"[0-9a-fA-F]{40}", source_ref):
        source_expression = "b.source_ref = '" + source_ref + "'"
    else:
        raise ValueError("invalid summary sourceRef")
    return f"""
        SELECT CASE WHEN EXISTS (
            SELECT 1 FROM test_data_batch b
            WHERE b.batch_key='{batch}' AND b.profile='{profile}' AND b.seed={seed}
              AND {source_expression}
        ) THEN 0 ELSE 1 END
    """


def database_validate(summary: dict, args: argparse.Namespace) -> list[str]:
    batch = summary.get("batchKey")
    if not isinstance(batch, str) or not re.fullmatch(r"skybooker:(dev|test|perf)", batch):
        return ["summary batchKey is missing or invalid"]
    env = os.environ.copy()
    password = env.get("MYSQL_PASSWORD")
    if password is not None:
        env["MYSQL_PWD"] = password
    command = [
        args.mysql,
        "--protocol=tcp", "-h", args.host, "-P", str(args.port), "-u", args.user,
        "-D", args.database_name, "--batch", "--skip-column-names", "--raw",
    ]
    errors: list[str] = []
    try:
        checks = [("batch_metadata_mismatch", database_metadata_query(summary))]
    except ValueError as exc:
        return [f"database metadata check cannot be built: {exc}"]
    checks.extend(DATABASE_CHECKS.items())
    for name, template in checks:
        query = template.format(batch=batch)
        result = subprocess.run(command, input=query, text=True, capture_output=True, env=env)
        if result.returncode != 0:
            errors.append(f"database check {name} failed to execute: {result.stderr.strip()}")
        elif result.stdout.strip() not in {"0", "0\n"}:
            errors.append(f"database check {name} returned {result.stdout.strip()}")
    return errors


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate generated SkyBooker test data SQL.")
    parser.add_argument("--file", help="Path to seed SQL file.")
    parser.add_argument("--database", action="store_true", help="Run consistency checks against MySQL.")
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("MYSQL_USER", "root"))
    parser.add_argument("--database-name", dest="database_name", default=os.getenv("MYSQL_DB", "flight_booking"))
    parser.add_argument("--mysql", default="mysql")
    args = parser.parse_args()
    if not args.file and not args.database:
        parser.error("one of --file or --database is required")

    summary: dict = {}
    errors: list[str] = []
    if args.file:
        try:
            sql = Path(args.file).read_text(encoding="utf-8")
            summary, errors = validate(sql)
        except Exception as exc:
            print(f"Validation failed: {exc}", file=sys.stderr)
            sys.exit(1)
        print(json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True))
    if args.database:
        if not summary:
            print("--database requires --file so the ownership batch can be checked", file=sys.stderr)
            sys.exit(2)
        errors.extend(database_validate(summary, args))
    if errors:
        print("\nErrors:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        sys.exit(1)
    if args.file:
        print(f"\nOK: {args.file}")
    if args.database:
        print("OK: database consistency checks")


if __name__ == "__main__":
    main()
