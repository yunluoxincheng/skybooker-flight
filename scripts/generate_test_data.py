#!/usr/bin/env python3
"""Generate reproducible SkyBooker test seed SQL.

Reference catalogs live in scripts/data and are loaded deterministically. The
generator then creates flights, seats, orders, refunds, changes, waitlists and
AI records from deterministic rules. It does not call external APIs at runtime.
"""

from __future__ import annotations

import argparse
import json
import random
import re
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any


USER_PASSWORD_HASH = "$2b$12$mxGK3588bVIlwCYgjrqa1.1esZ8vbAKALvroPmpAvJfGt3VO781oy"
AIRPORT_FEE = Decimal("50.00")
FUEL_FEE = Decimal("30.00")
SERVICE_FEE = Decimal("0.00")

COMPONENT_NAMES = (
    "reference", "users", "flights", "orders", "refunds", "changes", "waitlists", "ai", "all"
)
SCENARIO_NAMES = (
    "direct", "connecting", "payment", "cancel", "refund", "change", "waitlist", "sold-out",
    "delayed", "near-departure", "all"
)

COMPONENT_DEPENDENCIES = {
    "reference": set(),
    "users": {"reference"},
    "flights": {"reference"},
    "orders": {"users", "flights"},
    "refunds": {"orders"},
    "changes": {"orders", "flights"},
    "waitlists": {"users", "flights", "orders"},
    "ai": {"users", "flights"},
}

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


@dataclass(frozen=True)
class Airport:
    id: int
    code: str
    name: str
    city: str
    province: str
    country_or_region: str
    scope: str


@dataclass(frozen=True)
class Airline:
    id: int
    code: str
    name: str


@dataclass(frozen=True)
class SqlExpression:
    value: str


@dataclass(frozen=True)
class ProfileConfig:
    flight_airport_count: int
    airline_count: int
    route_count: int
    days: int
    user_count: int
    order_count: int
    id_base: int
    id_range: int
    popular_routes: int
    medium_routes: int
    popular_daily: int
    medium_daily: int
    cold_frequency_days: int


@dataclass
class Seat:
    id: int
    flight_id: int
    seat_no: str
    cabin_class: str
    seat_type: str
    price: Decimal
    status: str = "AVAILABLE"
    locked_by_order_id: int | None = None
    lock_expire_time: datetime | None = None
    reserved_history: bool = False


@dataclass
class Flight:
    id: int
    flight_no: str
    airline_id: int
    departure_airport_id: int
    arrival_airport_id: int
    departure_time: datetime
    arrival_time: datetime
    duration_minutes: int
    base_price: Decimal
    status: str
    publish_status: str
    direct_flag: int
    baggage_allowance: str
    punctuality_rate: Decimal
    tag: str | None = None
    tier: str = "cold"
    cabins: list[dict[str, Any]] = field(default_factory=list)
    seats: list[Seat] = field(default_factory=list)

    @property
    def total_seats(self) -> int:
        return len(self.seats)

    @property
    def remaining_seats(self) -> int:
        return sum(1 for seat in self.seats if seat.status == "AVAILABLE")


class Ids:
    def __init__(self, base: int, id_range: int) -> None:
        self.base = base
        if id_range <= 100_000:
            self.user = base + 100
            self.passenger = base + 1_000
            self.flight = base + 10_000
            self.seat = base + 20_000
            self.cabin = base + 30_000
            self.order = base + 50_000
            self.order_passenger = base + 60_000
            self.refund = base + 70_000
            self.change = base + 71_000
            self.waitlist = base + 72_000
            self.waitlist_passenger = base + 73_000
            self.ai_session = base + 74_000
            self.ai_message = base + 74_100
            self.ai_recommendation = base + 74_200
            self.connecting_itinerary = base + 75_000
            self.order_segment = base + 76_000
            self.segment_passenger = base + 77_000
            self.connecting_change = base + 78_000
            self.connecting_change_segment = base + 79_000
        else:
            self.user = base + 100
            self.passenger = base + 1_000
            self.flight = base + 10_000
            self.seat = base + 100_000
            self.cabin = base + 300_000
            self.order = base + 500_000
            self.order_passenger = base + 700_000
            self.refund = base + 800_000
            self.change = base + 820_000
            self.waitlist = base + 840_000
            self.waitlist_passenger = base + 860_000
            self.ai_session = base + 880_000
            self.ai_message = base + 890_000
            self.ai_recommendation = base + 900_000
            self.connecting_itinerary = base + 910_000
            self.order_segment = base + 920_000
            self.segment_passenger = base + 930_000
            self.connecting_change = base + 940_000
            self.connecting_change_segment = base + 950_000

    def next_user(self) -> int:
        value = self.user
        self.user += 1
        return value

    def next_passenger(self) -> int:
        value = self.passenger
        self.passenger += 1
        return value

    def next_flight(self) -> int:
        value = self.flight
        self.flight += 1
        return value

    def next_seat(self) -> int:
        value = self.seat
        self.seat += 1
        return value

    def next_cabin(self) -> int:
        value = self.cabin
        self.cabin += 1
        return value

    def next_order(self) -> int:
        value = self.order
        self.order += 1
        return value

    def next_order_passenger(self) -> int:
        value = self.order_passenger
        self.order_passenger += 1
        return value

    def next_refund(self) -> int:
        value = self.refund
        self.refund += 1
        return value

    def next_change(self) -> int:
        value = self.change
        self.change += 1
        return value

    def next_waitlist(self) -> int:
        value = self.waitlist
        self.waitlist += 1
        return value

    def next_waitlist_passenger(self) -> int:
        value = self.waitlist_passenger
        self.waitlist_passenger += 1
        return value

    def next_ai_session(self) -> int:
        value = self.ai_session
        self.ai_session += 1
        return value

    def next_ai_message(self) -> int:
        value = self.ai_message
        self.ai_message += 1
        return value

    def next_ai_recommendation(self) -> int:
        value = self.ai_recommendation
        self.ai_recommendation += 1
        return value

    def next_connecting_itinerary(self) -> int:
        value = self.connecting_itinerary
        self.connecting_itinerary += 1
        return value

    def next_order_segment(self) -> int:
        value = self.order_segment
        self.order_segment += 1
        return value

    def next_segment_passenger(self) -> int:
        value = self.segment_passenger
        self.segment_passenger += 1
        return value

    def next_connecting_change(self) -> int:
        value = self.connecting_change
        self.connecting_change += 1
        return value

    def next_connecting_change_segment(self) -> int:
        value = self.connecting_change_segment
        self.connecting_change_segment += 1
        return value


PROFILES = {
    "dev": ProfileConfig(
        flight_airport_count=24,
        airline_count=13,
        route_count=90,
        days=7,
        user_count=32,
        order_count=120,
        id_base=100_000,
        id_range=99_999,
        popular_routes=6,
        medium_routes=12,
        popular_daily=4,
        medium_daily=1,
        cold_frequency_days=7,
    ),
    "test": ProfileConfig(
        flight_airport_count=300,
        airline_count=24,
        route_count=320,
        days=30,
        user_count=240,
        order_count=2_200,
        id_base=200_000,
        id_range=999_999,
        popular_routes=8,
        medium_routes=24,
        popular_daily=4,
        medium_daily=1,
        cold_frequency_days=15,
    ),
    "perf": ProfileConfig(
        flight_airport_count=300,
        airline_count=36,
        route_count=1_200,
        days=90,
        user_count=5_000,
        order_count=50_000,
        id_base=2_000_000,
        id_range=9_999_999,
        popular_routes=30,
        medium_routes=100,
        popular_daily=5,
        medium_daily=1,
        cold_frequency_days=30,
    ),
}


DATA_DIR = Path(__file__).resolve().parent / "data"


def _load_json_catalog(filename: str, key: str) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    path = DATA_DIR / filename
    if not path.is_file():
        raise RuntimeError(f"missing catalog file: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or not isinstance(payload.get(key), list):
        raise RuntimeError(f"invalid catalog format: {path}")
    metadata = payload.get("metadata")
    if not isinstance(metadata, dict):
        raise RuntimeError(f"catalog metadata is missing: {path}")
    return payload[key], metadata


def _load_airports() -> tuple[list[Airport], dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    metadata: dict[str, Any] = {}
    for filename in ("airports-cn.json", "airports-international.json"):
        catalog_rows, catalog_metadata = _load_json_catalog(filename, "airports")
        rows.extend(catalog_rows)
        metadata[filename] = catalog_metadata
    airports = [
        Airport(
            id=int(row["id"]),
            code=str(row["code"]),
            name=str(row["name"]),
            city=str(row["city"]),
            province=str(row["province"]),
            country_or_region=str(row["countryOrRegion"]),
            scope=str(row["scope"]),
        )
        for row in rows
    ]
    codes = [airport.code for airport in airports]
    ids = [airport.id for airport in airports]
    if len(codes) != len(set(codes)):
        raise RuntimeError("airport catalog contains duplicate IATA codes")
    if len(ids) != len(set(ids)):
        raise RuntimeError("airport catalog contains duplicate IDs")
    if any(not re.fullmatch(r"[A-Z]{3}", code) for code in codes):
        raise RuntimeError("airport catalog contains an invalid IATA code")
    if any(airport.scope not in {"mainland", "special_region", "international"} for airport in airports):
        raise RuntimeError("airport catalog contains an invalid scope")
    metadata["airportReferenceCount"] = len(airports)
    metadata["domesticAirportCount"] = sum(airport.scope == "mainland" for airport in airports)
    metadata["specialRegionAirportCount"] = sum(airport.scope == "special_region" for airport in airports)
    metadata["internationalAirportCount"] = sum(airport.scope == "international" for airport in airports)
    return airports, metadata


def _load_airlines() -> tuple[list[Airline], dict[str, Any]]:
    rows, metadata = _load_json_catalog("airlines.json", "airlines")
    airlines = [
        Airline(id=int(row["id"]), code=str(row["code"]), name=str(row["name"]))
        for row in rows
    ]
    codes = [airline.code for airline in airlines]
    ids = [airline.id for airline in airlines]
    if len(codes) != len(set(codes)) or len(ids) != len(set(ids)):
        raise RuntimeError("airline catalog contains duplicate codes or IDs")
    if any(not re.fullmatch(r"[A-Z0-9]{2}", code) for code in codes):
        raise RuntimeError("airline catalog contains an invalid IATA code")
    metadata["airlineReferenceCount"] = len(airlines)
    return airlines, metadata


AIRPORTS, AIRPORT_CATALOG_METADATA = _load_airports()
AIRLINES, AIRLINE_CATALOG_METADATA = _load_airlines()


FORCED_ROUTES = [
    ("CAN", "PVG"),
    ("CAN", "SHA"),
    ("SZX", "PVG"),
    ("SHA", "PEK"),
    ("PVG", "PKX"),
    ("PEK", "SHA"),
    ("PKX", "PVG"),
    ("SZX", "HGH"),
    ("PEK", "CAN"),
    ("HKG", "SIN"),
    ("PVG", "SIN"),
    ("PVG", "HND"),
    ("ICN", "PVG"),
    ("BKK", "CAN"),
    ("XMN", "HKG"),
    ("TFU", "SHA"),
]

FLIGHT_AIRPORT_PRIORITY = (
    "PEK", "PKX", "PVG", "SHA", "CAN", "SZX", "CTU", "TFU", "CKG", "HGH", "NKG", "WUH",
    "XIY", "KMG", "XMN", "HAK", "SYX", "TAO", "CSX", "CGO", "TSN", "SHE", "DLC", "HRB",
    "HKG", "SIN", "HND", "ICN", "BKK", "KUL", "SGN", "HAN", "MNL", "SYD", "MEL", "LAX",
    "SFO", "JFK", "LHR", "CDG", "FRA", "DXB", "DOH", "AMS", "AUH", "YVR", "YYZ",
)

BIDIRECTIONAL_HUB_PAIRS = (
    ("PEK", "PVG"), ("PEK", "CAN"), ("PVG", "CAN"), ("SZX", "PVG"),
    ("CTU", "PVG"), ("CKG", "PEK"), ("HGH", "PVG"), ("XIY", "PEK"),
    ("KMG", "CAN"), ("XMN", "CAN"),
)

INTERNATIONAL_GATEWAY_PAIRS = (
    ("HKG", "CAN"), ("HKG", "PVG"), ("SIN", "PVG"), ("HND", "PVG"),
    ("NRT", "NKG"), ("ICN", "PEK"), ("BKK", "CAN"), ("KUL", "SZX"),
    ("SGN", "SZX"), ("HAN", "CAN"), ("MNL", "SZX"), ("SYD", "PVG"),
    ("MEL", "CAN"), ("LAX", "PEK"), ("SFO", "PVG"), ("JFK", "PEK"),
    ("LHR", "PEK"), ("CDG", "PVG"), ("FRA", "PEK"), ("DXB", "CAN"),
    ("DOH", "PVG"), ("AMS", "PEK"), ("AUH", "PVG"), ("YVR", "CAN"),
    ("YYZ", "PEK"),
)

def money(value: Decimal | int | float | str) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def round_to_10(value: Decimal) -> Decimal:
    return (value / Decimal("10")).quantize(Decimal("1"), rounding=ROUND_HALF_UP) * Decimal("10")


def sql_string(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_value(value: Any) -> str:
    if isinstance(value, SqlExpression):
        return value.value
    if value is None:
        return "NULL"
    if isinstance(value, Decimal):
        return f"{value:.2f}"
    if isinstance(value, datetime):
        return sql_string(value.strftime("%Y-%m-%d %H:%M:%S"))
    if isinstance(value, date):
        return sql_string(value.isoformat())
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return f"{value:.2f}"
    return sql_string(str(value))


def chunked(items: list[Any], size: int) -> list[list[Any]]:
    return [items[i : i + size] for i in range(0, len(items), size)]


def insert_statement(table: str, columns: list[str], rows: list[tuple[Any, ...]], chunk_size: int = 500) -> list[str]:
    statements: list[str] = []
    if not rows:
        return statements
    column_sql = ", ".join(columns)
    for chunk in chunked(rows, chunk_size):
        value_sql = []
        for row in chunk:
            value_sql.append("(" + ", ".join(sql_value(value) for value in row) + ")")
        statements.append(f"INSERT INTO {table}({column_sql}) VALUES\n" + ",\n".join(value_sql) + ";")
    return statements


def upsert_statement(
    table: str,
    columns: list[str],
    rows: list[tuple[Any, ...]],
    update_columns: list[str],
    chunk_size: int = 200,
) -> list[str]:
    statements = insert_statement(table, columns, rows, chunk_size)
    if not statements:
        return []
    suffix = ", ".join(f"{column} = VALUES({column})" for column in update_columns)
    return [statement[:-1] + f"\nON DUPLICATE KEY UPDATE {suffix};" for statement in statements]


def parse_base_date(seed: int, explicit: str | None) -> date:
    if explicit:
        return date.fromisoformat(explicit)
    seed_text = str(seed)
    if re.fullmatch(r"20\d{6}", seed_text):
        return date.fromisoformat(f"{seed_text[:4]}-{seed_text[4:6]}-{seed_text[6:8]}")
    return date.today()


def selected_airports(cfg: ProfileConfig) -> list[Airport]:
    # Every profile imports the complete reference catalog. The profile size
    # only controls which airports receive generated flight traffic.
    return list(AIRPORTS)


def selected_flight_airports(cfg: ProfileConfig) -> list[Airport]:
    by_code = {airport.code: airport for airport in AIRPORTS}
    ordered: list[Airport] = []
    seen: set[str] = set()
    for code in FLIGHT_AIRPORT_PRIORITY:
        airport = by_code.get(code)
        if airport is not None:
            ordered.append(airport)
            seen.add(code)
    ordered.extend(airport for airport in AIRPORTS if airport.code not in seen)
    return ordered[: min(cfg.flight_airport_count, len(ordered))]


def selected_airlines(cfg: ProfileConfig) -> list[Airline]:
    return AIRLINES[: min(cfg.airline_count, len(AIRLINES))]


def estimate_duration(dep: Airport, arr: Airport, rng: random.Random) -> int:
    if dep.scope == "international" or arr.scope == "international":
        return rng.randint(520, 780)
    if dep.scope != "mainland" or arr.scope != "mainland":
        return rng.randint(180, 360)
    if dep.province == arr.province:
        return rng.randint(70, 120)
    if {dep.city, arr.city} & {"乌鲁木齐", "喀什", "拉萨", "三亚", "哈尔滨"}:
        return rng.randint(190, 330)
    return rng.randint(95, 210)


def route_base_price(duration_minutes: int, tier: str, day_offset: int, scarcity: str | None = None) -> Decimal:
    multiplier = {"popular": Decimal("3.80"), "medium": Decimal("3.35"), "cold": Decimal("3.10")}[tier]
    demand = {"popular": Decimal("170"), "medium": Decimal("90"), "cold": Decimal("40")}[tier]
    date_factor = Decimal(day_offset % 7) * Decimal("18")
    price = Decimal(duration_minutes) * multiplier + demand + date_factor
    if scarcity == "last_one":
        price *= Decimal("1.28")
    elif scarcity == "sold_out":
        price *= Decimal("1.20")
    elif scarcity == "cheap":
        price *= Decimal("0.82")
    return money(max(Decimal("280"), round_to_10(price)))


def build_routes(airports: list[Airport], cfg: ProfileConfig, rng: random.Random) -> list[tuple[str, str, str]]:
    by_code = {airport.code: airport for airport in airports}
    routes: list[tuple[str, str]] = []
    seen: set[tuple[str, str]] = set()

    def add_route(dep: str, arr: str) -> None:
        if dep in by_code and arr in by_code and dep != arr and (dep, arr) not in seen:
            seen.add((dep, arr))
            routes.append((dep, arr))

    # First build a directed cycle so every airport used by the profile has
    # at least one outbound and one inbound route.
    for index, airport in enumerate(airports):
        add_route(airport.code, airports[(index + 1) % len(airports)].code)

    for dep, arr in FORCED_ROUTES:
        add_route(dep, arr)
    for dep, arr in BIDIRECTIONAL_HUB_PAIRS + INTERNATIONAL_GATEWAY_PAIRS:
        add_route(dep, arr)
        add_route(arr, dep)
    mainland_hubs = [
        airport.code for airport in airports
        if airport.scope == "mainland" and airport.code in {"PEK", "PVG", "CAN", "SZX", "CTU"}
    ]
    if mainland_hubs:
        for airport in airports:
            if airport.scope != "mainland":
                hub = mainland_hubs[airport.id % len(mainland_hubs)]
                add_route(airport.code, hub)
                add_route(hub, airport.code)

    candidates = [
        (dep.code, arr.code)
        for dep in airports
        for arr in airports
        if dep.code != arr.code and dep.city != arr.city
    ]
    rng.shuffle(candidates)
    target_count = max(cfg.route_count, len(airports))
    for dep, arr in candidates:
        if len(routes) >= target_count:
            break
        add_route(dep, arr)

    result: list[tuple[str, str, str]] = []
    for index, (dep, arr) in enumerate(routes):
        if index < cfg.popular_routes:
            tier = "popular"
        elif index < cfg.popular_routes + cfg.medium_routes:
            tier = "medium"
        else:
            tier = "cold"
        result.append((dep, arr, tier))
    return result


def cabin_layout(tag: str | None, rng: random.Random) -> list[tuple[str, int]]:
    if tag and tag.startswith("connecting-"):
        return [("ECONOMY", 6)]
    if tag in {"all_sold_out", "last_seat", "economy_sold_business_available", "waitlist_target"}:
        return [("BUSINESS", 4), ("ECONOMY", 18)]
    if tag == "multi_locked":
        return [("BUSINESS", 4), ("ECONOMY", 24)]
    choice = rng.choice(["compact", "standard", "wide"])
    if choice == "wide":
        return [("FIRST", 4), ("BUSINESS", 8), ("ECONOMY", 42)]
    if choice == "standard":
        return [("BUSINESS", 8), ("ECONOMY", 36)]
    return [("BUSINESS", 4), ("ECONOMY", 30)]


def cabin_price(base_price: Decimal, cabin: str) -> Decimal:
    if cabin == "FIRST":
        return money(round_to_10(base_price * Decimal("3.40")))
    if cabin == "BUSINESS":
        return money(round_to_10(base_price * Decimal("2.10")))
    return base_price


def seat_codes_for_cabin(cabin: str) -> list[str]:
    if cabin == "FIRST":
        return ["A", "C", "D", "F"]
    if cabin == "BUSINESS":
        return ["A", "C", "D", "F"]
    return ["A", "B", "C", "D", "E", "F"]


def seat_type(seat_code: str) -> str:
    if seat_code in {"A", "F"}:
        return "WINDOW"
    if seat_code in {"C", "D"}:
        return "AISLE"
    return "NORMAL"


def add_seats(flight: Flight, ids: Ids, rng: random.Random) -> None:
    next_row = {"FIRST": 1, "BUSINESS": 2, "ECONOMY": 10}
    disabled_budget = 1 if rng.random() < 0.12 and flight.tag not in {"all_sold_out", "last_seat"} else 0
    for cabin, total in cabin_layout(flight.tag, rng):
        price = cabin_price(flight.base_price, cabin)
        codes = seat_codes_for_cabin(cabin)
        created = 0
        while created < total:
            row_no = next_row[cabin]
            next_row[cabin] += 1
            for code in codes:
                if created >= total:
                    break
                seat = Seat(
                    id=ids.next_seat(),
                    flight_id=flight.id,
                    seat_no=f"{row_no}{code}",
                    cabin_class=cabin,
                    seat_type=seat_type(code),
                    price=price,
                )
                if disabled_budget > 0 and cabin == "ECONOMY" and created == min(5, total - 1):
                    seat.status = "DISABLED"
                    disabled_budget -= 1
                flight.seats.append(seat)
                created += 1
        flight.cabins.append(
            {
                "id": ids.next_cabin(),
                "flight_id": flight.id,
                "cabin_class": cabin,
                "price": price,
                "total_seats": total,
            }
        )


def make_flight(
    ids: Ids,
    rng: random.Random,
    airports_by_code: dict[str, Airport],
    airlines: list[Airline],
    dep_code: str,
    arr_code: str,
    depart_at: datetime,
    tier: str,
    tag: str | None = None,
    status: str | None = None,
    direct_flag: int | None = None,
    scarcity: str | None = None,
    publish_status: str = "PUBLISHED",
    duration_override: int | None = None,
) -> Flight:
    dep = airports_by_code[dep_code]
    arr = airports_by_code[arr_code]
    duration = duration_override or estimate_duration(dep, arr, rng)
    if tag == "cross_day":
        duration = max(duration, 170)
    arrival_at = depart_at + timedelta(minutes=duration)
    airline = rng.choice(airlines)
    flight_id = ids.next_flight()
    number_seed = (flight_id * 17 + depart_at.hour * 31 + depart_at.minute) % 9000 + 1000
    flight_no = f"{airline.code}{number_seed}"
    resolved_status = status or ("DELAYED" if rng.random() < 0.04 else "ON_TIME")
    flight = Flight(
        id=flight_id,
        flight_no=flight_no,
        airline_id=airline.id,
        departure_airport_id=dep.id,
        arrival_airport_id=arr.id,
        departure_time=depart_at,
        arrival_time=arrival_at,
        duration_minutes=duration,
        base_price=route_base_price(duration, tier, depart_at.toordinal() % 7, scarcity),
        status=resolved_status,
        publish_status=publish_status,
        direct_flag=direct_flag if direct_flag is not None else (0 if rng.random() < 0.08 else 1),
        baggage_allowance="20kg 托运行李",
        punctuality_rate=money(Decimal(rng.randint(8850, 9850)) / Decimal("100")),
        tag=tag,
        tier=tier,
    )
    add_seats(flight, ids, rng)
    return flight


def generate_flights(
    ids: Ids,
    rng: random.Random,
    cfg: ProfileConfig,
    base_date: date,
    airports: list[Airport],
    airlines: list[Airline],
    scenarios: set[str],
) -> list[Flight]:
    airports_by_code = {airport.code: airport for airport in airports}
    flights: list[Flight] = []

    special_specs = [
        ("direct", "CAN", "PVG", 1, time(7, 10), "popular", "cheap_morning", "ON_TIME", 1, "cheap"),
        ("direct", "CAN", "SHA", 1, time(9, 20), "popular", "morning", "ON_TIME", 1, None),
        ("delayed", "CAN", "PVG", 1, time(14, 30), "popular", "delayed", "DELAYED", 1, None),
        ("direct", "CAN", "SHA", 1, time(23, 40), "popular", "cross_day", "ON_TIME", 1, None),
        ("cancel", "CAN", "PVG", 1, time(11, 0), "popular", "cancelled", "CANCELLED", 1, None),
        ("near-departure", "CAN", "PVG", 0, time(10, 30), "popular", "soon", "ON_TIME", 1, None),
        ("sold-out", "SHA", "PEK", 2, time(8, 0), "popular", "economy_sold_business_available", "ON_TIME", 1, "sold_out"),
        ("sold-out", "PVG", "PKX", 2, time(12, 10), "popular", "all_sold_out", "ON_TIME", 1, "sold_out"),
        ("sold-out", "SZX", "HGH", 3, time(18, 35), "medium", "last_seat", "ON_TIME", 1, "last_one"),
        ("payment", "PEK", "SHA", 4, time(16, 50), "popular", "multi_locked", "ON_TIME", 1, None),
        ("direct", "HKG", "SIN", 5, time(22, 30), "medium", "non_direct", "ON_TIME", 0, None),
        ("waitlist", "CAN", "PVG", 2, time(20, 5), "popular", "waitlist_target", "ON_TIME", 1, "sold_out"),
    ]
    for scenario, dep, arr, day, depart_time, tier, tag, status, direct, scarcity in special_specs:
        if scenario not in scenarios:
            continue
        if dep in airports_by_code and arr in airports_by_code:
            flights.append(
                make_flight(
                    ids,
                    rng,
                    airports_by_code,
                    airlines,
                    dep,
                    arr,
                    datetime.combine(base_date + timedelta(days=day), depart_time),
                    tier,
                    tag=tag,
                    status=status,
                    direct_flag=direct,
                    scarcity=scarcity,
                )
            )

    if not scenarios.intersection({"direct", "payment", "cancel", "refund", "change", "waitlist", "sold-out", "delayed", "near-departure"}):
        return flights

    routes = build_routes(airports, cfg, rng)
    popular_slots = [time(6, 50), time(9, 25), time(13, 40), time(18, 15), time(22, 20)]
    medium_slots = [time(8, 35), time(16, 5)]
    cold_slots = [time(11, 20), time(19, 45)]
    for route_index, (dep, arr, tier) in enumerate(routes):
        for day in range(1, cfg.days + 1):
            if tier == "popular":
                slots = popular_slots[: cfg.popular_daily]
            elif tier == "medium":
                slots = medium_slots[: cfg.medium_daily]
            else:
                if day % cfg.cold_frequency_days != route_index % cfg.cold_frequency_days:
                    continue
                slots = [cold_slots[(route_index + day) % len(cold_slots)]]
            for slot in slots:
                minute_jitter = rng.choice([-10, -5, 0, 5, 10])
                depart_at = datetime.combine(base_date + timedelta(days=day), slot) + timedelta(minutes=minute_jitter)
                flights.append(
                    make_flight(
                        ids,
                        rng,
                        airports_by_code,
                        airlines,
                        dep,
                        arr,
                        depart_at,
                        tier,
                        status=None if "delayed" in scenarios else "ON_TIME",
                    )
                )

    flights.sort(key=lambda item: (item.departure_time, item.flight_no, item.id))
    return flights


def generate_connecting_itineraries(
    ids: Ids,
    rng: random.Random,
    base_date: date,
    airports: list[Airport],
    airlines: list[Airline],
) -> tuple[list[Flight], list[dict[str, Any]]]:
    """Create deterministic, managed two-segment itineraries for issue #148.

    The pair definitions deliberately exercise the business boundaries used by
    the itinerary service while keeping every daily seed row valid for MySQL.
    Negative combinations (broken airports and invalid transfer windows) remain
    test fixtures rather than production-like seed data.
    """
    airports_by_code = {airport.code: airport for airport in airports}
    specs = [
        ("published", "CAN", "SHA", "PEK", 1, time(8, 0), 120, 120, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("draft", "CAN", "SHA", "PEK", 2, time(8, 0), 120, 120, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("different-via", "CAN", "PVG", "PEK", 3, time(8, 0), 180, 120, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("minimum-window", "CAN", "SHA", "PEK", 4, time(8, 0), 120, 90, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("maximum-window", "CAN", "SHA", "PEK", 5, time(8, 0), 120, 360, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("cross-day", "CAN", "SHA", "PEK", 6, time(23, 0), 180, 90, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("first-tight", "CAN", "SHA", "PEK", 7, time(8, 0), 120, 120, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("second-tight", "CAN", "SHA", "PEK", 8, time(8, 0), 120, 120, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("sold-out", "CAN", "SHA", "PEK", 9, time(8, 0), 120, 120, "PUBLISHED", "PUBLISHED", "ON_TIME", "ON_TIME"),
        ("cancelled", "CAN", "SHA", "PEK", 10, time(8, 0), 120, 120, "PUBLISHED", "PUBLISHED", "CANCELLED", "ON_TIME"),
        ("unpublished", "CAN", "SHA", "PEK", 11, time(8, 0), 120, 120, "DRAFT", "PUBLISHED", "ON_TIME", "ON_TIME"),
    ]
    flights: list[Flight] = []
    itineraries: list[dict[str, Any]] = []
    for index, (tag, dep, via, arr, day, departure, first_duration, transfer_minutes,
                first_publish, second_publish, first_status, second_status) in enumerate(specs, start=1):
        if not {dep, via, arr}.issubset(airports_by_code):
            continue
        first = make_flight(
            ids, rng, airports_by_code, airlines, dep, via,
            datetime.combine(base_date + timedelta(days=day), departure),
            "popular", tag=f"connecting-{tag}-first", status=first_status,
            direct_flag=1, publish_status=first_publish, duration_override=first_duration,
        )
        second = make_flight(
            ids, rng, airports_by_code, airlines, via,
            arr, first.arrival_time + timedelta(minutes=transfer_minutes),
            "popular", tag=f"connecting-{tag}-second", status=second_status,
            direct_flag=1, publish_status=second_publish, duration_override=120 + (index % 3) * 30,
        )
        flights.extend((first, second))
        itineraries.append(
            {
                "id": ids.next_connecting_itinerary(),
                "first_flight_id": first.id,
                "second_flight_id": second.id,
                "publish_status": "PUBLISHED" if first_publish == second_publish == "PUBLISHED" and tag != "draft" else "DRAFT",
                "created_by": None,
                "created_at": datetime.combine(base_date, time(8, 0)) + timedelta(minutes=index),
                "updated_at": datetime.combine(base_date, time(8, 0)) + timedelta(minutes=index),
                "scenario": tag,
            }
        )
    return flights, itineraries


def generate_users_and_passengers(ids: Ids, profile: str, cfg: ProfileConfig) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    first_names = ["安", "博", "辰", "迪", "方", "佳", "可", "林", "明", "宁", "晴", "然", "森", "桐", "文", "熙", "雅", "远", "卓"]
    surnames = ["陈", "李", "王", "张", "刘", "赵", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林", "何"]
    users: list[dict[str, Any]] = []
    passengers: list[dict[str, Any]] = [
        {
            "id": 1,
            "user_id": 2,
            "name": "张三",
            "id_card_no": "110101199001010011",
            "passenger_type": "ADULT",
            "phone": "13800000000",
        }
    ]
    for index in range(1, cfg.user_count):
        user_id = ids.next_user()
        users.append(
            {
                "id": user_id,
                "email": f"seed+{profile}-{index:04d}@skybooker.test",
                "phone": f"139{cfg.id_base // 1000:03d}{index:05d}"[-11:],
                "password_hash": USER_PASSWORD_HASH,
                "nickname": f"测试用户{index:04d}",
                "role": "USER",
                "status": "NORMAL",
                "email_verified": 1,
                "phone_verified": 1 if index % 3 == 0 else 0,
                "last_login_at": None,
            }
        )
        passenger_count = 2 + (index % 3 == 0)
        for offset in range(passenger_count):
            passenger_id = ids.next_passenger()
            passenger_type = "CHILD" if offset == 2 else "ADULT"
            passengers.append(
                {
                    "id": passenger_id,
                    "user_id": user_id,
                    "name": f"{surnames[(index + offset) % len(surnames)]}{first_names[(index + offset * 2) % len(first_names)]}{first_names[(index + offset * 2 + 5) % len(first_names)]}",
                    "id_card_no": f"SB{profile.upper()}{index:04d}{offset:02d}19900101",
                    "passenger_type": passenger_type,
                    "phone": f"137{cfg.id_base // 1000:03d}{index:04d}{offset}"[-11:],
                }
            )
    return users, passengers


def passenger_pool_by_user(passengers: list[dict[str, Any]]) -> dict[int, list[dict[str, Any]]]:
    pool: dict[int, list[dict[str, Any]]] = {}
    for passenger in passengers:
        pool.setdefault(passenger["user_id"], []).append(passenger)
    return pool


def available_seats(flight: Flight, cabin: str | None = None, active: bool = True) -> list[Seat]:
    seats = [
        seat
        for seat in flight.seats
        if seat.status == "AVAILABLE" and (cabin is None or seat.cabin_class == cabin)
    ]
    if active:
        seats = [seat for seat in seats if not seat.reserved_history]
    return seats


def select_user_passengers(
    rng: random.Random,
    passengers_by_user: dict[int, list[dict[str, Any]]],
    count: int,
) -> tuple[int, list[dict[str, Any]]]:
    eligible = [user_id for user_id, items in passengers_by_user.items() if len(items) >= count]
    user_id = rng.choice(eligible)
    return user_id, rng.sample(passengers_by_user[user_id], count)


def order_amount(seats: list[Seat]) -> tuple[Decimal, Decimal, Decimal, Decimal, Decimal]:
    ticket_amount = money(sum((seat.price for seat in seats), Decimal("0.00")))
    passenger_count = len(seats)
    airport_fee = AIRPORT_FEE * passenger_count
    fuel_fee = FUEL_FEE * passenger_count
    total = money(ticket_amount + airport_fee + fuel_fee + SERVICE_FEE)
    return ticket_amount, airport_fee, fuel_fee, SERVICE_FEE, total


def create_order(
    ids: Ids,
    profile: str,
    sequence: int,
    user_id: int,
    flight: Flight,
    seats: list[Seat],
    passengers: list[dict[str, Any]],
    status: str,
    created_at: datetime,
    pay_time: datetime | None,
    expire_time: datetime | None,
    journey_type: str = "DIRECT",
    client_request_id: str | None = None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    ticket_amount, airport_fee, fuel_fee, service_fee, total_amount = order_amount(seats)
    order_id = ids.next_order()
    order = {
        "id": order_id,
        "order_no": f"TD{profile.upper()}{created_at:%Y%m%d}{sequence:08d}",
        "user_id": user_id,
        "flight_id": flight.id,
        "journey_type": journey_type,
        "client_request_id": client_request_id,
        "status": status,
        "ticket_amount": ticket_amount,
        "airport_fee": airport_fee,
        "fuel_fee": fuel_fee,
        "service_fee": service_fee,
        "total_amount": total_amount,
        "pay_time": pay_time,
        "expire_time": expire_time,
        "created_at": created_at,
        "updated_at": created_at,
    }
    order_passengers = []
    for seat, passenger in zip(seats, passengers):
        order_passengers.append(
            {
                "id": ids.next_order_passenger(),
                "order_id": order_id,
                "passenger_id": passenger["id"],
                "passenger_name": passenger["name"],
                "passenger_type": passenger["passenger_type"],
                "seat_id": seat.id,
                "seat_no": seat.seat_no,
                "ticket_price": seat.price,
                "created_at": created_at,
            }
        )
    return order, order_passengers


def mark_seats_for_order(seats: list[Seat], order_id: int, status: str, expire_time: datetime | None) -> None:
    if status == "PENDING_PAYMENT":
        for seat in seats:
            seat.status = "LOCKED"
            seat.locked_by_order_id = order_id
            seat.lock_expire_time = expire_time
    elif status in {"ISSUED", "CHANGE_PENDING", "CHANGED"}:
        for seat in seats:
            seat.status = "SOLD"
            seat.locked_by_order_id = order_id
            seat.lock_expire_time = None
    elif status in {"CANCELLED", "REFUNDED"}:
        for seat in seats:
            seat.reserved_history = True


def build_orders(
    ids: Ids,
    rng: random.Random,
    profile: str,
    cfg: ProfileConfig,
    base_dt: datetime,
    flights: list[Flight],
    passengers: list[dict[str, Any]],
    scenarios: set[str],
    include_refunds: bool = True,
    include_changes: bool = True,
    include_waitlists: bool = True,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    passengers_by_user = passenger_pool_by_user(passengers)
    orders: list[dict[str, Any]] = []
    order_passengers: list[dict[str, Any]] = []
    refunds: list[dict[str, Any]] = []
    changes: list[dict[str, Any]] = []
    waitlists: list[dict[str, Any]] = []
    waitlist_passengers: list[dict[str, Any]] = []
    order_sequence = 1

    def append_order(
        flight: Flight,
        seats: list[Seat],
        status: str,
        created_at: datetime,
        pay_time: datetime | None,
        expire_time: datetime | None,
        passengers_override: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        nonlocal order_sequence
        if passengers_override is None:
            user_id, selected_passengers = select_user_passengers(rng, passengers_by_user, len(seats))
        else:
            selected_passengers = passengers_override
            user_id = selected_passengers[0]["user_id"]
        order, ops = create_order(
            ids, profile, order_sequence, user_id, flight, seats, selected_passengers, status, created_at, pay_time, expire_time
        )
        order_sequence += 1
        orders.append(order)
        order_passengers.extend(ops)
        mark_seats_for_order(seats, order["id"], status, expire_time)
        return order

    def sell_many(flight: Flight, seats: list[Seat], label: str) -> None:
        chunks = chunked(seats, 2)
        for index, seat_chunk in enumerate(chunks):
            created_at = base_dt - timedelta(days=3 + index % 5, minutes=index)
            append_order(
                flight,
                seat_chunk,
                "ISSUED",
                created_at,
                created_at + timedelta(minutes=6),
                created_at + timedelta(minutes=15),
            )

    by_tag = {flight.tag: flight for flight in flights if flight.tag}

    economy_sold = by_tag.get("economy_sold_business_available") if "sold-out" in scenarios else None
    if economy_sold:
        sell_many(economy_sold, available_seats(economy_sold, "ECONOMY"), "economy-sold")

    all_sold = by_tag.get("all_sold_out") if "sold-out" in scenarios else None
    if all_sold:
        sell_many(all_sold, available_seats(all_sold), "all-sold")

    last_seat = by_tag.get("last_seat") if "sold-out" in scenarios else None
    if last_seat:
        seats = available_seats(last_seat)
        sell_many(last_seat, seats[:-1], "last-seat")

    multi_locked = by_tag.get("multi_locked") if "payment" in scenarios else None
    if multi_locked:
        seats = available_seats(multi_locked, "ECONOMY")[:2]
        created = base_dt - timedelta(minutes=5)
        append_order(multi_locked, seats, "PENDING_PAYMENT", created, None, base_dt + timedelta(minutes=10))

    waitlist_target = by_tag.get("waitlist_target") if "waitlist" in scenarios and include_waitlists else None
    if waitlist_target:
        economy = available_seats(waitlist_target, "ECONOMY")
        success_seat = economy[0:1]
        sell_many(waitlist_target, economy[1:], "waitlist-target-sold")
        created = base_dt - timedelta(days=1, hours=2)
        user_id, selected_passengers = select_user_passengers(rng, passengers_by_user, 1)
        success_order = append_order(
            waitlist_target,
            success_seat,
            "ISSUED",
            created,
            created + timedelta(minutes=8),
            created + timedelta(minutes=15),
            selected_passengers,
        )

        waitlist_specs = [
            ("PENDING_PAYMENT", 1, "ECONOMY", None, None, None, "未支付候补"),
            ("WAITING", 1, "ECONOMY", base_dt - timedelta(hours=5), None, None, "已支付排队中"),
            ("SUCCESS", 1, "ECONOMY", base_dt - timedelta(days=1, hours=3), success_order["id"], success_seat, "候补成功"),
            ("FAILED", 1, "ECONOMY", base_dt - timedelta(days=2), None, None, "航班取消候补失败"),
            ("CANCELLED", 1, "ECONOMY", None, None, None, "用户取消未支付候补"),
            ("REFUNDED", 1, "ECONOMY", base_dt - timedelta(days=2, hours=2), None, None, "已支付候补取消退款"),
            ("WAITING", 2, "ECONOMY", base_dt - timedelta(hours=4), None, None, "释放座位不足，需 2 个 ECONOMY 座位"),
            ("WAITING", 1, "BUSINESS", base_dt - timedelta(hours=3), None, None, "释放座位舱位 ECONOMY 与候补 BUSINESS 不一致"),
        ]
        for index, (status, count, cabin, paid_at, ticket_order_id, locked_seats, reason) in enumerate(waitlist_specs, start=1):
            user_id, selected = select_user_passengers(rng, passengers_by_user, count)
            pay_amount = order_amount([Seat(0, waitlist_target.id, "", cabin, "", cabin_price(waitlist_target.base_price, cabin)) for _ in range(count)])[4]
            waitlist_id = ids.next_waitlist()
            created_at = base_dt - timedelta(hours=8 + index)
            refund_amount = pay_amount if status == "REFUNDED" else None
            refund_time = base_dt - timedelta(hours=1) if status == "REFUNDED" else None
            waitlists.append(
                {
                    "id": waitlist_id,
                    "waitlist_no": f"WL{profile.upper()}{base_dt:%Y%m%d}{index:08d}",
                    "user_id": user_id,
                    "flight_id": waitlist_target.id,
                    "ticket_order_id": ticket_order_id,
                    "passenger_count": count,
                    "cabin_class": cabin,
                    "pay_amount": pay_amount,
                    "status": status,
                    "expire_time": created_at + timedelta(minutes=15),
                    "paid_at": paid_at,
                    "refund_amount": refund_amount,
                    "refund_time": refund_time,
                    "last_skip_reason": reason if status in {"WAITING", "FAILED"} else None,
                    "created_at": created_at,
                    "updated_at": created_at,
                }
            )
            for passenger_index, passenger in enumerate(selected):
                seat = locked_seats[passenger_index] if locked_seats else None
                waitlist_passengers.append(
                    {
                        "id": ids.next_waitlist_passenger(),
                        "waitlist_id": waitlist_id,
                        "passenger_id": passenger["id"],
                        "passenger_name": passenger["name"],
                        "passenger_type": passenger["passenger_type"],
                        "locked_seat_id": seat.id if seat else None,
                        "locked_seat_no": seat.seat_no if seat else None,
                        "created_at": created_at,
                        "updated_at": created_at,
                    }
                )

    status_cycle: list[str] = []
    if "direct" in scenarios:
        status_cycle.extend(["ISSUED", "ISSUED", "ISSUED"])
    if "payment" in scenarios:
        status_cycle.append("PENDING_PAYMENT")
    if "cancel" in scenarios:
        status_cycle.append("CANCELLED")
    if "refund" in scenarios and include_refunds:
        status_cycle.append("REFUNDED")
    if "change" in scenarios and include_changes:
        status_cycle.extend(["CHANGE_PENDING", "CHANGED"])
    protected_tags = {"all_sold_out", "last_seat", "economy_sold_business_available", "waitlist_target", "multi_locked"}
    sellable_flights = [
        flight
        for flight in flights
        if flight.status in {"ON_TIME", "DELAYED"}
        and flight.tag not in protected_tags
        and not (flight.tag and flight.tag.startswith("connecting-"))
    ]
    same_route: dict[tuple[int, int], list[Flight]] = {}
    for flight in sellable_flights:
        same_route.setdefault((flight.departure_airport_id, flight.arrival_airport_id), []).append(flight)

    if not status_cycle or not sellable_flights:
        return orders, order_passengers, refunds, changes, waitlists, waitlist_passengers

    attempts = 0
    while len(orders) < cfg.order_count and attempts < cfg.order_count * 20:
        attempts += 1
        status = status_cycle[len(orders) % len(status_cycle)]
        count = 2 if rng.random() < 0.18 else 1
        if rng.random() < 0.04:
            count = 3
        flight = rng.choice(sellable_flights)
        if status == "CHANGED" and include_changes:
            candidates = [item for item in same_route[(flight.departure_airport_id, flight.arrival_airport_id)] if item.id != flight.id]
            if not candidates:
                status = "ISSUED"
            else:
                new_flight = rng.choice(candidates)
                new_seats = available_seats(new_flight, active=True)[:count]
                old_seats = available_seats(flight, active=True)[:count]
                if len(new_seats) < count or len(old_seats) < count:
                    continue
                old_for_records = list(old_seats)
                for seat in old_for_records:
                    seat.reserved_history = True
                created = base_dt - timedelta(days=rng.randint(1, 12), minutes=rng.randint(0, 600))
                pay_time = created + timedelta(minutes=7)
                order = append_order(new_flight, new_seats, "CHANGED", created, pay_time, created + timedelta(minutes=15))
                for old_seat, new_seat in zip(old_for_records, new_seats):
                    changes.append(
                        {
                            "id": ids.next_change(),
                            "order_id": order["id"],
                            "old_flight_id": flight.id,
                            "new_flight_id": new_flight.id,
                            "old_seat_id": old_seat.id,
                            "new_seat_id": new_seat.id,
                            "price_diff": money(new_seat.price - old_seat.price),
                            "change_fee": money(order["total_amount"] * Decimal("0.10")),
                            "status": "SUCCESS",
                            "created_at": pay_time + timedelta(hours=1),
                        }
                    )
                continue

        seats = available_seats(flight, active=status not in {"CANCELLED", "REFUNDED"})[:count]
        if len(seats) < count:
            continue
        created = base_dt - timedelta(days=rng.randint(0, 18), minutes=rng.randint(0, 900))
        if status == "PENDING_PAYMENT":
            expire = base_dt - timedelta(minutes=20) if rng.random() < 0.35 else base_dt + timedelta(minutes=rng.randint(5, 45))
            order = append_order(flight, seats, status, created, None, expire)
        elif status == "CANCELLED":
            order = append_order(flight, seats, status, created, None, created + timedelta(minutes=15))
        else:
            pay_time = created + timedelta(minutes=6)
            order = append_order(flight, seats, status, created, pay_time, created + timedelta(minutes=15))
            if status == "REFUNDED" and include_refunds:
                fee = money(order["total_amount"] * Decimal("0.10"))
                refunds.append(
                    {
                        "id": ids.next_refund(),
                        "order_id": order["id"],
                        "user_id": order["user_id"],
                        "reason": "测试数据：计划变更退票",
                        "refund_amount": money(order["total_amount"] - fee),
                        "fee_amount": fee,
                        "status": "SUCCESS",
                        "created_at": pay_time + timedelta(hours=2),
                    }
                )

    return orders, order_passengers, refunds, changes, waitlists, waitlist_passengers


def build_connecting_orders(
    ids: Ids,
    profile: str,
    base_dt: datetime,
    itineraries: list[dict[str, Any]],
    flights: list[Flight],
    passengers: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    """Build valid two-segment order and change snapshots for managed itineraries."""
    flight_by_id = {flight.id: flight for flight in flights}
    passenger_by_user = passenger_pool_by_user(passengers)
    itinerary_by_scenario = {item["scenario"]: item for item in itineraries}
    connecting_orders: list[dict[str, Any]] = []
    segments: list[dict[str, Any]] = []
    segment_passengers: list[dict[str, Any]] = []
    refunds: list[dict[str, Any]] = []
    change_records: list[dict[str, Any]] = []
    change_segments: list[dict[str, Any]] = []

    def create_connecting_order(
        itinerary: dict[str, Any],
        status: str,
        passenger_count: int,
        sequence: int,
    ) -> tuple[dict[str, Any], list[dict[str, Any]], list[dict[str, Any]]]:
        first = flight_by_id[itinerary["first_flight_id"]]
        second = flight_by_id[itinerary["second_flight_id"]]
        first_seats = available_seats(first, "ECONOMY")[:passenger_count]
        second_seats = available_seats(second, "ECONOMY")[:passenger_count]
        if len(first_seats) < passenger_count or len(second_seats) < passenger_count:
            raise RuntimeError(f"connecting scenario {itinerary['scenario']} does not have enough seats")
        eligible = [user_id for user_id, items in passenger_by_user.items() if len(items) >= passenger_count]
        user_id = eligible[(sequence - 1) % len(eligible)]
        selected = passenger_by_user[user_id][:passenger_count]
        created_at = base_dt - timedelta(days=sequence, hours=sequence)
        all_seats = first_seats + second_seats
        ticket_amount = money(sum((seat.price for seat in all_seats), Decimal("0.00")))
        airport_fee = AIRPORT_FEE * passenger_count * 2
        fuel_fee = FUEL_FEE * passenger_count * 2
        total_amount = money(ticket_amount + airport_fee + fuel_fee + SERVICE_FEE)
        order_id = ids.next_order()
        order = {
            "id": order_id,
            "order_no": f"TC{profile.upper()}{base_dt:%Y%m%d}{sequence:08d}",
            "user_id": user_id,
            "flight_id": first.id,
            "journey_type": "CONNECTING",
            "client_request_id": f"seed-{profile}-connecting-{sequence:04d}",
            "status": status,
            "ticket_amount": ticket_amount,
            "airport_fee": airport_fee,
            "fuel_fee": fuel_fee,
            "service_fee": SERVICE_FEE,
            "total_amount": total_amount,
            "pay_time": created_at + timedelta(minutes=8) if status not in {"PENDING_PAYMENT", "CANCELLED"} else None,
            "expire_time": created_at + timedelta(minutes=15),
            "created_at": created_at,
            "updated_at": created_at,
        }
        connecting_orders.append(order)
        segment_rows: list[dict[str, Any]] = []
        for segment_no, (flight, seats) in enumerate(((first, first_seats), (second, second_seats)), start=1):
            segment_id = ids.next_order_segment()
            segment_rows.append(
                {
                    "id": segment_id,
                    "order_id": order_id,
                    "segment_no": segment_no,
                    "flight_id": flight.id,
                    "flight_no": flight.flight_no,
                    "airline_code": next(a.code for a in AIRLINES if a.id == flight.airline_id),
                    "airline_name": next(a.name for a in AIRLINES if a.id == flight.airline_id),
                    "departure_airport_code": next(a.code for a in AIRPORTS if a.id == flight.departure_airport_id),
                    "departure_airport_name": next(a.name for a in AIRPORTS if a.id == flight.departure_airport_id),
                    "departure_city": next(a.city for a in AIRPORTS if a.id == flight.departure_airport_id),
                    "arrival_airport_code": next(a.code for a in AIRPORTS if a.id == flight.arrival_airport_id),
                    "arrival_airport_name": next(a.name for a in AIRPORTS if a.id == flight.arrival_airport_id),
                    "arrival_city": next(a.city for a in AIRPORTS if a.id == flight.arrival_airport_id),
                    "departure_time": flight.departure_time,
                    "arrival_time": flight.arrival_time,
                    "ticket_amount": money(sum((seat.price for seat in seats), Decimal("0.00"))),
                    "created_at": created_at,
                    "updated_at": created_at,
                }
            )
            segments.append(segment_rows[-1])
            for passenger, seat in zip(selected, seats):
                segment_passengers.append(
                    {
                        "id": ids.next_segment_passenger(),
                        "order_segment_id": segment_id,
                        "passenger_id": passenger["id"],
                        "passenger_name": passenger["name"],
                        "passenger_type": passenger["passenger_type"],
                        "seat_id": seat.id,
                        "seat_no": seat.seat_no,
                        "ticket_price": seat.price,
                        "created_at": created_at,
                    }
                )
            mark_seats_for_order(seats, order_id, status, order["expire_time"])
        return order, segment_rows, selected

    requested = [
        ("published", "ISSUED", 1),
        ("minimum-window", "PENDING_PAYMENT", 2),
        ("different-via", "CANCELLED", 1),
        ("maximum-window", "REFUNDED", 1),
        ("cross-day", "ISSUED", 2),
    ]
    for sequence, (scenario, status, count) in enumerate(requested, start=1):
        itinerary = itinerary_by_scenario.get(scenario)
        if not itinerary or itinerary["publish_status"] != "PUBLISHED":
            continue
        order, _, _ = create_connecting_order(itinerary, status, count, sequence)
        if status == "REFUNDED":
            fee = money(order["total_amount"] * Decimal("0.10"))
            refunds.append(
                {
                    "id": ids.next_refund(),
                    "order_id": order["id"],
                    "user_id": order["user_id"],
                    "reason": "测试数据：联程计划变更退票",
                    "refund_amount": money(order["total_amount"] - fee),
                    "fee_amount": fee,
                    "status": "SUCCESS",
                    "created_at": order["created_at"] + timedelta(hours=2),
                }
            )

    source = itinerary_by_scenario.get("cross-day")
    replacement = itinerary_by_scenario.get("first-tight")
    source_order = next((item for item in connecting_orders if item["client_request_id"].endswith("0005")), None)
    if source and replacement and source_order and replacement["publish_status"] == "PUBLISHED":
        old_segments = [item for item in segments if item["order_id"] == source_order["id"]]
        new_first = flight_by_id[replacement["first_flight_id"]]
        new_second = flight_by_id[replacement["second_flight_id"]]
        new_seats = [available_seats(new_first, "ECONOMY")[:2], available_seats(new_second, "ECONOMY")[:2]]
        if all(len(items) == 2 for items in new_seats):
            old_total = source_order["total_amount"]
            new_total = money(sum((seat.price for items in new_seats for seat in items), Decimal("0.00")) + Decimal("160.00"))
            change_id = ids.next_connecting_change()
            change_records.append(
                {
                    "id": change_id,
                    "order_id": source_order["id"],
                    "user_id": source_order["user_id"],
                    "client_request_id": f"seed-{profile}-connecting-change-0001",
                    "old_total_amount": old_total,
                    "new_total_amount": new_total,
                    "price_difference": money(new_total - old_total),
                    "change_fee": money(new_total * Decimal("0.10")),
                    "reason": "测试数据：联程改签补差价",
                    "status": "SUCCESS",
                    "created_at": base_dt + timedelta(hours=3),
                    "updated_at": base_dt + timedelta(hours=3),
                }
            )
            passenger_ids = [item["passenger_id"] for item in segment_passengers if item["order_segment_id"] == old_segments[0]["id"]]
            for snapshot_type, pairs in (("OLD", [(old_segments[0], [item for item in segment_passengers if item["order_segment_id"] == old_segments[0]["id"]]),
                                                    (old_segments[1], [item for item in segment_passengers if item["order_segment_id"] == old_segments[1]["id"]])]),
                                         ("NEW", [(new_first, []), (new_second, [])])):
                for segment_no, pair in enumerate(pairs, start=1):
                    flight = pair[0]["flight_id"] if snapshot_type == "OLD" else pair[0].id
                    flight_obj = flight_by_id[flight] if isinstance(flight, int) else flight
                    if snapshot_type == "OLD":
                        seat_pairs = [
                            {"passenger_id": item["passenger_id"], "seat_id": item["seat_id"], "seat_no": item["seat_no"]}
                            for item in pair[1]
                        ]
                    else:
                        seat_pairs = [
                            {"passenger_id": passenger_id, "seat_id": seat.id, "seat_no": seat.seat_no}
                            for passenger_id, seat in zip(passenger_ids, new_seats[segment_no - 1])
                        ]
                    change_segments.append(
                        {
                            "id": ids.next_connecting_change_segment(),
                            "change_record_id": change_id,
                            "snapshot_type": snapshot_type,
                            "segment_no": segment_no,
                            "flight_id": flight_obj.id,
                            "flight_no": flight_obj.flight_no,
                            "departure_airport_code": next(a.code for a in AIRPORTS if a.id == flight_obj.departure_airport_id),
                            "arrival_airport_code": next(a.code for a in AIRPORTS if a.id == flight_obj.arrival_airport_id),
                            "departure_time": flight_obj.departure_time,
                            "arrival_time": flight_obj.arrival_time,
                            "passenger_seats": json.dumps(seat_pairs, ensure_ascii=False, separators=(",", ":")),
                            "created_at": base_dt + timedelta(hours=3),
                        }
                    )
            old_seat_ids = {
                item["seat_id"]
                for item in segment_passengers
                if item["order_segment_id"] in {old_segments[0]["id"], old_segments[1]["id"]}
            }
            for flight in flights:
                for seat in flight.seats:
                    if seat.id in old_seat_ids:
                        seat.status = "AVAILABLE"
                        seat.locked_by_order_id = None
                        seat.lock_expire_time = None
            for segment, flight, seats in zip(old_segments, (new_first, new_second), new_seats):
                segment["flight_id"] = flight.id
                segment["flight_no"] = flight.flight_no
                segment["departure_airport_code"] = next(a.code for a in AIRPORTS if a.id == flight.departure_airport_id)
                segment["departure_airport_name"] = next(a.name for a in AIRPORTS if a.id == flight.departure_airport_id)
                segment["departure_city"] = next(a.city for a in AIRPORTS if a.id == flight.departure_airport_id)
                segment["arrival_airport_code"] = next(a.code for a in AIRPORTS if a.id == flight.arrival_airport_id)
                segment["arrival_airport_name"] = next(a.name for a in AIRPORTS if a.id == flight.arrival_airport_id)
                segment["arrival_city"] = next(a.city for a in AIRPORTS if a.id == flight.arrival_airport_id)
                segment["departure_time"] = flight.departure_time
                segment["arrival_time"] = flight.arrival_time
                segment["ticket_amount"] = money(sum((seat.price for seat in seats), Decimal("0.00")))
                for passenger, seat in zip(
                    [item for item in segment_passengers if item["order_segment_id"] == segment["id"]], seats
                ):
                    passenger["seat_id"] = seat.id
                    passenger["seat_no"] = seat.seat_no
                    passenger["ticket_price"] = seat.price
            for flight, seats in ((new_first, new_seats[0]), (new_second, new_seats[1])):
                mark_seats_for_order(seats, source_order["id"], "CHANGED", None)
            source_order["status"] = "CHANGED"
    return connecting_orders, segments, segment_passengers, refunds, change_records, change_segments


def build_ai_records(
    ids: Ids,
    profile: str,
    base_dt: datetime,
    flights: list[Flight],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    sessions: list[dict[str, Any]] = []
    messages: list[dict[str, Any]] = []
    recommendations: list[dict[str, Any]] = []
    can_to_sh = [flight for flight in flights if flight.tag in {"cheap_morning", "morning", "delayed"}]
    recommended_ids = ",".join(str(flight.id) for flight in can_to_sh[:3])
    prompts = [
        ("明天广州到上海的便宜航班", {"departureCity": "广州", "arrivalCity": "上海", "sort": "PRICE_ASC"}),
        ("帮我找早上出发的航班", {"departureTimeStart": "06:00", "departureTimeEnd": "11:00"}),
        ("预算 800 以内", {"maxPrice": 800}),
        ("可以退改签吗", {"policy": "refund_change"}),
    ]
    for index, (prompt, condition) in enumerate(prompts, start=1):
        session_id = ids.next_ai_session()
        created_at = base_dt + timedelta(minutes=index)
        sessions.append(
            {
                "id": session_id,
                "public_session_id": f"seed{profile}{base_dt:%Y%m%d}{index:032d}"[:64],
                "user_id": 2,
                "session_title": prompt[:30],
                "status": "ACTIVE",
                "created_at": created_at,
                "updated_at": created_at,
            }
        )
        messages.append(
            {
                "id": ids.next_ai_message(),
                "session_id": session_id,
                "role": "USER",
                "content": prompt,
                "message_type": "TEXT",
                "extra_json": None,
                "created_at": created_at,
            }
        )
        extra = {
            "cards": [
                {
                    "flightId": flight.id,
                    "flightNo": flight.flight_no,
                    "price": f"{flight.base_price:.2f}",
                    "remainingSeats": flight.remaining_seats,
                }
                for flight in can_to_sh[:3]
            ],
            "quickActions": ["查看详情", "继续筛选"],
        }
        messages.append(
            {
                "id": ids.next_ai_message(),
                "session_id": session_id,
                "role": "ASSISTANT",
                "content": "已根据数据库航班为你筛选出可购买选项。",
                "message_type": "RECOMMENDATION" if index <= 3 else "TEXT",
                "extra_json": json.dumps(extra, ensure_ascii=False) if index <= 3 else None,
                "created_at": created_at + timedelta(seconds=5),
            }
        )
        if index <= 3:
            recommendations.append(
                {
                    "id": ids.next_ai_recommendation(),
                    "session_id": session_id,
                    "user_id": 2,
                    "query_text": prompt,
                    "parsed_condition_json": json.dumps(condition, ensure_ascii=False),
                    "recommended_flight_ids": recommended_ids,
                    "search_url": "/flights?departureCity=广州&arrivalCity=上海&sort=PRICE_ASC",
                    "created_at": created_at + timedelta(seconds=5),
                }
            )
    return sessions, messages, recommendations


def validate_dataset(
    flights: list[Flight],
    orders: list[dict[str, Any]],
    order_passengers: list[dict[str, Any]],
    waitlists: list[dict[str, Any]],
    itineraries: list[dict[str, Any]] | None = None,
    order_segments: list[dict[str, Any]] | None = None,
    segment_passengers: list[dict[str, Any]] | None = None,
    connecting_changes: list[dict[str, Any]] | None = None,
    connecting_change_segments: list[dict[str, Any]] | None = None,
    flight_airports: list[Airport] | None = None,
) -> list[str]:
    errors: list[str] = []
    orders_by_id = {order["id"]: order for order in orders}
    order_passenger_seat_pairs = {(op["order_id"], op["seat_id"]) for op in order_passengers}
    segment_seat_pairs = {
        (next((segment["order_id"] for segment in (order_segments or []) if segment["id"] == passenger["order_segment_id"]), None), passenger["seat_id"])
        for passenger in (segment_passengers or [])
    }
    all_order_seat_pairs = order_passenger_seat_pairs | segment_seat_pairs
    for flight in flights:
        cabin_total = sum(cabin["total_seats"] for cabin in flight.cabins)
        if cabin_total != flight.total_seats:
            errors.append(f"flight {flight.id}: cabin total {cabin_total} != total seats {flight.total_seats}")
        available_count = sum(1 for seat in flight.seats if seat.status == "AVAILABLE")
        if available_count != flight.remaining_seats:
            errors.append(f"flight {flight.id}: remaining seats mismatch")
        prices = {(cabin["cabin_class"], cabin["price"]) for cabin in flight.cabins}
        for seat in flight.seats:
            if (seat.cabin_class, seat.price) not in prices:
                errors.append(f"seat {seat.id}: price does not match cabin")
            if seat.status == "SOLD":
                if seat.locked_by_order_id is None:
                    errors.append(f"seat {seat.id}: SOLD without order")
                elif (seat.locked_by_order_id, seat.id) not in all_order_seat_pairs:
                    errors.append(f"seat {seat.id}: SOLD order passenger missing")
            if seat.status == "LOCKED":
                if seat.locked_by_order_id is None or seat.lock_expire_time is None:
                    errors.append(f"seat {seat.id}: LOCKED without order or expiry")
                elif seat.locked_by_order_id not in orders_by_id:
                    errors.append(f"seat {seat.id}: LOCKED order missing")
    if flight_airports:
        outbound = {flight.departure_airport_id for flight in flights}
        inbound = {flight.arrival_airport_id for flight in flights}
        for airport in flight_airports:
            if airport.id not in outbound:
                errors.append(f"airport {airport.code}: no outbound flight coverage")
            if airport.id not in inbound:
                errors.append(f"airport {airport.code}: no inbound flight coverage")
        mainland_ids = {airport.id for airport in flight_airports if airport.scope == "mainland"}
        for airport in flight_airports:
            if airport.scope == "mainland":
                continue
            connected_to_mainland = any(
                (flight.departure_airport_id == airport.id and flight.arrival_airport_id in mainland_ids)
                or (flight.arrival_airport_id == airport.id and flight.departure_airport_id in mainland_ids)
                for flight in flights
            )
            if not connected_to_mainland:
                errors.append(f"airport {airport.code}: no mainland gateway route")
    for order in orders:
        if order["status"] in {"CANCELLED", "REFUNDED"}:
            for flight in flights:
                for seat in flight.seats:
                    if seat.locked_by_order_id == order["id"] and seat.status in {"SOLD", "LOCKED"}:
                        errors.append(f"order {order['id']}: cancelled/refunded order still owns seat {seat.id}")
    for waitlist in waitlists:
        if waitlist["status"] == "SUCCESS" and waitlist["ticket_order_id"] is None:
            errors.append(f"waitlist {waitlist['id']}: SUCCESS without ticket order")
    flight_by_id = {flight.id: flight for flight in flights}
    for itinerary in itineraries or []:
        first = flight_by_id.get(itinerary["first_flight_id"])
        second = flight_by_id.get(itinerary["second_flight_id"])
        if not first or not second:
            errors.append(f"connecting itinerary {itinerary['id']}: flight missing")
            continue
        transfer = int((second.departure_time - first.arrival_time).total_seconds() // 60)
        if first.arrival_airport_id != second.departure_airport_id:
            errors.append(f"connecting itinerary {itinerary['id']}: airports are not continuous")
        if not 90 <= transfer <= 360:
            errors.append(f"connecting itinerary {itinerary['id']}: transfer window {transfer} minutes is invalid")
        if first.direct_flag != 1 or second.direct_flag != 1:
            errors.append(f"connecting itinerary {itinerary['id']}: both flights must be direct")
    segments_by_order: dict[int, list[dict[str, Any]]] = {}
    for segment in order_segments or []:
        segments_by_order.setdefault(segment["order_id"], []).append(segment)
    passengers_by_segment: dict[int, list[dict[str, Any]]] = {}
    for passenger in segment_passengers or []:
        passengers_by_segment.setdefault(passenger["order_segment_id"], []).append(passenger)
    for order in orders:
        if order.get("journey_type") != "CONNECTING":
            continue
        order_segments_for_order = sorted(segments_by_order.get(order["id"], []), key=lambda item: item["segment_no"])
        if [item["segment_no"] for item in order_segments_for_order] != [1, 2]:
            errors.append(f"connecting order {order['id']}: must contain ordered segments 1 and 2")
            continue
        passenger_sets = []
        for segment in order_segments_for_order:
            passenger_sets.append({item["passenger_id"] for item in passengers_by_segment.get(segment["id"], [])})
        if len(passenger_sets) != 2 or passenger_sets[0] != passenger_sets[1]:
            errors.append(f"connecting order {order['id']}: passenger sets differ between segments")
    changes_by_id = {item["id"]: item for item in connecting_changes or []}
    snapshots_by_change: dict[int, set[tuple[str, int]]] = {}
    for snapshot in connecting_change_segments or []:
        snapshots_by_change.setdefault(snapshot["change_record_id"], set()).add((snapshot["snapshot_type"], snapshot["segment_no"]))
    for change_id in changes_by_id:
        if snapshots_by_change.get(change_id) != {("OLD", 1), ("OLD", 2), ("NEW", 1), ("NEW", 2)}:
            errors.append(f"connecting change {change_id}: OLD/NEW snapshots are incomplete")
    return errors


def generated_id_values(dataset: dict[str, Any]) -> list[int]:
    cfg: ProfileConfig = dataset["cfg"]
    ids: list[int] = []
    ids.extend(user["id"] for user in dataset["users"])
    ids.extend(passenger["id"] for passenger in dataset["passengers"] if passenger["id"] >= cfg.id_base)
    ids.extend(flight.id for flight in dataset["flights"])
    ids.extend(cabin["id"] for flight in dataset["flights"] for cabin in flight.cabins)
    ids.extend(seat.id for flight in dataset["flights"] for seat in flight.seats)
    ids.extend(order["id"] for order in dataset["orders"])
    ids.extend(order_passenger["id"] for order_passenger in dataset["order_passengers"])
    ids.extend(refund["id"] for refund in dataset["refunds"])
    ids.extend(change["id"] for change in dataset["changes"])
    ids.extend(waitlist["id"] for waitlist in dataset["waitlists"])
    ids.extend(passenger["id"] for passenger in dataset["waitlist_passengers"])
    ids.extend(session["id"] for session in dataset["ai_sessions"])
    ids.extend(message["id"] for message in dataset["ai_messages"])
    ids.extend(recommendation["id"] for recommendation in dataset["ai_recommendations"])
    ids.extend(item["id"] for item in dataset["connecting_itineraries"])
    ids.extend(item["id"] for item in dataset["order_segments"])
    ids.extend(item["id"] for item in dataset["segment_passengers"])
    ids.extend(item["id"] for item in dataset["connecting_changes"])
    ids.extend(item["id"] for item in dataset["connecting_change_segments"])
    return ids


def validate_generated_id_ranges(dataset: dict[str, Any]) -> list[str]:
    cfg: ProfileConfig = dataset["cfg"]
    start_id = cfg.id_base
    end_id = cfg.id_base + cfg.id_range
    errors: list[str] = []
    for value in generated_id_values(dataset):
        if not (start_id <= value <= end_id):
            errors.append(f"generated id {value} outside seed range {start_id}-{end_id}")
    return errors


def resolve_components(
    raw_components: str | None,
    auto_dependencies: bool = True,
    scenarios: set[str] | None = None,
) -> set[str]:
    requested = {item.strip().lower() for item in (raw_components or "all").split(",") if item.strip()}
    if "all" in requested:
        requested = set(COMPONENT_NAMES) - {"all"}
    invalid = requested - set(COMPONENT_NAMES)
    if invalid:
        raise ValueError(f"unknown components: {', '.join(sorted(invalid))}")
    missing_component_dependencies = {
        dependency
        for component in requested
        for dependency in COMPONENT_DEPENDENCIES[component]
        if dependency not in requested
    }
    if not auto_dependencies:
        resolved = set(requested)
    else:
        resolved = set(requested)
        changed = True
        while changed:
            changed = False
            for component in tuple(resolved):
                before = len(resolved)
                resolved.update(COMPONENT_DEPENDENCIES[component])
                changed = changed or len(resolved) != before
    if scenarios:
        missing_scenario_components = {
            dependency
            for scenario in scenarios
            for dependency in SCENARIO_COMPONENT_DEPENDENCIES.get(scenario, set())
            if dependency not in resolved
        }
        if not auto_dependencies and (missing_component_dependencies or missing_scenario_components):
            messages = []
            if missing_component_dependencies:
                messages.append(
                    "components require dependencies: "
                    + ", ".join(sorted(missing_component_dependencies))
                )
            if missing_scenario_components:
                messages.append(
                    "scenarios require components: "
                    + ", ".join(sorted(missing_scenario_components))
                )
            raise ValueError("; ".join(messages) + "; remove --no-auto-dependencies or add them explicitly")
        if missing_scenario_components and auto_dependencies:
            resolved.update(missing_scenario_components)
            changed = True
            while changed:
                changed = False
                for component in tuple(resolved):
                    before = len(resolved)
                    resolved.update(COMPONENT_DEPENDENCIES[component])
                    changed = changed or len(resolved) != before
    elif not auto_dependencies and missing_component_dependencies:
        raise ValueError(
            "components require dependencies: "
            + ", ".join(sorted(missing_component_dependencies))
            + "; remove --no-auto-dependencies or add them explicitly"
        )
    return resolved


def resolve_scenarios(raw_scenarios: str | None) -> set[str]:
    requested = {item.strip().lower() for item in (raw_scenarios or "all").split(",") if item.strip()}
    if "all" in requested:
        requested = set(SCENARIO_NAMES) - {"all"}
    invalid = requested - set(SCENARIO_NAMES)
    if invalid:
        raise ValueError(f"unknown scenarios: {', '.join(sorted(invalid))}")
    return requested


def build_dataset(
    profile: str,
    seed: int,
    base_date: date,
    components: set[str] | None = None,
    scenarios: set[str] | None = None,
) -> dict[str, Any]:
    cfg = PROFILES[profile]
    selected_components = components if components is not None else resolve_components(None)
    selected_scenarios = scenarios if scenarios is not None else resolve_scenarios(None)
    rng = random.Random(seed)
    ids = Ids(cfg.id_base, cfg.id_range)
    airports = selected_airports(cfg)
    flight_airports = selected_flight_airports(cfg)
    airlines = selected_airlines(cfg)
    users: list[dict[str, Any]] = []
    passengers: list[dict[str, Any]] = []
    if "users" in selected_components:
        users, passengers = generate_users_and_passengers(ids, profile, cfg)

    direct_scenario_names = {
        "direct", "payment", "cancel", "refund", "change", "waitlist", "sold-out",
        "delayed", "near-departure",
    }
    should_generate_flights = "flights" in selected_components and bool(selected_scenarios & (direct_scenario_names | {"connecting"}))
    flights = generate_flights(ids, rng, cfg, base_date, flight_airports, airlines, selected_scenarios) if should_generate_flights else []
    connecting_flights: list[Flight] = []
    connecting_itineraries: list[dict[str, Any]] = []
    if "connecting" in selected_scenarios and "flights" in selected_components:
        connecting_flights, connecting_itineraries = generate_connecting_itineraries(ids, rng, base_date, flight_airports, airlines)
        flights.extend(connecting_flights)
    base_dt = datetime.combine(base_date, time(9, 0))
    orders: list[dict[str, Any]] = []
    order_passengers: list[dict[str, Any]] = []
    refunds: list[dict[str, Any]] = []
    changes: list[dict[str, Any]] = []
    waitlists: list[dict[str, Any]] = []
    waitlist_passengers: list[dict[str, Any]] = []
    if "orders" in selected_components and selected_scenarios & direct_scenario_names:
        (
            orders,
            order_passengers,
            refunds,
            changes,
            waitlists,
            waitlist_passengers,
        ) = build_orders(
            ids,
            rng,
            profile,
            cfg,
            base_dt,
            flights,
            passengers,
            selected_scenarios,
            include_refunds="refunds" in selected_components,
            include_changes="changes" in selected_components,
            include_waitlists="waitlists" in selected_components,
        )
    connecting_orders: list[dict[str, Any]] = []
    order_segments: list[dict[str, Any]] = []
    segment_passengers: list[dict[str, Any]] = []
    connecting_changes: list[dict[str, Any]] = []
    connecting_change_segments: list[dict[str, Any]] = []
    if "connecting" in selected_scenarios and "orders" in selected_components:
        (
            connecting_orders,
            order_segments,
            segment_passengers,
            connecting_refunds,
            connecting_changes,
            connecting_change_segments,
        ) = build_connecting_orders(ids, profile, base_dt, connecting_itineraries, flights, passengers)
        if "refunds" in selected_components:
            refunds.extend(connecting_refunds)
        if "changes" not in selected_components:
            connecting_changes = []
            connecting_change_segments = []
        orders.extend(connecting_orders)
    ai_sessions: list[dict[str, Any]] = []
    ai_messages: list[dict[str, Any]] = []
    ai_recommendations: list[dict[str, Any]] = []
    if "ai" in selected_components:
        ai_sessions, ai_messages, ai_recommendations = build_ai_records(ids, profile, base_dt, flights)
    dataset = {
        "profile": profile,
        "seed": seed,
        "base_date": base_date,
        "cfg": cfg,
        "airports": airports,
        "flight_airports": flight_airports if should_generate_flights else [],
        "airlines": airlines,
        "users": users,
        "passengers": passengers,
        "flights": flights,
        "orders": orders,
        "order_passengers": order_passengers,
        "refunds": refunds,
        "changes": changes,
        "waitlists": waitlists,
        "waitlist_passengers": waitlist_passengers,
        "ai_sessions": ai_sessions,
        "ai_messages": ai_messages,
        "ai_recommendations": ai_recommendations,
        "connecting_itineraries": connecting_itineraries,
        "order_segments": order_segments,
        "segment_passengers": segment_passengers,
        "connecting_changes": connecting_changes,
        "connecting_change_segments": connecting_change_segments,
        "components": selected_components,
        "scenarios": selected_scenarios,
    }
    errors = validate_dataset(
        flights, orders, order_passengers, waitlists, connecting_itineraries,
        order_segments, segment_passengers, connecting_changes, connecting_change_segments,
        flight_airports if should_generate_flights and selected_scenarios.intersection(direct_scenario_names) else [],
    )
    errors.extend(validate_generated_id_ranges(dataset))
    if errors:
        raise RuntimeError("Generated dataset failed validation:\n" + "\n".join(errors[:20]))
    return dataset


def rows_for_users(users: list[dict[str, Any]]) -> list[tuple[Any, ...]]:
    return [
        (
            user["id"], user["email"], user["phone"], user["password_hash"], user["nickname"],
            user["role"], user["status"], user["email_verified"], user["phone_verified"], user["last_login_at"],
        )
        for user in users
    ]


def rows_for_passengers(passengers: list[dict[str, Any]], seed_base: int) -> list[tuple[Any, ...]]:
    return [
        (
            passenger["id"], passenger["user_id"], passenger["name"], passenger["id_card_no"],
            passenger["passenger_type"], passenger["phone"],
        )
        for passenger in passengers
        if passenger["id"] >= seed_base
    ]


OWNED_TABLE_DELETE_ORDER = (
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


def owned_row_ids(dataset: dict[str, Any]) -> list[tuple[str, int]]:
    components: set[str] = dataset["components"]
    rows: list[tuple[str, int]] = []

    def add(table: str, values: list[int]) -> None:
        rows.extend((table, value) for value in values)

    if "users" in components:
        add("users", [item["id"] for item in dataset["users"]])
        add("passenger", [item["id"] for item in dataset["passengers"] if item["id"] >= dataset["cfg"].id_base])
    if "flights" in components:
        add("flight", [item.id for item in dataset["flights"]])
        add("flight_cabin", [cabin["id"] for flight in dataset["flights"] for cabin in flight.cabins])
        add("flight_seat", [seat.id for flight in dataset["flights"] for seat in flight.seats])
        add("connecting_itinerary", [item["id"] for item in dataset["connecting_itineraries"]])
    if "orders" in components:
        add("ticket_order", [item["id"] for item in dataset["orders"]])
        add("order_passenger", [item["id"] for item in dataset["order_passengers"]])
        add("ticket_order_segment", [item["id"] for item in dataset["order_segments"]])
        add("order_segment_passenger", [item["id"] for item in dataset["segment_passengers"]])
    if "refunds" in components:
        add("refund_record", [item["id"] for item in dataset["refunds"]])
    if "changes" in components:
        add("change_record", [item["id"] for item in dataset["changes"]])
        add("connecting_change_record", [item["id"] for item in dataset["connecting_changes"]])
        add("connecting_change_segment", [item["id"] for item in dataset["connecting_change_segments"]])
    if "waitlists" in components:
        add("waitlist_order", [item["id"] for item in dataset["waitlists"]])
        add("waitlist_passenger", [item["id"] for item in dataset["waitlist_passengers"]])
    if "ai" in components:
        add("ai_chat_session", [item["id"] for item in dataset["ai_sessions"]])
        add("ai_chat_message", [item["id"] for item in dataset["ai_messages"]])
        add("ai_recommendation_record", [item["id"] for item in dataset["ai_recommendations"]])
    return rows


def render_sql(dataset: dict[str, Any]) -> str:
    profile = dataset["profile"]
    seed = dataset["seed"]
    base_date = dataset["base_date"]
    cfg: ProfileConfig = dataset["cfg"]
    source_ref = dataset.get("source_ref")
    components: set[str] = dataset["components"]
    include = lambda component: component in components
    start_id = cfg.id_base
    end_id = cfg.id_base + cfg.id_range
    generated_ids = generated_id_values(dataset)
    generated_id_min = min(generated_ids) if generated_ids else None
    generated_id_max = max(generated_ids) if generated_ids else None
    batch_key = f"skybooker:{profile}"
    ownership = owned_row_ids(dataset)
    statements: list[str] = [
        f"-- SkyBooker reproducible seed data",
        f"-- profile: {profile}",
        f"-- seed: {seed}",
        f"-- base_date: {base_date.isoformat()}",
        f"-- seed_id_range: {start_id}-{end_id}",
        "SET NAMES utf8mb4;",
        "SET time_zone = '+08:00';",
        "START TRANSACTION;",
        "",
        "-- Ownership is checked before replacing the profile batch. A row with the same ID",
        "-- is rejected unless it already belongs to this batch.",
        "CREATE TEMPORARY TABLE tmp_skybooker_seed_rows (",
        "    table_name VARCHAR(64) NOT NULL,",
        "    row_id BIGINT NOT NULL,",
        "    PRIMARY KEY (table_name, row_id)",
        ") DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
    ]
    ownership_rows = [(table, row_id) for table, row_id in ownership]
    statements.extend(insert_statement("tmp_skybooker_seed_rows", ["table_name", "row_id"], ownership_rows))
    collision_statements = ["SET @skybooker_ownership_conflicts = 0;"]
    for table in OWNED_TABLE_DELETE_ORDER:
        collision_statements.extend(
            [
                "SELECT COUNT(*) INTO @skybooker_collision_rows FROM tmp_skybooker_seed_rows r "
                "JOIN " + table + " existing_row ON existing_row.id = r.row_id "
                "LEFT JOIN test_data_ownership o ON o.batch_key = " + sql_string(batch_key) +
                " AND o.table_name = r.table_name AND o.row_id = r.row_id "
                f"WHERE r.table_name = {sql_string(table)} AND o.row_id IS NULL;",
                "SET @skybooker_ownership_conflicts = @skybooker_ownership_conflicts + @skybooker_collision_rows;",
            ]
        )
    collision_statements.extend([
        "SET @skybooker_collision_sql = IF(@skybooker_ownership_conflicts = 0,",
        "    'DO 0',",
        "    'SELECT * FROM skybooker_ownership_conflict_abort');",
        "PREPARE skybooker_collision_stmt FROM @skybooker_collision_sql;",
        "EXECUTE skybooker_collision_stmt;",
        "DEALLOCATE PREPARE skybooker_collision_stmt;",
        "",
        "-- Replace the complete previous batch, independent of the new component subset.",
    ])
    statements.extend(collision_statements)
    for table in OWNED_TABLE_DELETE_ORDER:
        statements.append(
            f"DELETE target FROM {table} target JOIN test_data_ownership owner "
            f"ON owner.batch_key = {sql_string(batch_key)} AND owner.table_name = {sql_string(table)} "
            "AND owner.row_id = target.id;"
        )
    statements.extend([
        f"DELETE FROM test_data_ownership WHERE batch_key = {sql_string(batch_key)};",
        f"DELETE FROM test_data_batch WHERE batch_key = {sql_string(batch_key)};",
        "",
    ])

    airport_rows = [(a.code, a.name, a.city, a.province, "ENABLED") for a in dataset["airports"]]
    airline_rows = [(a.code, a.name, None, "ENABLED") for a in dataset["airlines"]]
    if include("reference"):
        statements.extend(
            upsert_statement("airline", ["code", "name", "logo_url", "status"], airline_rows, ["name", "logo_url", "status"])
        )
        statements.extend(
            upsert_statement("airport", ["code", "name", "city", "province", "status"], airport_rows, ["name", "city", "province", "status"])
        )

    if include("users"):
        statements.extend(
            insert_statement(
                "users",
                [
                    "id", "email", "phone", "password_hash", "nickname", "role", "status",
                    "email_verified", "phone_verified", "last_login_at",
                ],
                rows_for_users(dataset["users"]),
            )
        )
        statements.extend(
            insert_statement(
                "passenger",
                ["id", "user_id", "name", "id_card_no", "passenger_type", "phone"],
                rows_for_passengers(dataset["passengers"], cfg.id_base),
            )
        )

    airports_by_id = {airport.id: airport for airport in dataset["airports"]}
    airlines_by_id = {airline.id: airline for airline in dataset["airlines"]}
    flight_rows = [
        (
            flight.id, flight.flight_no,
            SqlExpression("(SELECT id FROM airline WHERE code=" + sql_string(airlines_by_id[flight.airline_id].code) + ")"),
            SqlExpression("(SELECT id FROM airport WHERE code=" + sql_string(airports_by_id[flight.departure_airport_id].code) + ")"),
            SqlExpression("(SELECT id FROM airport WHERE code=" + sql_string(airports_by_id[flight.arrival_airport_id].code) + ")"),
            flight.departure_time, flight.arrival_time, flight.duration_minutes, flight.base_price,
            flight.remaining_seats, flight.total_seats, flight.status, flight.publish_status, flight.direct_flag,
            flight.baggage_allowance, flight.punctuality_rate,
        )
        for flight in dataset["flights"]
    ]
    if include("flights"):
        statements.extend(
            insert_statement(
                "flight",
                [
                    "id", "flight_no", "airline_id", "departure_airport_id", "arrival_airport_id",
                    "departure_time", "arrival_time", "duration_minutes", "base_price",
                    "remaining_seats", "total_seats", "status", "publish_status", "direct_flag",
                    "baggage_allowance", "punctuality_rate",
                ],
                flight_rows,
            )
        )
    cabin_rows = [
        (cabin["id"], cabin["flight_id"], cabin["cabin_class"], cabin["price"], cabin["total_seats"])
        for flight in dataset["flights"]
        for cabin in flight.cabins
    ]
    if include("flights"):
        statements.extend(insert_statement("flight_cabin", ["id", "flight_id", "cabin_class", "price", "total_seats"], cabin_rows))
        connecting_itinerary_rows = [
            (
                item["id"], item["first_flight_id"], item["second_flight_id"], item["publish_status"],
                item["created_by"], item["created_at"], item["updated_at"],
            )
            for item in dataset["connecting_itineraries"]
        ]
        statements.extend(
            insert_statement(
                "connecting_itinerary",
                ["id", "first_flight_id", "second_flight_id", "publish_status", "created_by", "created_at", "updated_at"],
                connecting_itinerary_rows,
            )
        )

    order_rows = [
        (
            order["id"], order["order_no"], order["user_id"], order["flight_id"], order["journey_type"],
            order["client_request_id"], order["status"],
            order["ticket_amount"], order["airport_fee"], order["fuel_fee"], order["service_fee"], order["total_amount"],
            order["pay_time"], order["expire_time"], order["created_at"], order["updated_at"],
        )
        for order in dataset["orders"]
    ]
    if include("orders"):
        statements.extend(
            insert_statement(
                "ticket_order",
                [
                    "id", "order_no", "user_id", "flight_id", "journey_type", "client_request_id", "status",
                    "ticket_amount", "airport_fee", "fuel_fee", "service_fee", "total_amount", "pay_time",
                    "expire_time", "created_at", "updated_at",
                ],
                order_rows,
            )
        )

    seat_rows = [
        (
            seat.id, seat.flight_id, seat.seat_no, seat.cabin_class, seat.seat_type, seat.price,
            seat.status, 0, seat.locked_by_order_id, seat.lock_expire_time,
        )
        for flight in dataset["flights"]
        for seat in flight.seats
    ]
    if include("flights"):
        statements.extend(
            insert_statement(
                "flight_seat",
                [
                    "id", "flight_id", "seat_no", "cabin_class", "seat_type", "price", "status",
                    "version", "locked_by_order_id", "lock_expire_time",
                ],
                seat_rows,
            )
        )

    op_rows = [
        (
            op["id"], op["order_id"], op["passenger_id"], op["passenger_name"], op["passenger_type"],
            op["seat_id"], op["seat_no"], op["ticket_price"], op["created_at"],
        )
        for op in dataset["order_passengers"]
    ]
    if include("orders"):
        statements.extend(
            insert_statement(
                "order_passenger",
                ["id", "order_id", "passenger_id", "passenger_name", "passenger_type", "seat_id", "seat_no", "ticket_price", "created_at"],
                op_rows,
            )
        )
        order_segment_rows = [
            (
                segment["id"], segment["order_id"], segment["segment_no"], segment["flight_id"], segment["flight_no"],
                segment["airline_code"], segment["airline_name"], segment["departure_airport_code"],
                segment["departure_airport_name"], segment["departure_city"], segment["arrival_airport_code"],
                segment["arrival_airport_name"], segment["arrival_city"], segment["departure_time"],
                segment["arrival_time"], segment["ticket_amount"], segment["created_at"], segment["updated_at"],
            )
            for segment in dataset["order_segments"]
        ]
        statements.extend(
            insert_statement(
                "ticket_order_segment",
                [
                    "id", "order_id", "segment_no", "flight_id", "flight_no", "airline_code", "airline_name",
                    "departure_airport_code", "departure_airport_name", "departure_city", "arrival_airport_code",
                    "arrival_airport_name", "arrival_city", "departure_time", "arrival_time", "ticket_amount",
                    "created_at", "updated_at",
                ],
                order_segment_rows,
            )
        )
        segment_passenger_rows = [
            (
                passenger["id"], passenger["order_segment_id"], passenger["passenger_id"], passenger["passenger_name"],
                passenger["passenger_type"], passenger["seat_id"], passenger["seat_no"], passenger["ticket_price"],
                passenger["created_at"],
            )
            for passenger in dataset["segment_passengers"]
        ]
        statements.extend(
            insert_statement(
                "order_segment_passenger",
                [
                    "id", "order_segment_id", "passenger_id", "passenger_name", "passenger_type", "seat_id",
                    "seat_no", "ticket_price", "created_at",
                ],
                segment_passenger_rows,
            )
        )

    refund_rows = [
        (
            refund["id"], refund["order_id"], refund["user_id"], refund["reason"], refund["refund_amount"],
            refund["fee_amount"], refund["status"], refund["created_at"],
        )
        for refund in dataset["refunds"]
    ]
    if include("refunds"):
        statements.extend(
            insert_statement(
                "refund_record",
                ["id", "order_id", "user_id", "reason", "refund_amount", "fee_amount", "status", "created_at"],
                refund_rows,
            )
        )

    change_rows = [
        (
            change["id"], change["order_id"], change["old_flight_id"], change["new_flight_id"],
            change["old_seat_id"], change["new_seat_id"], change["price_diff"], change["change_fee"],
            change["status"], change["created_at"],
        )
        for change in dataset["changes"]
    ]
    if include("changes"):
        statements.extend(
            insert_statement(
                "change_record",
                [
                    "id", "order_id", "old_flight_id", "new_flight_id", "old_seat_id", "new_seat_id",
                    "price_diff", "change_fee", "status", "created_at",
                ],
                change_rows,
            )
        )
        connecting_change_rows = [
            (
                change["id"], change["order_id"], change["user_id"], change["client_request_id"],
                change["old_total_amount"], change["new_total_amount"], change["price_difference"],
                change["change_fee"], change["reason"], change["status"], change["created_at"], change["updated_at"],
            )
            for change in dataset["connecting_changes"]
        ]
        statements.extend(
            insert_statement(
                "connecting_change_record",
                [
                    "id", "order_id", "user_id", "client_request_id", "old_total_amount", "new_total_amount",
                    "price_difference", "change_fee", "reason", "status", "created_at", "updated_at",
                ],
                connecting_change_rows,
            )
        )
        connecting_change_segment_rows = [
            (
                segment["id"], segment["change_record_id"], segment["snapshot_type"], segment["segment_no"],
                segment["flight_id"], segment["flight_no"], segment["departure_airport_code"],
                segment["arrival_airport_code"], segment["departure_time"], segment["arrival_time"],
                segment["passenger_seats"], segment["created_at"],
            )
            for segment in dataset["connecting_change_segments"]
        ]
        statements.extend(
            insert_statement(
                "connecting_change_segment",
                [
                    "id", "change_record_id", "snapshot_type", "segment_no", "flight_id", "flight_no",
                    "departure_airport_code", "arrival_airport_code", "departure_time", "arrival_time",
                    "passenger_seats", "created_at",
                ],
                connecting_change_segment_rows,
            )
        )

    waitlist_rows = [
        (
            waitlist["id"], waitlist["waitlist_no"], waitlist["user_id"], waitlist["flight_id"],
            waitlist["ticket_order_id"], waitlist["passenger_count"], waitlist["cabin_class"], waitlist["pay_amount"],
            waitlist["status"], waitlist["expire_time"], waitlist["paid_at"], waitlist["refund_amount"],
            waitlist["refund_time"], waitlist["last_skip_reason"], waitlist["created_at"], waitlist["updated_at"],
        )
        for waitlist in dataset["waitlists"]
    ]
    if include("waitlists"):
        statements.extend(
            insert_statement(
                "waitlist_order",
                [
                    "id", "waitlist_no", "user_id", "flight_id", "ticket_order_id", "passenger_count",
                    "cabin_class", "pay_amount", "status", "expire_time", "paid_at", "refund_amount",
                    "refund_time", "last_skip_reason", "created_at", "updated_at",
                ],
                waitlist_rows,
            )
        )
    waitlist_passenger_rows = [
        (
            passenger["id"], passenger["waitlist_id"], passenger["passenger_id"], passenger["passenger_name"],
            passenger["passenger_type"], passenger["locked_seat_id"], passenger["locked_seat_no"],
            passenger["created_at"], passenger["updated_at"],
        )
        for passenger in dataset["waitlist_passengers"]
    ]
    if include("waitlists"):
        statements.extend(
            insert_statement(
                "waitlist_passenger",
                [
                    "id", "waitlist_id", "passenger_id", "passenger_name", "passenger_type", "locked_seat_id",
                    "locked_seat_no", "created_at", "updated_at",
                ],
                waitlist_passenger_rows,
            )
        )

    session_rows = [
        (
            session["id"], session["public_session_id"], session["user_id"], session["session_title"],
            session["status"], session["created_at"], session["updated_at"],
        )
        for session in dataset["ai_sessions"]
    ]
    if include("ai"):
        statements.extend(
            insert_statement(
                "ai_chat_session",
                ["id", "public_session_id", "user_id", "session_title", "status", "created_at", "updated_at"],
                session_rows,
            )
        )
    message_rows = [
        (
            message["id"], message["session_id"], message["role"], message["content"], message["message_type"],
            message["extra_json"], message["created_at"],
        )
        for message in dataset["ai_messages"]
    ]
    if include("ai"):
        statements.extend(
            insert_statement(
                "ai_chat_message",
                ["id", "session_id", "role", "content", "message_type", "extra_json", "created_at"],
                message_rows,
            )
        )
    recommendation_rows = [
        (
            rec["id"], rec["session_id"], rec["user_id"], rec["query_text"], rec["parsed_condition_json"],
            rec["recommended_flight_ids"], rec["search_url"], rec["created_at"],
        )
        for rec in dataset["ai_recommendations"]
    ]
    if include("ai"):
        statements.extend(
            insert_statement(
                "ai_recommendation_record",
                [
                    "id", "session_id", "user_id", "query_text", "parsed_condition_json",
                    "recommended_flight_ids", "search_url", "created_at",
                ],
                recommendation_rows,
            )
        )

    statements.extend(
        [
            "",
            "INSERT INTO test_data_batch(batch_key, profile, seed, source_ref) VALUES ("
            + ", ".join([sql_string(batch_key), sql_string(profile), str(seed), sql_string(source_ref)])
            + ") ON DUPLICATE KEY UPDATE profile=VALUES(profile), seed=VALUES(seed), source_ref=VALUES(source_ref);",
        ]
    )
    ownership_insert_rows = [(batch_key, table, row_id) for table, row_id in ownership]
    statements.extend(
        insert_statement(
            "test_data_ownership",
            ["batch_key", "table_name", "row_id"],
            ownership_insert_rows,
        )
    )
    statements.extend(["DROP TEMPORARY TABLE tmp_skybooker_seed_rows;", ""])

    reference_airports: list[Airport] = dataset["airports"]
    flight_airports: list[Airport] = dataset.get("flight_airports", [])
    outbound_ids = {flight.departure_airport_id for flight in dataset["flights"]}
    inbound_ids = {flight.arrival_airport_id for flight in dataset["flights"]}
    airports_without_outbound = [airport.code for airport in flight_airports if airport.id not in outbound_ids]
    airports_without_inbound = [airport.code for airport in flight_airports if airport.id not in inbound_ids]
    mainland_ids = {airport.id for airport in flight_airports if airport.scope == "mainland"}
    airports_without_mainland_gateway = [
        airport.code
        for airport in flight_airports
        if airport.scope != "mainland"
        and not any(
            (flight.departure_airport_id == airport.id and flight.arrival_airport_id in mainland_ids)
            or (flight.arrival_airport_id == airport.id and flight.departure_airport_id in mainland_ids)
            for flight in dataset["flights"]
        )
    ]
    flight_coverage_required = bool(
        include("flights")
        and set(dataset["scenarios"]).intersection({
            "direct", "payment", "cancel", "refund", "change", "waitlist", "sold-out",
            "delayed", "near-departure",
        })
    )
    airport_codes = sorted(airport.code for airport in reference_airports)
    flight_airport_codes = sorted(airport.code for airport in flight_airports)
    mainland_flight_airport_codes = sorted(
        airport.code for airport in flight_airports if airport.scope == "mainland"
    )
    non_mainland_flight_airport_codes = sorted(
        airport.code for airport in flight_airports if airport.scope != "mainland"
    )
    required_bidirectional_routes = [
        [dep, arr]
        for dep, arr in BIDIRECTIONAL_HUB_PAIRS
        if dep in set(flight_airport_codes) and arr in set(flight_airport_codes)
    ]
    airport_catalog_source = AIRPORT_CATALOG_METADATA["airports-cn.json"].get("source")

    summary = {
        "profile": profile,
        "seed": seed,
        "baseDate": base_date.isoformat(),
        "batchKey": batch_key,
        "sourceRef": source_ref,
        "components": sorted(components),
        "scenarios": sorted(dataset["scenarios"]),
        "scenariosExplicit": dataset.get("scenarios_explicit", True),
        "seedIdRange": f"{start_id}-{end_id}",
        "generatedIdMin": generated_id_min,
        "generatedIdMax": generated_id_max,
        "airports": len(reference_airports),
        "airportReferenceCount": len(reference_airports),
        "flightAirportCount": len(flight_airports),
        "domesticAirportCount": sum(airport.scope == "mainland" for airport in reference_airports),
        "specialRegionAirportCount": sum(airport.scope == "special_region" for airport in reference_airports),
        "internationalAirportCount": sum(airport.scope == "international" for airport in reference_airports),
        "countriesCovered": len({airport.country_or_region for airport in reference_airports}),
        "regionsCovered": sorted({airport.province for airport in reference_airports}),
        "airportCodes": airport_codes,
        "flightAirportCodes": flight_airport_codes,
        "mainlandFlightAirportCodes": mainland_flight_airport_codes,
        "nonMainlandFlightAirportCodes": non_mainland_flight_airport_codes,
        "requiredBidirectionalRoutes": required_bidirectional_routes,
        "airportsWithOutboundFlights": len(outbound_ids.intersection({airport.id for airport in flight_airports})),
        "airportsWithInboundFlights": len(inbound_ids.intersection({airport.id for airport in flight_airports})),
        "airportsWithoutOutboundFlights": airports_without_outbound,
        "airportsWithoutInboundFlights": airports_without_inbound,
        "airportsWithoutMainlandGateway": airports_without_mainland_gateway,
        "flightCoverageRequired": flight_coverage_required,
        "airportCatalogSource": airport_catalog_source,
        "airportCatalogRetrievedAt": AIRPORT_CATALOG_METADATA["airports-cn.json"].get("retrievedAt"),
        "airlines": len(dataset["airlines"]),
        "routes": len({(flight.departure_airport_id, flight.arrival_airport_id) for flight in dataset["flights"]}),
        "flights": len(dataset["flights"]) if include("flights") else 0,
        "cabins": len(cabin_rows) if include("flights") else 0,
        "seats": len(seat_rows) if include("flights") else 0,
        "users": len(dataset["users"]) + 1 if include("users") else 0,
        "passengers": len(dataset["passengers"]) if include("users") else 0,
        "orders": len(dataset["orders"]) if include("orders") else 0,
        "refunds": len(dataset["refunds"]) if include("refunds") else 0,
        "changes": len(dataset["changes"]) if include("changes") else 0,
        "waitlists": len(dataset["waitlists"]) if include("waitlists") else 0,
        "aiSessions": len(dataset["ai_sessions"]) if include("ai") else 0,
        "connectingItineraries": len(dataset["connecting_itineraries"]) if include("flights") else 0,
        "connectingOrders": sum(1 for order in dataset["orders"] if order.get("journey_type") == "CONNECTING") if include("orders") else 0,
        "connectingChanges": len(dataset["connecting_changes"]) if include("changes") else 0,
        "connectingChangeSnapshots": len(dataset["connecting_change_segments"]) if include("changes") else 0,
        "scenarioCoverage": {
            scenario: sum(1 for item in dataset["connecting_itineraries"] if item["scenario"] == scenario)
            for scenario in sorted({item["scenario"] for item in dataset["connecting_itineraries"]})
        },
        "validation": {
            "flightRemainingMatchesAvailableSeats": True,
            "flightCabinTotalsMatchFlightTotal": True,
            "seatPricesMatchCabinPrices": True,
            "soldSeatsHaveOrderPassenger": True,
            "lockedSeatsHaveOrderAndExpiry": True,
            "successWaitlistsHaveTicketOrder": True,
            "generatedIdsWithinProfileRange": True,
            "airportCodesUnique": len(airport_codes) == len(set(airport_codes)),
            "flightCoverageComplete": not flight_coverage_required or (
                not airports_without_outbound
                and not airports_without_inbound
                and not airports_without_mainland_gateway
            ),
            "internationalGatewaysConnected": not flight_coverage_required or not airports_without_mainland_gateway,
        },
    }
    statements.extend(
        [
            "",
            "-- SKYBOOKER_SEED_SUMMARY_BEGIN",
            "-- " + json.dumps(summary, ensure_ascii=False, sort_keys=True),
            "-- SKYBOOKER_SEED_SUMMARY_END",
            "COMMIT;",
            "",
        ]
    )
    return "\n\n".join(statements)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate SkyBooker reproducible test data SQL.")
    parser.add_argument("--profile", choices=sorted(PROFILES), default="dev")
    parser.add_argument("--seed", type=int, default=20260707)
    parser.add_argument("--base-date", help="YYYY-MM-DD. Defaults to YYYYMMDD when --seed looks like a date.")
    parser.add_argument(
        "--components", default="all",
        help="Comma-separated modules: reference,users,flights,orders,refunds,changes,waitlists,ai,all.",
    )
    parser.add_argument(
        "--scenarios",
        help="Comma-separated scenarios: direct,connecting,payment,cancel,refund,change,waitlist,sold-out,delayed,near-departure,all. Omit to use all applicable scenarios without expanding explicit components.",
    )
    parser.add_argument(
        "--no-auto-dependencies", action="store_true",
        help="Fail instead of adding required component dependencies automatically.",
    )
    parser.add_argument(
        "--output",
        help="Output SQL path. Defaults to backend/src/main/resources/db/seed/seed-<profile>.sql.",
    )
    parser.add_argument("--summary-file", help="Write the machine-readable summary JSON to this path.")
    parser.add_argument("--source-ref", help="Resolved helper commit SHA recorded with the ownership batch.")
    args = parser.parse_args()

    base_date = parse_base_date(args.seed, args.base_date)
    scenarios = resolve_scenarios(args.scenarios)
    components = resolve_components(
        args.components,
        auto_dependencies=not args.no_auto_dependencies,
        scenarios=scenarios if args.scenarios is not None else None,
    )
    dataset = build_dataset(args.profile, args.seed, base_date, components, scenarios)
    dataset["scenarios_explicit"] = args.scenarios is not None
    dataset["source_ref"] = args.source_ref
    sql = render_sql(dataset)
    output = Path(args.output) if args.output else Path("backend/src/main/resources/db/seed") / f"seed-{args.profile}.sql"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(sql, encoding="utf-8")
    if args.summary_file:
        summary_match = re.search(
            r"-- SKYBOOKER_SEED_SUMMARY_BEGIN\s*\n-- (?P<json>\{.*?\})\s*\n-- SKYBOOKER_SEED_SUMMARY_END",
            sql,
            re.DOTALL,
        )
        if summary_match:
            summary_path = Path(args.summary_file)
            summary_path.parent.mkdir(parents=True, exist_ok=True)
            summary_path.write_text(json.dumps(json.loads(summary_match.group("json")), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {output} ({len(sql.encode('utf-8'))} bytes)")


if __name__ == "__main__":
    main()
