package com.skybooker.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.flight.enums.FlightSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LlmIntentParserService {

    private static final int MAX_PASSENGER_COUNT = 9;
    private static final List<String> REQUIRED_FIELDS = List.of("departureCity", "arrivalCity", "departureDate");
    private static final List<String> ALLOWED_CABINS = List.of("ECONOMY", "BUSINESS", "FIRST");

    private final LlmChatClient llmChatClient;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemDefaultZone();

    public void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ParsedCondition parse(String message) {
        if (message == null || message.isBlank()) {
            throw new LlmIntentParseException("Blank message is handled by rule parser");
        }

        String content = llmChatClient.complete(systemPrompt(), message.trim());
        JsonNode root = parseJson(content);

        String departureCity = text(root, "departureCity");
        String arrivalCity = text(root, "arrivalCity");
        LocalDate departureDate = date(root, "departureDate");
        Integer passengerCount = passengerCount(root, "passengerCount");
        if (passengerCount == null) {
            passengerCount = 1;
        }
        String cabinClass = cabin(root, "cabinClass");
        String airlineRaw = text(root, "airlineRaw");
        if (airlineRaw == null) {
            airlineRaw = text(root, "airlineName");
        }
        BigDecimal minPrice = money(root, "minPrice");
        BigDecimal maxPrice = money(root, "maxPrice");
        LocalTime departureTimeStart = time(root, "departureTimeStart");
        LocalTime departureTimeEnd = time(root, "departureTimeEnd");
        Integer maxDurationMinutes = positiveInteger(root, "maxDurationMinutes");
        Boolean directOnly = bool(root, "directOnly");
        String sort = sort(root, "sort");

        List<String> missing = missingFields(departureCity, arrivalCity, departureDate);
        String followUpQuestion = text(root, "followUpQuestion");
        if (!missing.isEmpty() && followUpQuestion == null) {
            followUpQuestion = "请问您想从哪个城市出发，飞往哪个城市，什么时间出发？";
        }

        return ParsedCondition.builder()
                .departureCity(departureCity)
                .arrivalCity(arrivalCity)
                .departureDate(departureDate)
                .passengerCount(passengerCount)
                .cabinClass(cabinClass)
                .airlineRaw(airlineRaw)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .departureTimeStart(departureTimeStart)
                .departureTimeEnd(departureTimeEnd)
                .maxDurationMinutes(maxDurationMinutes)
                .directOnly(directOnly)
                .sort(sort)
                .missingFields(missing)
                .followUpQuestion(followUpQuestion)
                .quickActionLabels(quickActionLabels(root))
                .build();
    }

    private JsonNode parseJson(String content) {
        String json = stripCodeFence(content);
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new LlmIntentParseException("LLM output root is not an object");
            }
            return root;
        } catch (LlmIntentParseException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmIntentParseException("LLM output is not valid JSON", e);
        }
    }

    private String stripCodeFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new LlmIntentParseException("Field " + field + " must be text");
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private LocalDate date(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            throw new LlmIntentParseException("Field " + field + " must be yyyy-MM-dd", e);
        }
    }

    private LocalTime time(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null) return null;
        try {
            return LocalTime.parse(value);
        } catch (DateTimeException e) {
            throw new LlmIntentParseException("Field " + field + " must be HH:mm", e);
        }
    }

    private BigDecimal money(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        try {
            BigDecimal result = value.isNumber()
                    ? value.decimalValue()
                    : new BigDecimal(text(root, field));
            if (result.signum() < 0) {
                throw new LlmIntentParseException("Field " + field + " must not be negative");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new LlmIntentParseException("Field " + field + " must be numeric", e);
        }
    }

    private Integer positiveInteger(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        int result;
        if (value.isInt()) {
            result = value.asInt();
        } else if (value.isTextual()) {
            try {
                result = Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException e) {
                throw new LlmIntentParseException("Field " + field + " must be an integer", e);
            }
        } else {
            throw new LlmIntentParseException("Field " + field + " must be an integer");
        }
        if (result <= 0) {
            throw new LlmIntentParseException("Field " + field + " must be positive");
        }
        return result;
    }

    private Integer passengerCount(JsonNode root, String field) {
        Integer result = positiveInteger(root, field);
        if (result != null && result > MAX_PASSENGER_COUNT) {
            throw new LlmIntentParseException("Field " + field + " is out of range");
        }
        return result;
    }

    private Boolean bool(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) {
            throw new LlmIntentParseException("Field " + field + " must be boolean");
        }
        return value.asBoolean();
    }

    private String cabin(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null) return null;
        String upper = value.toUpperCase();
        if (!ALLOWED_CABINS.contains(upper)) {
            throw new LlmIntentParseException("Field " + field + " is unsupported");
        }
        return upper;
    }

    private String sort(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null) return null;
        FlightSort sort = FlightSort.fromParam(value);
        if (sort == null) {
            throw new LlmIntentParseException("Field " + field + " is unsupported");
        }
        return sort.name();
    }

    private List<String> missingFields(String departureCity, String arrivalCity, LocalDate departureDate) {
        List<String> missing = new ArrayList<>();
        if (departureCity == null) missing.add("departureCity");
        if (arrivalCity == null) missing.add("arrivalCity");
        if (departureDate == null) missing.add("departureDate");
        return missing;
    }

    private List<String> quickActionLabels(JsonNode root) {
        JsonNode value = root.get("quickActionLabels");
        if (value == null || value.isNull()) return List.of();
        if (!value.isArray()) {
            throw new LlmIntentParseException("Field quickActionLabels must be an array");
        }
        List<String> labels = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new LlmIntentParseException("Field quickActionLabels items must be text");
            }
            String label = item.asText().trim();
            if (!label.isEmpty()) {
                labels.add(label);
            }
            if (labels.size() >= 5) break;
        }
        return labels;
    }

    private String systemPrompt() {
        LocalDate today = LocalDate.now(clock);
        return """
                你是机票查询条件解析器。只能返回一个 JSON 对象，不要返回 Markdown 或解释。
                当前日期是 %s。用户提到“今天、明天、后天、大后天、下周、下周一、周末”等相对日期时，
                必须基于这个日期换算为具体 departureDate。
                你不能编造航班、航班号、价格、库存、机场、航空公司、URL 或推荐卡片。
                只输出这些字段，未知字段会被后端忽略：
                departureCity, arrivalCity, departureDate, passengerCount, cabinClass,
                airlineRaw, minPrice, maxPrice, departureTimeStart, departureTimeEnd,
                maxDurationMinutes, directOnly, sort, missingFields, followUpQuestion,
                quickActionLabels。
                departureDate 使用 yyyy-MM-dd；time 字段使用 HH:mm；cabinClass 只能是 ECONOMY、BUSINESS、FIRST；
                sort 只能是 PRICE_ASC、DURATION_ASC、TIME_ASC、SEATS_DESC、PUNCTUAL_DESC 或 DEFAULT。
                如果缺少 departureCity、arrivalCity 或 departureDate，请在 missingFields 中列出这些字段，
                并给出 followUpQuestion。
                """.formatted(today);
    }
}
