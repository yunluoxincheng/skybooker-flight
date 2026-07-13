# Test-data reference catalogs

These files are versioned inputs to `generate_test_data.py`; the generator does
not call an external service while producing a seed.

- `airports-cn.json` contains the mainland China scheduled-service airport
  catalog. `airports-international.json` contains Hong Kong, Macau, Taiwan and
  curated international hubs.
- Airport rows retain the original seed IDs where possible and carry stable
  IATA codes, display names, city, province/country, and an explicit `scope`.
- The airport catalog is based on the dated OurAirports CSV snapshot recorded
  in each file's `metadata`, filtered to scheduled-service airports with IATA
  codes and excluding heliports. The additional international hubs are listed
  in the metadata notes.
- `airlines.json` is the maintained seed carrier catalog with stable IATA
  airline codes.

When refreshing a catalog, update the metadata date/source, preserve existing
IDs and codes, run the generator and static validation matrix, and review the
coverage summary before committing the change.
