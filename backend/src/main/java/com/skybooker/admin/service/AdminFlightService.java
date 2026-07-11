package com.skybooker.admin.service;

import com.skybooker.admin.dto.AdminFlightQueryDTO;
import com.skybooker.admin.dto.AdminCreateConnectingFlightsDTO;
import com.skybooker.admin.dto.FlightCabinDTO;
import com.skybooker.admin.dto.FlightFormDTO;
import com.skybooker.admin.dto.ConnectingItineraryFormDTO;
import com.skybooker.admin.support.AdminListQuerySupport;
import com.skybooker.admin.vo.ConnectingFlightPairVO;
import com.skybooker.admin.vo.ConnectingItineraryAdminVO;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.response.PageResponse;
import com.skybooker.flight.entity.Flight;
import com.skybooker.flight.entity.FlightCabin;
import com.skybooker.flight.entity.FlightSeat;
import com.skybooker.flight.enums.FlightSort;
import com.skybooker.flight.mapper.FlightCabinMapper;
import com.skybooker.flight.mapper.FlightMapper;
import com.skybooker.flight.vo.FlightCabinVO;
import com.skybooker.flight.vo.FlightVO;
import com.skybooker.itinerary.entity.ConnectingItinerary;
import com.skybooker.itinerary.mapper.ItineraryMapper;
import com.skybooker.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminFlightService {

    private static final Set<String> VALID_STATUSES = Set.of("ON_TIME", "DELAYED", "CANCELLED");
    private static final Set<String> VALID_PUBLISH_STATUSES = Set.of("PUBLISHED", "DRAFT");

    private final FlightMapper flightMapper;
    private final FlightCabinMapper flightCabinMapper;
    private final ItineraryMapper itineraryMapper;

    public PageResponse<FlightVO> listFlights(AdminFlightQueryDTO query) {
        AdminListQuerySupport.normalize(query);
        validateEnum(query.getStatus(), VALID_STATUSES);
        validateEnum(query.getPublishStatus(), VALID_PUBLISH_STATUSES);
        validateDate(query.getDepartureDateStart());
        validateDate(query.getDepartureDateEnd());

        int offset = AdminListQuerySupport.offset(query);
        List<FlightVO> records = flightMapper.searchFlightsAdmin(query, offset, query.getSize());
        long total = flightMapper.countFlightsAdmin(query);
        return new PageResponse<>(records, total, query.getPage(), query.getSize());
    }

    /** 枚举白名单校验:空值放过(不过滤),非空但非法返回 VALIDATION_ERROR(不 500)。 */
    private void validateEnum(String value, Set<String> valid) {
        if (value != null && !value.isBlank() && !valid.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    /** 日期格式校验(yyyy-MM-dd):空值放过,非法格式返回 VALIDATION_ERROR(不 500)。 */
    private void validateDate(String value) {
        if (value == null || value.isBlank()) return;
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Transactional
    public FlightVO createFlight(FlightFormDTO dto) {
        validateFlightForm(dto, true);
        Flight flight = insertFlight(dto);
        return flightMapper.findFlightByIdAnyStatus(flight.getId());
    }

    @Transactional
    public ConnectingFlightPairVO createConnectingFlights(AdminCreateConnectingFlightsDTO dto) {
        FlightFormDTO first = dto.getFirstSegment();
        FlightFormDTO second = dto.getSecondSegment();
        validateFlightForm(first, true);
        validateFlightForm(second, true);
        long transferMinutes = validateConnection(first.getDepartureAirportId(), first.getArrivalAirportId(),
                first.getArrivalTime(), second.getDepartureAirportId(), second.getArrivalAirportId(), second.getDepartureTime());

        Flight firstFlight = insertFlight(first);
        Flight secondFlight = insertFlight(second);
        createManaged(firstFlight.getId(), secondFlight.getId(), "DRAFT");
        return new ConnectingFlightPairVO(
                flightMapper.findFlightByIdAnyStatus(firstFlight.getId()),
                flightMapper.findFlightByIdAnyStatus(secondFlight.getId()),
                transferMinutes);
    }

    @Transactional
    public FlightVO updateFlight(Long id, FlightFormDTO dto) {
        Flight existing = flightMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        boolean hasSeats = flightMapper.existsSeatsByFlightId(id);
        boolean hasOrders = flightMapper.existsOrdersByFlightId(id);
        boolean hasCabinConfig = flightCabinMapper.existsByFlightId(id);

        if (hasSeats || hasOrders) {
            boolean structuralChange = !existing.getFlightNo().equals(dto.getFlightNo())
                    || !existing.getAirlineId().equals(dto.getAirlineId())
                    || !existing.getDepartureAirportId().equals(dto.getDepartureAirportId())
                    || !existing.getArrivalAirportId().equals(dto.getArrivalAirportId())
                    || !existing.getDepartureTime().equals(dto.getDepartureTime())
                    || !existing.getArrivalTime().equals(dto.getArrivalTime())
                    || !existing.getTotalSeats().equals(dto.getTotalSeats())
                    || existing.getBasePrice().compareTo(dto.getBasePrice()) != 0;
            if (structuralChange) {
                throw new BusinessException(ErrorCode.FLIGHT_HAS_INVENTORY);
            }
        } else if (hasCabinConfig && !existing.getTotalSeats().equals(dto.getTotalSeats())) {
            // 仅有舱位配置(尚未生成座位):禁止改 totalSeats,避免 cabin 总数与航班总数漂移
            throw new BusinessException(ErrorCode.FLIGHT_HAS_INVENTORY);
        }

        validateFlightForm(dto, false);
        Flight updated = toEntity(dto);
        updated.setId(id);
        // Ordinary maintenance must never reclassify legacy stopover rows. New
        // itinerary composition only accepts direct rows; migration is a separate concern.
        updated.setDirectFlag(existing.getDirectFlag());
        updated.setRemainingSeats(existing.getRemainingSeats());
        flightMapper.updateFlight(updated);
        return flightMapper.findFlightByIdAnyStatus(id);
    }

    public List<ConnectingItineraryAdminVO> listConnectingItineraries() {
        return itineraryMapper.findAllManaged().stream().map(this::toManagedVO).toList();
    }

    @Transactional
    public ConnectingItineraryAdminVO createConnectingItinerary(ConnectingItineraryFormDTO dto) {
        validateManagedFlights(dto.getFirstFlightId(), dto.getSecondFlightId());
        if (itineraryMapper.findManagedPair(dto.getFirstFlightId(), dto.getSecondFlightId()) != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return toManagedVO(createManaged(dto.getFirstFlightId(), dto.getSecondFlightId(), "DRAFT"));
    }

    @Transactional
    public ConnectingItineraryAdminVO updateConnectingItinerary(Long id, ConnectingItineraryFormDTO dto) {
        ConnectingItinerary existing = requireManaged(id);
        if ("PUBLISHED".equals(existing.getPublishStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATE_INVALID);
        }
        validateManagedFlights(dto.getFirstFlightId(), dto.getSecondFlightId());
        ConnectingItinerary duplicate = itineraryMapper.findManagedPair(dto.getFirstFlightId(), dto.getSecondFlightId());
        if (duplicate != null && !id.equals(duplicate.getId())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        itineraryMapper.updateManagedFlights(id, dto.getFirstFlightId(), dto.getSecondFlightId());
        return toManagedVO(requireManaged(id));
    }

    @Transactional
    public ConnectingItineraryAdminVO setConnectingItineraryPublished(Long id, boolean published) {
        ConnectingItinerary itinerary = requireManaged(id);
        if (published) {
            validateManagedFlights(itinerary.getFirstFlightId(), itinerary.getSecondFlightId());
            Flight first = flightMapper.findById(itinerary.getFirstFlightId());
            Flight second = flightMapper.findById(itinerary.getSecondFlightId());
            if (!"PUBLISHED".equals(first.getPublishStatus()) || !"PUBLISHED".equals(second.getPublishStatus())) {
                throw new BusinessException(ErrorCode.FLIGHT_NOT_SELLABLE);
            }
        }
        itineraryMapper.updateManagedStatus(id, published ? "PUBLISHED" : "DRAFT");
        return toManagedVO(requireManaged(id));
    }

    @Transactional
    public void publishFlight(Long id) {
        Flight flight = flightMapper.findById(id);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        flight.setPublishStatus("PUBLISHED");
        flightMapper.updateFlight(flight);
    }

    @Transactional
    public void unpublishFlight(Long id) {
        Flight flight = flightMapper.findById(id);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        flight.setPublishStatus("DRAFT");
        flightMapper.updateFlight(flight);
    }

    @Transactional
    public void setCabins(Long flightId, List<FlightCabinDTO> cabinDTOs) {
        Flight flight = flightMapper.findById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        // 舱位配置仅在未生成座位时可写(与 updateFlight 同守护),避免与已售座位/订单快照不一致
        if (flightMapper.existsSeatsByFlightId(flightId)) {
            throw new BusinessException(ErrorCode.FLIGHT_HAS_INVENTORY);
        }
        if (cabinDTOs == null || cabinDTOs.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        validateCabinConfig(cabinDTOs, flight.getTotalSeats());

        flightCabinMapper.deleteByFlightId(flightId);
        List<FlightCabin> cabins = new ArrayList<>();
        for (FlightCabinDTO dto : cabinDTOs) {
            FlightCabin c = new FlightCabin();
            c.setFlightId(flightId);
            c.setCabinClass(dto.getCabinClass());
            c.setPrice(dto.getPrice());
            c.setTotalSeats(dto.getTotalSeats());
            cabins.add(c);
        }
        flightCabinMapper.batchUpsert(cabins);
    }

    public List<FlightCabinVO> getCabins(Long flightId) {
        if (flightMapper.findById(flightId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return flightCabinMapper.findByFlightId(flightId);
    }

    @Transactional
    public void generateSeats(Long flightId) {
        Flight flight = flightMapper.findById(flightId);
        if (flight == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (flight.getTotalSeats() <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (flight.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (flightMapper.existsSeatsByFlightId(flightId)) throw new BusinessException(ErrorCode.SEAT_ALREADY_EXISTS);

        List<FlightCabinVO> cabins = flightCabinMapper.findByFlightId(flightId);
        if (cabins.isEmpty()) {
            // 未显式配置舱位:回退为单经济舱(base_price / total_seats),保持"创建即可生成"的兼容流程
            FlightCabin defaultCabin = new FlightCabin();
            defaultCabin.setFlightId(flightId);
            defaultCabin.setCabinClass("ECONOMY");
            defaultCabin.setPrice(flight.getBasePrice());
            defaultCabin.setTotalSeats(flight.getTotalSeats());
            flightCabinMapper.batchUpsert(List.of(defaultCabin));
            cabins = flightCabinMapper.findByFlightId(flightId);
        } else {
            // defense in depth:防止历史脏数据或绕过 service 的调用导致 cabin 总数与航班总数漂移
            int cabinSum = cabins.stream().mapToInt(FlightCabinVO::getTotalSeats).sum();
            if (cabinSum != flight.getTotalSeats()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        String[] letters = {"A", "B", "C", "D", "E", "F"};
        List<FlightSeat> seats = new ArrayList<>();
        int seatCount = 0;
        int row = 1;
        // cabins 已按 FIRST→BUSINESS→ECONOMY 排序;每舱从新排起,舱位间不混排
        for (FlightCabinVO cabin : cabins) {
            int letterIdx = 0;
            for (int i = 0; i < cabin.getTotalSeats(); i++) {
                String letter = letters[letterIdx];
                FlightSeat seat = new FlightSeat();
                seat.setFlightId(flightId);
                seat.setSeatNo(row + letter);
                seat.setCabinClass(cabin.getCabinClass());
                seat.setSeatType(getSeatType(letter));
                seat.setPrice(cabin.getPrice());
                seat.setStatus("AVAILABLE");
                seats.add(seat);
                seatCount++;
                letterIdx++;
                if (letterIdx == letters.length) {
                    letterIdx = 0;
                    row++;
                }
            }
            // 当前排未填满则下一舱另起新排,保证舱位物理边界(符合真实机型布局)
            if (letterIdx != 0) {
                row++;
            }
        }

        flightMapper.batchInsertFlightSeats(seats);
        flightMapper.setRemainingSeats(flightId, seatCount);
    }

    /**
     * 舱位配置校验:
     * - 舱位不重复
     * - 各舱 totalSeats 之和 = flight.totalSeats(配置与实际生成座位 1:1,无遗漏无溢出)
     * - 数量符合现实布局 ECONOMY ≥ BUSINESS ≥ FIRST(仅校验存在的舱位,避免经济舱比公务舱少等反常配置)
     */
    private void validateCabinConfig(List<FlightCabinDTO> cabinDTOs, int flightTotalSeats) {
        Map<String, Integer> totals = new HashMap<>();
        int sum = 0;
        for (FlightCabinDTO dto : cabinDTOs) {
            // 白名单兜底(不依赖 List 元素级 @Valid 触发)
            if (!FlightSort.isValidCabin(dto.getCabinClass())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            if (dto.getTotalSeats() == null || dto.getTotalSeats() <= 0
                    || dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            if (totals.put(dto.getCabinClass(), dto.getTotalSeats()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            sum += dto.getTotalSeats();
        }
        if (sum != flightTotalSeats) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Integer first = totals.get("FIRST");
        Integer business = totals.get("BUSINESS");
        Integer economy = totals.get("ECONOMY");
        if (first != null && business != null && first > business) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (business != null && economy != null && business > economy) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (first != null && economy != null && first > economy) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String getSeatType(String letter) {
        return switch (letter) {
            case "A", "F" -> "WINDOW";
            case "C", "D" -> "AISLE";
            default -> "NORMAL";
        };
    }

    private void validateFlightForm(FlightFormDTO dto, boolean requireDirect) {
        // A flight row is one independently sellable nonstop segment. A managed
        // connecting itinerary references two such rows without copying inventory.
        if (requireDirect && Boolean.FALSE.equals(dto.getDirectFlag())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!flightMapper.existsAirlineById(dto.getAirlineId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!flightMapper.existsAirportById(dto.getDepartureAirportId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!flightMapper.existsAirportById(dto.getArrivalAirportId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getDepartureAirportId().equals(dto.getArrivalAirportId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!dto.getArrivalTime().isAfter(dto.getDepartureTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String status = dto.getStatus() != null ? dto.getStatus() : "ON_TIME";
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String publishStatus = dto.getPublishStatus() != null ? dto.getPublishStatus() : "DRAFT";
        if (!VALID_PUBLISH_STATUSES.contains(publishStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getDurationMinutes() == null || dto.getDurationMinutes() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getBasePrice() == null || dto.getBasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getTotalSeats() == null || dto.getTotalSeats() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private Flight toEntity(FlightFormDTO dto) {
        Flight f = new Flight();
        f.setFlightNo(dto.getFlightNo());
        f.setAirlineId(dto.getAirlineId());
        f.setDepartureAirportId(dto.getDepartureAirportId());
        f.setArrivalAirportId(dto.getArrivalAirportId());
        f.setDepartureTime(dto.getDepartureTime());
        f.setArrivalTime(dto.getArrivalTime());
        f.setDurationMinutes(dto.getDurationMinutes());
        f.setBasePrice(dto.getBasePrice());
        f.setTotalSeats(dto.getTotalSeats());
        f.setStatus(dto.getStatus() != null ? dto.getStatus() : "ON_TIME");
        f.setPublishStatus(dto.getPublishStatus() != null ? dto.getPublishStatus() : "DRAFT");
        f.setDirectFlag(true);
        f.setBaggageAllowance(dto.getBaggageAllowance());
        f.setPunctualityRate(dto.getPunctualityRate());
        return f;
    }

    private Flight insertFlight(FlightFormDTO dto) {
        Flight flight = toEntity(dto);
        flight.setRemainingSeats(0);
        flightMapper.insertFlight(flight);
        return flight;
    }

    private long validateManagedFlights(Long firstFlightId, Long secondFlightId) {
        if (firstFlightId == null || secondFlightId == null || firstFlightId.equals(secondFlightId)) {
            throw new BusinessException(ErrorCode.INVALID_CONNECTION);
        }
        Flight first = flightMapper.findById(firstFlightId);
        Flight second = flightMapper.findById(secondFlightId);
        if (first == null || second == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!Boolean.TRUE.equals(first.getDirectFlag()) || !Boolean.TRUE.equals(second.getDirectFlag())) {
            throw new BusinessException(ErrorCode.INVALID_CONNECTION);
        }
        return validateConnection(first.getDepartureAirportId(), first.getArrivalAirportId(), first.getArrivalTime(),
                second.getDepartureAirportId(), second.getArrivalAirportId(), second.getDepartureTime());
    }

    private long validateConnection(Long originId, Long connectionArrivalId, java.time.LocalDateTime firstArrival,
                                    Long connectionDepartureId, Long destinationId, java.time.LocalDateTime secondDeparture) {
        long transferMinutes = Duration.between(firstArrival, secondDeparture).toMinutes();
        if (!connectionArrivalId.equals(connectionDepartureId) || originId.equals(destinationId)
                || transferMinutes < 90 || transferMinutes > 360) {
            throw new BusinessException(ErrorCode.INVALID_CONNECTION);
        }
        return transferMinutes;
    }

    private ConnectingItinerary createManaged(Long firstFlightId, Long secondFlightId, String status) {
        ConnectingItinerary itinerary = new ConnectingItinerary();
        itinerary.setFirstFlightId(firstFlightId);
        itinerary.setSecondFlightId(secondFlightId);
        itinerary.setPublishStatus(status);
        itinerary.setCreatedBy(SecurityUtil.getCurrentUserId());
        itineraryMapper.insertManaged(itinerary);
        return itineraryMapper.findManagedById(itinerary.getId());
    }

    private ConnectingItinerary requireManaged(Long id) {
        ConnectingItinerary itinerary = itineraryMapper.findManagedById(id);
        if (itinerary == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return itinerary;
    }

    private ConnectingItineraryAdminVO toManagedVO(ConnectingItinerary itinerary) {
        FlightVO first = flightMapper.findFlightByIdAnyStatus(itinerary.getFirstFlightId());
        FlightVO second = flightMapper.findFlightByIdAnyStatus(itinerary.getSecondFlightId());
        int transfer = (int) Duration.between(first.getArrivalTime(), second.getDepartureTime()).toMinutes();
        return new ConnectingItineraryAdminVO(itinerary.getId(), first, second, itinerary.getPublishStatus(), transfer,
                itinerary.getCreatedBy(), itinerary.getCreatedAt(), itinerary.getUpdatedAt());
    }
}
