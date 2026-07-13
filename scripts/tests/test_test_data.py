from __future__ import annotations

import importlib.util
import os
import re
import subprocess
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


generator = load_module("skybooker_generate_test_data", ROOT / "scripts/generate_test_data.py")
validator = load_module("skybooker_validate_test_data", ROOT / "scripts/validate_test_data.py")
cleaner = load_module("skybooker_clean_test_data", ROOT / "scripts/clean_test_data.py")


class TestDataToolTest(unittest.TestCase):
    def test_reference_catalog_is_complete_and_profiles_do_not_truncate_it(self):
        self.assertEqual(len(generator.AIRPORTS), 311)
        self.assertEqual(len(generator.AIRPORTS), len({airport.code for airport in generator.AIRPORTS}))
        self.assertEqual(len(generator.AIRPORTS), len({airport.id for airport in generator.AIRPORTS}))
        self.assertEqual(
            len(generator.AIRPORTS),
            generator.AIRPORT_CATALOG_METADATA["airportReferenceCount"],
        )
        self.assertEqual(sum(airport.scope == "mainland" for airport in generator.AIRPORTS), 270)
        self.assertEqual(sum(airport.scope == "special_region" for airport in generator.AIRPORTS), 15)
        self.assertEqual(sum(airport.scope == "international" for airport in generator.AIRPORTS), 26)
        self.assertEqual(
            generator.AIRPORT_CATALOG_METADATA["mainlandAirportCodeSetSha256"],
            generator.EXPECTED_MAINLAND_AIRPORT_CODE_SET_SHA256,
        )
        codes = {airport.code for airport in generator.AIRPORTS}
        self.assertTrue({"XNT", "EJN", "HSF", "LIJ", "BZJ", "JRJ", "XYI", "AHJ", "DEJ", "ZAT", "APJ", "DHH"} <= codes)
        self.assertTrue({"AMS", "AUH", "YVR", "YYZ"} <= codes)
        self.assertTrue({"DAX", "WEN"}.isdisjoint(codes))
        for airport in generator.AIRPORTS:
            if airport.scope in {"mainland", "special_region"}:
                self.assertIsNone(re.search(r"[A-Za-z]", airport.name), airport.code)
                self.assertIsNone(re.search(r"[A-Za-z]", airport.city), airport.code)
        configured_codes = set(generator.FLIGHT_AIRPORT_PRIORITY)
        for routes in (generator.FORCED_ROUTES, generator.BIDIRECTIONAL_HUB_PAIRS, generator.INTERNATIONAL_GATEWAY_PAIRS):
            configured_codes.update(code for route in routes for code in route)
        self.assertTrue(configured_codes <= codes)
        for profile, config in generator.PROFILES.items():
            self.assertEqual(generator.selected_airports(config), generator.AIRPORTS, profile)
            flight_airports = generator.selected_flight_airports(config)
            self.assertGreaterEqual(len(flight_airports), 20)
            self.assertLessEqual(len(flight_airports), len(generator.AIRPORTS))

    def test_shell_entrypoint_defaults_base_date_to_today_override(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "seed.sql"
            summary_file = Path(directory) / "summary.json"
            env = os.environ.copy()
            env["SKYBOOKER_BASE_DATE"] = "2030-01-02"
            subprocess.run(
                [
                    str(ROOT / "scripts/test-data.sh"), "generate",
                    "--source-dir", str(ROOT), "--profile", "dev",
                    "--components", "reference", "--output", str(output),
                    "--summary-file", str(summary_file),
                ],
                check=True, text=True, capture_output=True, env=env,
            )
            self.assertEqual(validator.extract_summary(output.read_text())["baseDate"], "2030-01-02")

    def test_flight_coverage_summary_has_inbound_outbound_and_gateway_routes(self):
        components = generator.resolve_components("flights")
        dataset = generator.build_dataset(
            "test", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("direct")
        )
        summary, errors = validator.validate(generator.render_sql(dataset))
        self.assertEqual(errors, [])
        self.assertTrue(summary["flightCoverageRequired"])
        self.assertEqual(summary["airportsWithoutOutboundFlights"], [])
        self.assertEqual(summary["airportsWithoutInboundFlights"], [])
        self.assertEqual(summary["airportsWithoutMainlandGateway"], [])
        self.assertEqual(summary["airportsWithOutboundFlights"], summary["flightAirportCount"])
        self.assertEqual(summary["airportsWithInboundFlights"], summary["flightAirportCount"])
        coverage_checks = validator.database_coverage_checks(summary, summary["batchKey"])
        check_names = {name for name, _ in coverage_checks}
        self.assertIn("airport_outbound_coverage", check_names)
        self.assertIn("airport_inbound_coverage", check_names)
        self.assertIn("international_gateway_coverage", check_names)

    def test_reference_rows_resolve_database_ids_by_code(self):
        components = generator.resolve_components("flights")
        dataset = generator.build_dataset(
            "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("direct")
        )
        sql = generator.render_sql(dataset)
        self.assertIn("INSERT INTO airport(code, name, city, province, status)", sql)
        self.assertIn("INSERT INTO airline(code, name, logo_url, status)", sql)
        self.assertIn("UPDATE airport a SET a.status='DISABLED' WHERE a.code IN ('DAX', 'WEN')", sql)
        self.assertIn("DELETE a FROM airport a WHERE a.code IN ('DAX', 'WEN')", sql)
        self.assertEqual(summary := validator.extract_summary(sql), validator.validate(sql)[0])
        self.assertEqual(summary["legacyManagedAirportCodes"], ["DAX", "WEN"])
        reference_checks = dict(validator.database_reference_checks(summary))
        self.assertIn("airport_catalog_fields_mismatch", reference_checks)
        self.assertIn("legacy_managed_airports_still_enabled", reference_checks)
        first_flight = dataset["flights"][0]
        airport_by_id = {airport.id: airport for airport in dataset["airports"]}
        airline_by_id = {airline.id: airline for airline in dataset["airlines"]}
        self.assertIn(
            f"(SELECT id FROM airport WHERE code='{airport_by_id[first_flight.departure_airport_id].code}')",
            sql,
        )
        self.assertIn(
            f"(SELECT id FROM airline WHERE code='{airline_by_id[first_flight.airline_id].code}')",
            sql,
        )

    def generate_cli_summary(self, arguments):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "seed.sql"
            result = subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts/generate_test_data.py"),
                    "--profile", "dev",
                    "--seed", "20260707",
                    *arguments,
                    "--output", str(output),
                ],
                check=True,
                text=True,
                capture_output=True,
            )
            self.assertIn("Generated", result.stdout)
            sql = output.read_text(encoding="utf-8")
            summary, errors = validator.validate(sql)
            self.assertEqual(errors, [])
            return summary

    def test_component_dependency_resolution(self):
        self.assertEqual(generator.resolve_components("orders"), {"reference", "users", "flights", "orders"})
        with self.assertRaises(ValueError):
            generator.resolve_components("orders", auto_dependencies=False)
        refund_components = generator.resolve_components(
            "flights,orders,changes",
            scenarios=generator.resolve_scenarios("refund"),
        )
        self.assertIn("refunds", refund_components)
        with self.assertRaisesRegex(ValueError, "scenarios require components: refunds"):
            generator.resolve_components(
                "reference,users,flights,orders,changes",
                auto_dependencies=False,
                scenarios=generator.resolve_scenarios("refund"),
            )

    def test_cli_default_scenarios_do_not_expand_selected_components(self):
        flights_orders = self.generate_cli_summary(["--components", "flights,orders"])
        self.assertEqual(
            set(flights_orders["components"]),
            {"reference", "users", "flights", "orders"},
        )
        self.assertFalse(flights_orders["scenariosExplicit"])
        self.assertEqual(flights_orders["refunds"], 0)
        self.assertEqual(flights_orders["changes"], 0)
        self.assertEqual(flights_orders["waitlists"], 0)

        users = self.generate_cli_summary(["--components", "users"])
        self.assertEqual(set(users["components"]), {"reference", "users"})
        self.assertEqual(users["flights"], 0)
        self.assertEqual(users["orders"], 0)

        explicit_refund = self.generate_cli_summary([
            "--components", "flights,orders", "--scenarios", "refund",
        ])
        self.assertTrue(explicit_refund["scenariosExplicit"])
        self.assertIn("refunds", explicit_refund["components"])
        self.assertGreater(explicit_refund["refunds"], 0)

        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [
                    sys.executable,
                    str(ROOT / "scripts/generate_test_data.py"),
                    "--profile", "dev",
                    "--seed", "20260707",
                    "--components", "flights,orders",
                    "--scenarios", "refund",
                    "--no-auto-dependencies",
                    "--output", str(Path(directory) / "seed.sql"),
                ],
                text=True,
                capture_output=True,
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("components require dependencies", result.stderr)

    def test_connecting_dataset_is_deterministic_and_complete(self):
        components = generator.resolve_components("all")
        scenarios = generator.resolve_scenarios("connecting")
        first = generator.build_dataset("dev", 20260707, date(2026, 7, 7), components, scenarios)
        second = generator.build_dataset("dev", 20260707, date(2026, 7, 7), components, scenarios)
        self.assertEqual(generator.render_sql(first), generator.render_sql(second))
        self.assertGreaterEqual(len(first["connecting_itineraries"]), 5)
        self.assertEqual(len(first["connecting_changes"]), 1)
        self.assertEqual(len(first["connecting_change_segments"]), 4)
        self.assertEqual(generator.validate_generated_id_ranges(first), [])

    def test_partial_render_is_dynamic(self):
        components = generator.resolve_components("flights")
        dataset = generator.build_dataset(
            "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("connecting")
        )
        summary, errors = validator.validate(generator.render_sql(dataset))
        self.assertEqual(errors, [])
        self.assertEqual(summary["components"], ["flights", "reference"])
        self.assertIn("INSERT INTO connecting_itinerary", generator.render_sql(dataset))
        self.assertNotIn("INSERT INTO ticket_order", generator.render_sql(dataset))

    def test_flights_component_never_references_unrendered_orders(self):
        components = generator.resolve_components("flights")
        dataset = generator.build_dataset(
            "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("all")
        )
        self.assertEqual(dataset["orders"], [])
        self.assertTrue(all(seat.locked_by_order_id is None for flight in dataset["flights"] for seat in flight.seats))
        sql = generator.render_sql(dataset)
        self.assertNotIn("INSERT INTO ticket_order", sql)
        self.assertNotIn("locked_by_order_id=", sql)

    def test_all_components_single_scenarios_validate(self):
        components = generator.resolve_components("all")
        for scenario_name in ("direct", "refund", "change", "waitlist", "connecting"):
            dataset = generator.build_dataset(
                "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios(scenario_name)
            )
            _, errors = validator.validate(generator.render_sql(dataset))
            self.assertEqual(errors, [], scenario_name)

    def test_scenarios_are_scoped(self):
        components = generator.resolve_components("all")
        delayed = generator.build_dataset(
            "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("delayed")
        )
        self.assertTrue(any(flight.tag == "delayed" for flight in delayed["flights"]))
        self.assertEqual(delayed["orders"], [])
        direct = generator.build_dataset(
            "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("direct")
        )
        self.assertEqual(len(direct["waitlists"]), 0)
        self.assertEqual(len(direct["connecting_itineraries"]), 0)
        self.assertTrue(all(flight.status != "DELAYED" for flight in direct["flights"]))

    def test_ownership_render_is_batch_scoped(self):
        components = generator.resolve_components("users")
        dataset = generator.build_dataset(
            "dev", 20260707, date(2026, 7, 7), components, generator.resolve_scenarios("direct")
        )
        sql = generator.render_sql(dataset)
        self.assertIn("INSERT INTO test_data_batch", sql)
        self.assertIn("INSERT INTO test_data_ownership", sql)
        self.assertIn("skybooker:dev", sql)
        self.assertNotIn("DELETE FROM users WHERE id BETWEEN", sql)
        self.assertIn("COLLATE utf8mb4_unicode_ci", sql)

    def test_cleanup_is_profile_scoped_and_respects_foreign_keys(self):
        sql = cleaner.render("dev", "all")
        self.assertNotIn("TRUNCATE", sql.upper())
        self.assertNotIn("FOREIGN_KEY_CHECKS", sql.upper())
        self.assertLess(sql.index("connecting_change_segment"), sql.index("ticket_order"))
        self.assertLess(sql.index("ticket_order"), sql.index("flight_seat"))
        self.assertLess(sql.index("flight_seat"), sql.index("DELETE target FROM flight "))
        self.assertNotIn("DELETE FROM airline", sql)
        self.assertNotIn("DELETE FROM airport", sql)
        users_sql = cleaner.render("dev", "users")
        self.assertIn("ticket_order", users_sql)
        self.assertIn("refund_record", users_sql)
        self.assertIn("connecting_change_record", users_sql)
        self.assertIn("s.status IN ('LOCKED', 'SOLD')", users_sql)

    def test_database_metadata_query_checks_seed_and_source_ref(self):
        query = validator.database_metadata_query({
            "profile": "dev",
            "batchKey": "skybooker:dev",
            "seed": 20260707,
            "sourceRef": None,
        })
        self.assertIn("b.seed=20260707", query)
        self.assertIn("b.source_ref <=> NULL", query)

    def test_shell_help_and_syntax(self):
        shell = ROOT / "scripts/test-data.sh"
        subprocess.run(["bash", "-n", str(shell)], check=True)
        result = subprocess.run([str(shell), "--help"], check=True, text=True, capture_output=True)
        self.assertIn("doctor", result.stdout)
        self.assertIn("--ref REF", result.stdout)


if __name__ == "__main__":
    unittest.main()
