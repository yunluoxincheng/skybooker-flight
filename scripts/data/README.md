# Test-data reference catalogs

These files are versioned inputs to `generate_test_data.py`; the generator does
not call an external service while producing a seed.

- `airports-cn.json` contains the fixed 2025 year-end snapshot of all 270
  CAAC-certificated mainland transport airports. `airports-international.json`
  contains 15 Hong Kong, Macau and Taiwan airports plus 26 international hubs.
- Airport rows retain the original seed IDs where possible and carry stable
  IATA codes, display names, city, province/country, and an explicit `scope`.
- The mainland scope follows the CAAC 2025 national civil transport airport
  statistics bulletin, CAAC airport directory and China civil aviation eAIP.
  Hong Kong, Macau and Taiwan are counted separately. IATA codes and Chinese
  display fields are supplemented from public airport records.
- CI verifies the mainland catalog as an exact set. The checksum is SHA-256 of
  the IATA codes sorted ascending, with one `\n` after every code. The expected
  value is `533d61d6bb6ead694c794091ea777875bd584f9d0c8b78a610a8e2d5e3a230a8`.
- `airlines.json` is the maintained seed carrier catalog with stable IATA
  airline codes.

When refreshing a catalog, update the metadata date/source, preserve existing
IDs and codes, run the generator and static validation matrix, and review the
coverage summary before committing the change.

Official mainland references:

- CAAC 2025 statistics bulletin: <http://fuwu.caacnews.com.cn/1/1/202602/t20260226_1393527.html>
- CAAC airport directory: <http://www.caac.gov.cn/GYMH/MHGK/MYJC/>
- China civil aviation eAIP: <https://www.eaipchina.cn/Content/Vue/index.html>
