from __future__ import annotations

import importlib.util
import subprocess
import sys
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
    def test_component_dependency_resolution(self):
        self.assertEqual(generator.resolve_components("orders"), {"reference", "users", "flights", "orders"})
        with self.assertRaises(ValueError):
            generator.resolve_components("orders", auto_dependencies=False)

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

    def test_shell_help_and_syntax(self):
        shell = ROOT / "scripts/test-data.sh"
        subprocess.run(["bash", "-n", str(shell)], check=True)
        result = subprocess.run([str(shell), "--help"], check=True, text=True, capture_output=True)
        self.assertIn("doctor", result.stdout)
        self.assertIn("--ref REF", result.stdout)


if __name__ == "__main__":
    unittest.main()
