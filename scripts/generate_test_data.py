#!/usr/bin/env python3
"""Generate reproducible SkyBooker test seed SQL.

The generator intentionally keeps aviation reference data small and readable,
then creates flights, seats, orders, refunds, changes, waitlists and AI records
from deterministic rules. It does not call external APIs.
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


@dataclass(frozen=True)
class Airport:
    id: int
    code: str
    name: str
    city: str
    province: str


@dataclass(frozen=True)
class Airline:
    id: int
    code: str
    name: str


@dataclass(frozen=True)
class ProfileConfig:
    airport_count: int
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
            self.order = base + 50_000
            self.order_passenger = base + 60_000
            self.refund = base + 70_000
            self.change = base + 71_000
            self.waitlist = base + 72_000
            self.waitlist_passenger = base + 73_000
            self.ai_session = base + 74_000
            self.ai_message = base + 74_100
            self.ai_recommendation = base + 74_200
        else:
            self.user = base + 100
            self.passenger = base + 1_000
            self.flight = base + 10_000
            self.seat = base + 100_000
            self.order = base + 500_000
            self.order_passenger = base + 700_000
            self.refund = base + 800_000
            self.change = base + 820_000
            self.waitlist = base + 840_000
            self.waitlist_passenger = base + 860_000
            self.ai_session = base + 880_000
            self.ai_message = base + 890_000
            self.ai_recommendation = base + 900_000

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


PROFILES = {
    "dev": ProfileConfig(
        airport_count=24,
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
        airport_count=60,
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
        airport_count=100,
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


AIRLINES = [
    Airline(1, "MU", "中国东方航空"),
    Airline(2, "CZ", "中国南方航空"),
    Airline(3, "CA", "中国国际航空"),
    Airline(4, "HU", "海南航空"),
    Airline(5, "ZH", "深圳航空"),
    Airline(6, "MF", "厦门航空"),
    Airline(7, "3U", "四川航空"),
    Airline(8, "9C", "春秋航空"),
    Airline(9, "HO", "吉祥航空"),
    Airline(10, "CX", "国泰航空"),
    Airline(11, "SQ", "新加坡航空"),
    Airline(12, "NH", "全日空"),
    Airline(13, "KE", "大韩航空"),
    Airline(14, "FM", "上海航空"),
    Airline(15, "SC", "山东航空"),
    Airline(16, "JD", "首都航空"),
    Airline(17, "GS", "天津航空"),
    Airline(18, "KN", "中国联合航空"),
    Airline(19, "EU", "成都航空"),
    Airline(20, "PN", "西部航空"),
    Airline(21, "GJ", "长龙航空"),
    Airline(22, "NS", "河北航空"),
    Airline(23, "TV", "西藏航空"),
    Airline(24, "OQ", "重庆航空"),
    Airline(25, "BK", "奥凯航空"),
    Airline(26, "GX", "北部湾航空"),
    Airline(27, "RY", "江西航空"),
    Airline(28, "8L", "祥鹏航空"),
    Airline(29, "Y8", "金鹏航空"),
    Airline(30, "KY", "昆明航空"),
    Airline(31, "JL", "日本航空"),
    Airline(32, "TG", "泰国国际航空"),
    Airline(33, "MH", "马来西亚航空"),
    Airline(34, "UA", "美国联合航空"),
    Airline(35, "BA", "英国航空"),
    Airline(36, "EK", "阿联酋航空"),
    Airline(37, "QR", "卡塔尔航空"),
    Airline(38, "LH", "德国汉莎航空"),
]


AIRPORTS = [
    Airport(1, "SHA", "上海虹桥国际机场", "上海", "上海"),
    Airport(2, "PVG", "上海浦东国际机场", "上海", "上海"),
    Airport(3, "PEK", "北京首都国际机场", "北京", "北京"),
    Airport(4, "PKX", "北京大兴国际机场", "北京", "北京"),
    Airport(5, "CAN", "广州白云国际机场", "广州", "广东"),
    Airport(6, "SZX", "深圳宝安国际机场", "深圳", "广东"),
    Airport(7, "CTU", "成都双流国际机场", "成都", "四川"),
    Airport(8, "TFU", "成都天府国际机场", "成都", "四川"),
    Airport(9, "CKG", "重庆江北国际机场", "重庆", "重庆"),
    Airport(10, "HGH", "杭州萧山国际机场", "杭州", "浙江"),
    Airport(11, "NKG", "南京禄口国际机场", "南京", "江苏"),
    Airport(12, "WUH", "武汉天河国际机场", "武汉", "湖北"),
    Airport(13, "XIY", "西安咸阳国际机场", "西安", "陕西"),
    Airport(14, "KMG", "昆明长水国际机场", "昆明", "云南"),
    Airport(15, "XMN", "厦门高崎国际机场", "厦门", "福建"),
    Airport(16, "HAK", "海口美兰国际机场", "海口", "海南"),
    Airport(17, "SYX", "三亚凤凰国际机场", "三亚", "海南"),
    Airport(18, "HKG", "香港国际机场", "香港", "香港"),
    Airport(19, "MFM", "澳门国际机场", "澳门", "澳门"),
    Airport(20, "SIN", "新加坡樟宜机场", "新加坡", "新加坡"),
    Airport(21, "HND", "东京羽田机场", "东京", "日本"),
    Airport(22, "NRT", "东京成田机场", "东京", "日本"),
    Airport(23, "ICN", "首尔仁川机场", "首尔", "韩国"),
    Airport(24, "BKK", "曼谷素万那普机场", "曼谷", "泰国"),
    Airport(25, "TAO", "青岛胶东国际机场", "青岛", "山东"),
    Airport(26, "TNA", "济南遥墙国际机场", "济南", "山东"),
    Airport(27, "CSX", "长沙黄花国际机场", "长沙", "湖南"),
    Airport(28, "CGO", "郑州新郑国际机场", "郑州", "河南"),
    Airport(29, "TSN", "天津滨海国际机场", "天津", "天津"),
    Airport(30, "SHE", "沈阳桃仙国际机场", "沈阳", "辽宁"),
    Airport(31, "DLC", "大连周水子国际机场", "大连", "辽宁"),
    Airport(32, "HRB", "哈尔滨太平国际机场", "哈尔滨", "黑龙江"),
    Airport(33, "CGQ", "长春龙嘉国际机场", "长春", "吉林"),
    Airport(34, "HET", "呼和浩特白塔国际机场", "呼和浩特", "内蒙古"),
    Airport(35, "URC", "乌鲁木齐地窝堡国际机场", "乌鲁木齐", "新疆"),
    Airport(36, "LHW", "兰州中川国际机场", "兰州", "甘肃"),
    Airport(37, "INC", "银川河东国际机场", "银川", "宁夏"),
    Airport(38, "XNN", "西宁曹家堡国际机场", "西宁", "青海"),
    Airport(39, "TYN", "太原武宿国际机场", "太原", "山西"),
    Airport(40, "KHN", "南昌昌北国际机场", "南昌", "江西"),
    Airport(41, "NGB", "宁波栎社国际机场", "宁波", "浙江"),
    Airport(42, "WEN", "温州龙湾国际机场", "温州", "浙江"),
    Airport(43, "HFE", "合肥新桥国际机场", "合肥", "安徽"),
    Airport(44, "NNG", "南宁吴圩国际机场", "南宁", "广西"),
    Airport(45, "KWE", "贵阳龙洞堡国际机场", "贵阳", "贵州"),
    Airport(46, "KWL", "桂林两江国际机场", "桂林", "广西"),
    Airport(47, "LJG", "丽江三义国际机场", "丽江", "云南"),
    Airport(48, "DYG", "张家界荷花国际机场", "张家界", "湖南"),
    Airport(49, "ZUH", "珠海金湾机场", "珠海", "广东"),
    Airport(50, "SWA", "揭阳潮汕国际机场", "揭阳", "广东"),
    Airport(51, "JHG", "西双版纳嘎洒国际机场", "西双版纳", "云南"),
    Airport(52, "LXA", "拉萨贡嘎国际机场", "拉萨", "西藏"),
    Airport(53, "KHG", "喀什徕宁国际机场", "喀什", "新疆"),
    Airport(54, "YNT", "烟台蓬莱国际机场", "烟台", "山东"),
    Airport(55, "WEH", "威海大水泊机场", "威海", "山东"),
    Airport(56, "NTG", "南通兴东国际机场", "南通", "江苏"),
    Airport(57, "LYI", "临沂启阳国际机场", "临沂", "山东"),
    Airport(58, "YIW", "义乌机场", "义乌", "浙江"),
    Airport(59, "JJN", "泉州晋江国际机场", "泉州", "福建"),
    Airport(60, "HSN", "舟山普陀山机场", "舟山", "浙江"),
    Airport(61, "YNZ", "盐城南洋国际机场", "盐城", "江苏"),
    Airport(62, "CZX", "常州奔牛国际机场", "常州", "江苏"),
    Airport(63, "YIH", "宜昌三峡机场", "宜昌", "湖北"),
    Airport(64, "XUZ", "徐州观音国际机场", "徐州", "江苏"),
    Airport(65, "WUS", "武夷山机场", "武夷山", "福建"),
    Airport(66, "AQG", "安庆天柱山机场", "安庆", "安徽"),
    Airport(67, "JDZ", "景德镇罗家机场", "景德镇", "江西"),
    Airport(68, "JIQ", "黔江武陵山机场", "重庆", "重庆"),
    Airport(69, "DSN", "鄂尔多斯伊金霍洛国际机场", "鄂尔多斯", "内蒙古"),
    Airport(70, "BAV", "包头东河机场", "包头", "内蒙古"),
    Airport(71, "MDG", "牡丹江海浪国际机场", "牡丹江", "黑龙江"),
    Airport(72, "JMU", "佳木斯东郊机场", "佳木斯", "黑龙江"),
    Airport(73, "NDG", "齐齐哈尔三家子机场", "齐齐哈尔", "黑龙江"),
    Airport(74, "YNJ", "延吉朝阳川国际机场", "延吉", "吉林"),
    Airport(75, "TEN", "铜仁凤凰机场", "铜仁", "贵州"),
    Airport(76, "LZH", "柳州白莲机场", "柳州", "广西"),
    Airport(77, "LZO", "泸州云龙机场", "泸州", "四川"),
    Airport(78, "MIG", "绵阳南郊机场", "绵阳", "四川"),
    Airport(79, "YBP", "宜宾五粮液机场", "宜宾", "四川"),
    Airport(80, "NAO", "南充高坪机场", "南充", "四川"),
    Airport(81, "WXN", "万州五桥机场", "重庆", "重庆"),
    Airport(82, "ENH", "恩施许家坪机场", "恩施", "湖北"),
    Airport(83, "LLV", "吕梁大武机场", "吕梁", "山西"),
    Airport(84, "CIH", "长治王村机场", "长治", "山西"),
    Airport(85, "HDG", "邯郸机场", "邯郸", "河北"),
    Airport(86, "BPE", "秦皇岛北戴河机场", "秦皇岛", "河北"),
    Airport(87, "KUL", "吉隆坡国际机场", "吉隆坡", "马来西亚"),
    Airport(88, "SGN", "胡志明市新山一国际机场", "胡志明市", "越南"),
    Airport(89, "HAN", "河内内排国际机场", "河内", "越南"),
    Airport(90, "DAD", "岘港国际机场", "岘港", "越南"),
    Airport(91, "MNL", "马尼拉尼诺伊阿基诺国际机场", "马尼拉", "菲律宾"),
    Airport(92, "CGK", "雅加达苏加诺-哈达国际机场", "雅加达", "印度尼西亚"),
    Airport(93, "DPS", "巴厘岛伍拉赖国际机场", "巴厘岛", "印度尼西亚"),
    Airport(94, "SYD", "悉尼金斯福德·史密斯机场", "悉尼", "澳大利亚"),
    Airport(95, "MEL", "墨尔本机场", "墨尔本", "澳大利亚"),
    Airport(96, "LAX", "洛杉矶国际机场", "洛杉矶", "美国"),
    Airport(97, "SFO", "旧金山国际机场", "旧金山", "美国"),
    Airport(98, "JFK", "纽约肯尼迪国际机场", "纽约", "美国"),
    Airport(99, "LHR", "伦敦希思罗机场", "伦敦", "英国"),
    Airport(100, "CDG", "巴黎戴高乐机场", "巴黎", "法国"),
    Airport(101, "FRA", "法兰克福机场", "法兰克福", "德国"),
    Airport(102, "DXB", "迪拜国际机场", "迪拜", "阿联酋"),
    Airport(103, "DOH", "多哈哈马德国际机场", "多哈", "卡塔尔"),
]


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

CHINA_PROVINCES = {
    "北京", "上海", "广东", "四川", "重庆", "浙江", "江苏", "湖北", "陕西", "云南", "福建",
    "海南", "山东", "湖南", "河南", "天津", "辽宁", "黑龙江", "吉林", "内蒙古", "新疆",
    "甘肃", "宁夏", "青海", "山西", "江西", "安徽", "广西", "贵州", "西藏", "河北",
    "香港", "澳门",
}
LONG_HAUL_PROVINCES = {"澳大利亚", "美国", "英国", "法国", "德国", "阿联酋", "卡塔尔"}


def money(value: Decimal | int | float | str) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def round_to_10(value: Decimal) -> Decimal:
    return (value / Decimal("10")).quantize(Decimal("1"), rounding=ROUND_HALF_UP) * Decimal("10")


def sql_string(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def sql_value(value: Any) -> str:
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
    return AIRPORTS[: min(cfg.airport_count, len(AIRPORTS))]


def selected_airlines(cfg: ProfileConfig) -> list[Airline]:
    return AIRLINES[: min(cfg.airline_count, len(AIRLINES))]


def estimate_duration(dep: Airport, arr: Airport, rng: random.Random) -> int:
    if dep.province in LONG_HAUL_PROVINCES or arr.province in LONG_HAUL_PROVINCES:
        return rng.randint(520, 780)
    if dep.province not in CHINA_PROVINCES or arr.province not in CHINA_PROVINCES:
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

    for dep, arr in FORCED_ROUTES:
        if dep in by_code and arr in by_code and dep != arr:
            seen.add((dep, arr))
            routes.append((dep, arr))

    candidates = [
        (dep.code, arr.code)
        for dep in airports
        for arr in airports
        if dep.code != arr.code and dep.city != arr.city
    ]
    rng.shuffle(candidates)
    for dep, arr in candidates:
        if len(routes) >= cfg.route_count:
            break
        if (dep, arr) not in seen:
            seen.add((dep, arr))
            routes.append((dep, arr))

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
        flight.cabins.append({"flight_id": flight.id, "cabin_class": cabin, "price": price, "total_seats": total})


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
) -> Flight:
    dep = airports_by_code[dep_code]
    arr = airports_by_code[arr_code]
    duration = estimate_duration(dep, arr, rng)
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
        publish_status="PUBLISHED",
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
) -> list[Flight]:
    airports_by_code = {airport.code: airport for airport in airports}
    flights: list[Flight] = []

    special_specs = [
        ("CAN", "PVG", 1, time(7, 10), "popular", "cheap_morning", "ON_TIME", 1, "cheap"),
        ("CAN", "SHA", 1, time(9, 20), "popular", "morning", "ON_TIME", 1, None),
        ("CAN", "PVG", 1, time(14, 30), "popular", "delayed", "DELAYED", 1, None),
        ("CAN", "SHA", 1, time(23, 40), "popular", "cross_day", "ON_TIME", 1, None),
        ("CAN", "PVG", 1, time(11, 0), "popular", "cancelled", "CANCELLED", 1, None),
        ("CAN", "PVG", 0, time(10, 30), "popular", "soon", "ON_TIME", 1, None),
        ("SHA", "PEK", 2, time(8, 0), "popular", "economy_sold_business_available", "ON_TIME", 1, "sold_out"),
        ("PVG", "PKX", 2, time(12, 10), "popular", "all_sold_out", "ON_TIME", 1, "sold_out"),
        ("SZX", "HGH", 3, time(18, 35), "medium", "last_seat", "ON_TIME", 1, "last_one"),
        ("PEK", "SHA", 4, time(16, 50), "popular", "multi_locked", "ON_TIME", 1, None),
        ("HKG", "SIN", 5, time(22, 30), "medium", "non_direct", "ON_TIME", 0, None),
        ("CAN", "PVG", 2, time(20, 5), "popular", "waitlist_target", "ON_TIME", 1, "sold_out"),
    ]
    for dep, arr, day, depart_time, tier, tag, status, direct, scarcity in special_specs:
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
                flights.append(make_flight(ids, rng, airports_by_code, airlines, dep, arr, depart_at, tier))

    flights.sort(key=lambda item: (item.departure_time, item.flight_no, item.id))
    return flights


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
                "real_name": f"{surnames[index % len(surnames)]}{first_names[index % len(first_names)]}{first_names[(index + 3) % len(first_names)]}",
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
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    ticket_amount, airport_fee, fuel_fee, service_fee, total_amount = order_amount(seats)
    order_id = ids.next_order()
    order = {
        "id": order_id,
        "order_no": f"TD{profile.upper()}{created_at:%Y%m%d}{sequence:08d}",
        "user_id": user_id,
        "flight_id": flight.id,
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

    economy_sold = by_tag.get("economy_sold_business_available")
    if economy_sold:
        sell_many(economy_sold, available_seats(economy_sold, "ECONOMY"), "economy-sold")

    all_sold = by_tag.get("all_sold_out")
    if all_sold:
        sell_many(all_sold, available_seats(all_sold), "all-sold")

    last_seat = by_tag.get("last_seat")
    if last_seat:
        seats = available_seats(last_seat)
        sell_many(last_seat, seats[:-1], "last-seat")

    multi_locked = by_tag.get("multi_locked")
    if multi_locked:
        seats = available_seats(multi_locked, "ECONOMY")[:2]
        created = base_dt - timedelta(minutes=5)
        append_order(multi_locked, seats, "PENDING_PAYMENT", created, None, base_dt + timedelta(minutes=10))

    waitlist_target = by_tag.get("waitlist_target")
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

    status_cycle = [
        "ISSUED", "ISSUED", "ISSUED", "PENDING_PAYMENT", "CANCELLED",
        "REFUNDED", "CHANGE_PENDING", "CHANGED",
    ]
    protected_tags = {"all_sold_out", "last_seat", "economy_sold_business_available", "waitlist_target", "multi_locked"}
    sellable_flights = [
        flight
        for flight in flights
        if flight.status in {"ON_TIME", "DELAYED"} and flight.tag not in protected_tags
    ]
    same_route: dict[tuple[int, int], list[Flight]] = {}
    for flight in sellable_flights:
        same_route.setdefault((flight.departure_airport_id, flight.arrival_airport_id), []).append(flight)

    attempts = 0
    while len(orders) < cfg.order_count and attempts < cfg.order_count * 20:
        attempts += 1
        status = status_cycle[len(orders) % len(status_cycle)]
        count = 2 if rng.random() < 0.18 else 1
        if rng.random() < 0.04:
            count = 3
        flight = rng.choice(sellable_flights)
        if status == "CHANGED":
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
            if status == "REFUNDED":
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
) -> list[str]:
    errors: list[str] = []
    orders_by_id = {order["id"]: order for order in orders}
    order_passenger_seat_pairs = {(op["order_id"], op["seat_id"]) for op in order_passengers}
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
                elif (seat.locked_by_order_id, seat.id) not in order_passenger_seat_pairs:
                    errors.append(f"seat {seat.id}: SOLD order passenger missing")
            if seat.status == "LOCKED":
                if seat.locked_by_order_id is None or seat.lock_expire_time is None:
                    errors.append(f"seat {seat.id}: LOCKED without order or expiry")
                elif seat.locked_by_order_id not in orders_by_id:
                    errors.append(f"seat {seat.id}: LOCKED order missing")
    for order in orders:
        if order["status"] in {"CANCELLED", "REFUNDED"}:
            for flight in flights:
                for seat in flight.seats:
                    if seat.locked_by_order_id == order["id"] and seat.status in {"SOLD", "LOCKED"}:
                        errors.append(f"order {order['id']}: cancelled/refunded order still owns seat {seat.id}")
    for waitlist in waitlists:
        if waitlist["status"] == "SUCCESS" and waitlist["ticket_order_id"] is None:
            errors.append(f"waitlist {waitlist['id']}: SUCCESS without ticket order")
    return errors


def generated_id_values(dataset: dict[str, Any]) -> list[int]:
    cfg: ProfileConfig = dataset["cfg"]
    ids: list[int] = []
    ids.extend(user["id"] for user in dataset["users"])
    ids.extend(passenger["id"] for passenger in dataset["passengers"] if passenger["id"] >= cfg.id_base)
    ids.extend(flight.id for flight in dataset["flights"])
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


def build_dataset(profile: str, seed: int, base_date: date) -> dict[str, Any]:
    cfg = PROFILES[profile]
    rng = random.Random(seed)
    ids = Ids(cfg.id_base, cfg.id_range)
    airports = selected_airports(cfg)
    airlines = selected_airlines(cfg)
    users, passengers = generate_users_and_passengers(ids, profile, cfg)
    flights = generate_flights(ids, rng, cfg, base_date, airports, airlines)
    base_dt = datetime.combine(base_date, time(9, 0))
    orders, order_passengers, refunds, changes, waitlists, waitlist_passengers = build_orders(
        ids, rng, profile, cfg, base_dt, flights, passengers
    )
    ai_sessions, ai_messages, ai_recommendations = build_ai_records(ids, profile, base_dt, flights)
    dataset = {
        "profile": profile,
        "seed": seed,
        "base_date": base_date,
        "cfg": cfg,
        "airports": airports,
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
    }
    errors = validate_dataset(flights, orders, order_passengers, waitlists)
    errors.extend(validate_generated_id_ranges(dataset))
    if errors:
        raise RuntimeError("Generated dataset failed validation:\n" + "\n".join(errors[:20]))
    return dataset


def rows_for_users(users: list[dict[str, Any]]) -> list[tuple[Any, ...]]:
    return [
        (
            user["id"], user["email"], user["phone"], user["password_hash"], user["nickname"], user["real_name"],
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


def render_sql(dataset: dict[str, Any]) -> str:
    profile = dataset["profile"]
    seed = dataset["seed"]
    base_date = dataset["base_date"]
    cfg: ProfileConfig = dataset["cfg"]
    start_id = cfg.id_base
    end_id = cfg.id_base + cfg.id_range
    generated_ids = generated_id_values(dataset)
    generated_id_min = min(generated_ids) if generated_ids else None
    generated_id_max = max(generated_ids) if generated_ids else None
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
        "-- Reset only rows owned by this seed profile.",
        f"DELETE FROM ai_recommendation_record WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM ai_chat_message WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM ai_chat_session WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM waitlist_passenger WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM waitlist_order WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM change_record WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM refund_record WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM order_passenger WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM ticket_order WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM flight_seat WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM flight_cabin WHERE flight_id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM flight WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM passenger WHERE id BETWEEN {start_id} AND {end_id};",
        f"DELETE FROM users WHERE id BETWEEN {start_id} AND {end_id};",
        "",
    ]

    airport_rows = [(a.id, a.code, a.name, a.city, a.province, "ENABLED") for a in dataset["airports"]]
    airline_rows = [(a.id, a.code, a.name, None, "ENABLED") for a in dataset["airlines"]]
    statements.extend(
        upsert_statement("airline", ["id", "code", "name", "logo_url", "status"], airline_rows, ["name", "logo_url", "status"])
    )
    statements.extend(
        upsert_statement("airport", ["id", "code", "name", "city", "province", "status"], airport_rows, ["name", "city", "province", "status"])
    )

    statements.extend(
        insert_statement(
            "users",
            [
                "id", "email", "phone", "password_hash", "nickname", "real_name", "role", "status",
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

    flight_rows = [
        (
            flight.id, flight.flight_no, flight.airline_id, flight.departure_airport_id, flight.arrival_airport_id,
            flight.departure_time, flight.arrival_time, flight.duration_minutes, flight.base_price,
            flight.remaining_seats, flight.total_seats, flight.status, flight.publish_status, flight.direct_flag,
            flight.baggage_allowance, flight.punctuality_rate,
        )
        for flight in dataset["flights"]
    ]
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
        (cabin["flight_id"], cabin["cabin_class"], cabin["price"], cabin["total_seats"])
        for flight in dataset["flights"]
        for cabin in flight.cabins
    ]
    statements.extend(insert_statement("flight_cabin", ["flight_id", "cabin_class", "price", "total_seats"], cabin_rows))

    order_rows = [
        (
            order["id"], order["order_no"], order["user_id"], order["flight_id"], order["status"],
            order["ticket_amount"], order["airport_fee"], order["fuel_fee"], order["service_fee"], order["total_amount"],
            order["pay_time"], order["expire_time"], order["created_at"], order["updated_at"],
        )
        for order in dataset["orders"]
    ]
    statements.extend(
        insert_statement(
            "ticket_order",
            [
                "id", "order_no", "user_id", "flight_id", "status", "ticket_amount", "airport_fee",
                "fuel_fee", "service_fee", "total_amount", "pay_time", "expire_time", "created_at", "updated_at",
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
    statements.extend(
        insert_statement(
            "order_passenger",
            ["id", "order_id", "passenger_id", "passenger_name", "passenger_type", "seat_id", "seat_no", "ticket_price", "created_at"],
            op_rows,
        )
    )

    refund_rows = [
        (
            refund["id"], refund["order_id"], refund["user_id"], refund["reason"], refund["refund_amount"],
            refund["fee_amount"], refund["status"], refund["created_at"],
        )
        for refund in dataset["refunds"]
    ]
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

    waitlist_rows = [
        (
            waitlist["id"], waitlist["waitlist_no"], waitlist["user_id"], waitlist["flight_id"],
            waitlist["ticket_order_id"], waitlist["passenger_count"], waitlist["cabin_class"], waitlist["pay_amount"],
            waitlist["status"], waitlist["expire_time"], waitlist["paid_at"], waitlist["refund_amount"],
            waitlist["refund_time"], waitlist["last_skip_reason"], waitlist["created_at"], waitlist["updated_at"],
        )
        for waitlist in dataset["waitlists"]
    ]
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

    summary = {
        "profile": profile,
        "seed": seed,
        "baseDate": base_date.isoformat(),
        "seedIdRange": f"{start_id}-{end_id}",
        "generatedIdMin": generated_id_min,
        "generatedIdMax": generated_id_max,
        "airports": len(dataset["airports"]),
        "airlines": len(dataset["airlines"]),
        "routes": len({(flight.departure_airport_id, flight.arrival_airport_id) for flight in dataset["flights"]}),
        "flights": len(dataset["flights"]),
        "cabins": len(cabin_rows),
        "seats": len(seat_rows),
        "users": len(dataset["users"]) + 1,
        "passengers": len(dataset["passengers"]),
        "orders": len(dataset["orders"]),
        "refunds": len(dataset["refunds"]),
        "changes": len(dataset["changes"]),
        "waitlists": len(dataset["waitlists"]),
        "aiSessions": len(dataset["ai_sessions"]),
        "validation": {
            "flightRemainingMatchesAvailableSeats": True,
            "flightCabinTotalsMatchFlightTotal": True,
            "seatPricesMatchCabinPrices": True,
            "soldSeatsHaveOrderPassenger": True,
            "lockedSeatsHaveOrderAndExpiry": True,
            "successWaitlistsHaveTicketOrder": True,
            "generatedIdsWithinProfileRange": True,
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
        "--output",
        help="Output SQL path. Defaults to backend/src/main/resources/db/seed/seed-<profile>.sql.",
    )
    args = parser.parse_args()

    base_date = parse_base_date(args.seed, args.base_date)
    dataset = build_dataset(args.profile, args.seed, base_date)
    sql = render_sql(dataset)
    output = Path(args.output) if args.output else Path("backend/src/main/resources/db/seed") / f"seed-{args.profile}.sql"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(sql, encoding="utf-8")
    print(f"Generated {output} ({len(sql.encode('utf-8'))} bytes)")


if __name__ == "__main__":
    main()
