-- Legacy placeholder.
-- Flyway no longer inserts demo flights or demo orders. Do not rewrite all flight
-- dates in a seeded database because dev/test datasets intentionally span 7/30
-- days and include same-day, cross-day, cancelled, delayed and sold-out cases.
--
-- Regenerate seed SQL instead:
--   python3 scripts/generate_test_data.py --profile dev --seed 20260707 --base-date <YYYY-MM-DD>
--   python3 scripts/generate_test_data.py --profile test --seed 20260707 --base-date <YYYY-MM-DD>

SELECT 'Flyway demo flight dates are no longer refreshed; regenerate db/seed/seed-*.sql instead.' AS message;
