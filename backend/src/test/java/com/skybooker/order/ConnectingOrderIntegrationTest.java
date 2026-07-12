package com.skybooker.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.common.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConnectingOrderIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired Clock clock;
    String token; String adminToken;

    @BeforeEach void login() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk()).andReturn();
        token=json.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        var admin=mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"SkyBooker@Init2026!\"}"))
                .andExpect(status().isOk()).andReturn();
        adminToken=json.readTree(admin.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    @AfterEach void cleanupFeatureData() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_connecting_second_leg");
        jdbc.execute("DROP TRIGGER IF EXISTS test_slow_connecting_change_status");
        jdbc.update("DELETE cs FROM connecting_change_segment cs JOIN connecting_change_record cr ON cr.id=cs.change_record_id WHERE cr.user_id=2");
        jdbc.update("DELETE FROM connecting_change_record WHERE user_id=2");
        jdbc.update("DELETE p FROM order_segment_passenger p JOIN ticket_order_segment s ON s.id=p.order_segment_id JOIN ticket_order o ON o.id=s.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE s FROM ticket_order_segment s JOIN ticket_order o ON o.id=s.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE r FROM refund_record r JOIN ticket_order o ON o.id=r.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE p FROM order_passenger p JOIN ticket_order o ON o.id=p.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE FROM ticket_order WHERE client_request_id IS NOT NULL");
        jdbc.update("DELETE ci FROM connecting_itinerary ci JOIN flight f ON f.id=ci.first_flight_id OR f.id=ci.second_flight_id WHERE f.flight_no LIKE 'CNX%' OR f.flight_no LIKE 'DIR%'");
        jdbc.update("DELETE s FROM flight_seat s JOIN flight f ON f.id=s.flight_id WHERE f.flight_no LIKE 'CNX%' OR f.flight_no LIKE 'DIR%'");
        jdbc.update("DELETE c FROM flight_cabin c JOIN flight f ON f.id=c.flight_id WHERE f.flight_no LIKE 'CNX%' OR f.flight_no LIKE 'DIR%'");
        jdbc.update("DELETE FROM flight WHERE flight_no LIKE 'CNX%' OR flight_no LIKE 'DIR%'");
    }

    @Test void searchCreatePayAndReadAllSegments() throws Exception {
        Pair pair=createPair(120); String body=connectingRequest(pair, UUID.randomUUID());
        mvc.perform(get("/api/itineraries/search").param("departureCity","北京").param("arrivalCity","广州")
                        .param("departureDate",pair.departure.toLocalDate().toString()).param("passengerCount","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[?(@.journeyType == 'CONNECTING')]").isNotEmpty());
        var created=mvc.perform(post("/api/orders/connecting").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journeyType").value("CONNECTING"))
                .andExpect(jsonPath("$.data.segments",hasSize(2))).andReturn();
        long orderId=json.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ISSUED"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM flight_seat WHERE locked_by_order_id=? AND status='SOLD'",Integer.class,orderId));
    }

    @Test void directOnlyExcludesConnectionsAndQuoteRequiresUserAuthentication() throws Exception {
        Pair pair=createPair(120);
        mvc.perform(get("/api/itineraries/search").param("departureCity","北京").param("arrivalCity","广州")
                        .param("departureDate",pair.departure.toLocalDate().toString()).param("directOnly","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[?(@.journeyType == 'CONNECTING')]").isEmpty());
        mvc.perform(post("/api/itineraries/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmentFlightIds\":["+pair.firstFlight+","+pair.secondFlight+"],\"passengerIds\":[1]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void legacyNonDirectFlightsAreExcludedFromDirectAndConnectingItineraries() throws Exception {
        Pair pair=createPair(120);
        jdbc.update("UPDATE flight SET direct_flag=0 WHERE id=?",pair.firstFlight);
        var result=mvc.perform(get("/api/itineraries/search").param("departureCity","北京").param("arrivalCity","广州")
                        .param("departureDate",pair.departure.toLocalDate().toString()).param("passengerCount","1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode records=json.readTree(result.getResponse().getContentAsString()).path("data").path("records");
        for(JsonNode itinerary:records){
            for(JsonNode segment:itinerary.path("segments")){
                assertEquals(false,segment.path("id").asLong()==pair.firstFlight,
                        "direct_flag=0 flight must not appear in an itinerary");
            }
        }
        var directResult=mvc.perform(get("/api/itineraries/search").param("departureCity","北京").param("arrivalCity","武汉")
                        .param("departureDate",pair.departure.toLocalDate().toString()).param("passengerCount","1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode directRecords=json.readTree(directResult.getResponse().getContentAsString()).path("data").path("records");
        for(JsonNode itinerary:directRecords){
            for(JsonNode segment:itinerary.path("segments")){
                assertEquals(false,segment.path("id").asLong()==pair.firstFlight,
                        "direct_flag=0 flight must not appear as a direct itinerary");
            }
        }
    }

    @Test void rejectsMixedPassengerSets() throws Exception {
        Pair pair=createPair(120);
        String body=json.writeValueAsString(java.util.Map.of("clientRequestId",UUID.randomUUID().toString(),"segments",java.util.List.of(
                java.util.Map.of("flightId",pair.firstFlight,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",pair.firstSeat))),
                java.util.Map.of("flightId",pair.secondFlight,"items",java.util.List.of(java.util.Map.of("passengerId",2,"seatId",pair.secondSeat))))));
        mvc.perform(post("/api/orders/connecting").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(30006));
    }

    @Test void duplicateRequestReturnsSameParentOrder() throws Exception {
        Pair pair=createPair(120); UUID request=UUID.randomUUID(); String body=connectingRequest(pair,request);
        JsonNode first=response(post("/api/orders/connecting"),body); JsonNode second=response(post("/api/orders/connecting"),body);
        assertEquals(first.path("data").path("id").asLong(),second.path("data").path("id").asLong());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE user_id=2 AND client_request_id=?",Integer.class,request.toString()));
    }

    @Test void conflictingReuseOfClientRequestIdIsRejected() throws Exception {
        Pair first=createPair(120); Pair other=createPair(150); UUID request=UUID.randomUUID();
        response(post("/api/orders/connecting"),connectingRequest(first,request));
        mvc.perform(post("/api/orders/connecting").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                        .content(connectingRequest(other,request))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(30007));
    }

    @Test void secondLegLockFailureRollsBackEverything() throws Exception {
        Pair pair=createPair(120);
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_connecting_second_leg");
        jdbc.execute("CREATE TRIGGER test_fail_connecting_second_leg AFTER UPDATE ON flight FOR EACH ROW " +
                "BEGIN IF NEW.id="+pair.firstFlight+" THEN UPDATE flight_seat SET status='SOLD' WHERE id="+pair.secondSeat+"; END IF; END");
        try {
            mvc.perform(post("/api/orders/connecting").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                            .content(connectingRequest(pair,UUID.randomUUID()))).andExpect(status().isBadRequest());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS test_fail_connecting_second_leg");
        }
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,pair.firstSeat));
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,pair.secondSeat));
        assertEquals(4,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,pair.firstFlight));
    }

    @Test void cancellationReleasesBothSegmentsWithoutRefund() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/cancel").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM flight_seat WHERE id IN (?,?) AND status='AVAILABLE'",Integer.class,pair.firstSeat,pair.secondSeat));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM refund_record WHERE order_id=?",Integer.class,orderId));
    }

    @Test void refundReleasesSoldSeatsAcrossBothSegments() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/"+orderId+"/refund").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"联程测试退票\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM flight_seat WHERE id IN (?,?) AND status='AVAILABLE'",Integer.class,pair.firstSeat,pair.secondSeat));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM refund_record WHERE order_id=?",Integer.class,orderId));
    }

    @Test void connectingRefundFulfillsOrdinaryWaitlistOnReleasedSecondLeg() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement statement=connection.prepareStatement(
                "INSERT INTO waitlist_order(waitlist_no,user_id,flight_id,passenger_count,cabin_class,pay_amount,status,paid_at) VALUES(?,2,?,1,'ECONOMY',580,'WAITING',NOW())",
                Statement.RETURN_GENERATED_KEYS); statement.setString(1,"CNXWL"+System.nanoTime()); statement.setLong(2,pair.secondFlight); return statement; },key);
        long waitlistId=key.getKey().longValue();
        jdbc.update("INSERT INTO waitlist_passenger(waitlist_id,passenger_id,passenger_name,passenger_type) SELECT ?,id,name,passenger_type FROM passenger WHERE id=1",waitlistId);
        mvc.perform(post("/api/orders/"+orderId+"/refund").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"联程释放候补测试\"}"))
                .andExpect(status().isOk());
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM waitlist_order WHERE id=?",String.class,waitlistId));
        Long fulfilledOrder=jdbc.queryForObject("SELECT ticket_order_id FROM waitlist_order WHERE id=?",Long.class,waitlistId);
        jdbc.update("UPDATE waitlist_order SET ticket_order_id=NULL WHERE id=?",waitlistId);
        jdbc.update("DELETE FROM waitlist_passenger WHERE waitlist_id=?",waitlistId);
        jdbc.update("DELETE FROM waitlist_order WHERE id=?",waitlistId);
        jdbc.update("DELETE FROM order_passenger WHERE order_id=?",fulfilledOrder);
        jdbc.update("UPDATE flight_seat SET status='AVAILABLE',locked_by_order_id=NULL,lock_expire_time=NULL WHERE locked_by_order_id=?",fulfilledOrder);
        jdbc.update("DELETE FROM ticket_order WHERE id=?",fulfilledOrder);
    }

    @Test void wholeItineraryChangeToDirectProtectsNewSeatAndAuditsSnapshots() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        long replacement=flight("DIR"+System.nanoTime(),3,5,pair.departure.plusHours(4),pair.departure.plusHours(7));
        long replacementSeat=seat(replacement); UUID request=UUID.randomUUID();
        String body=json.writeValueAsString(java.util.Map.of("clientRequestId",request.toString(),"reason","联程改直飞",
                "segments",java.util.List.of(java.util.Map.of("flightId",replacement,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",replacementSeat))))));
        mvc.perform(post("/api/orders/"+orderId+"/connecting-change").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGED")).andExpect(jsonPath("$.data.segments",hasSize(1)));
        assertEquals("DIRECT",jdbc.queryForObject("SELECT journey_type FROM ticket_order WHERE id=?",String.class,orderId));
        assertEquals("SOLD",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,replacementSeat));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM flight_seat WHERE id IN (?,?) AND status='AVAILABLE'",Integer.class,pair.firstSeat,pair.secondSeat));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM connecting_change_segment cs JOIN connecting_change_record cr ON cr.id=cs.change_record_id WHERE cr.order_id=?",Integer.class,orderId));
        long secondReplacement=flight("DIR"+System.nanoTime(),3,5,pair.departure.plusHours(8),pair.departure.plusHours(11));
        long secondSeat=seat(secondReplacement);
        String secondBody=json.writeValueAsString(java.util.Map.of("clientRequestId",UUID.randomUUID().toString(),"reason","直飞后再次整段改签",
                "segments",java.util.List.of(java.util.Map.of("flightId",secondReplacement,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",secondSeat))))));
        mvc.perform(post("/api/orders/"+orderId+"/connecting-change").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.journeyType").value("DIRECT"))
                .andExpect(jsonPath("$.data.segments",hasSize(1)));
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,replacementSeat));
        assertEquals("SOLD",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,secondSeat));
    }

    @Test void adminCanCreateConnectingOrderAndOperationIsAudited() throws Exception {
        Pair pair=createPair(120); UUID request=UUID.randomUUID();
        JsonNode payload=json.readTree(connectingRequest(pair,request));
        ((com.fasterxml.jackson.databind.node.ObjectNode)payload).put("userId",2);
        var result=mvc.perform(post("/api/admin/orders/connecting").header("Authorization","Bearer "+adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.journeyType").value("CONNECTING"))
                .andExpect(jsonPath("$.data.segments",hasSize(2))).andReturn();
        long orderId=json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE target_type='ORDER' AND target_id=? AND action='ORDER_CREATE'",Integer.class,orderId));
        String secondFlightNo=jdbc.queryForObject("SELECT flight_no FROM flight WHERE id=?",String.class,pair.secondFlight);
        mvc.perform(get("/api/admin/orders").header("Authorization","Bearer "+adminToken).param("flightNo",secondFlightNo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[?(@.id == "+orderId+")]").isNotEmpty());
        mvc.perform(get("/api/admin/orders").header("Authorization","Bearer "+adminToken).param("flightKeyword","广州"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[?(@.id == "+orderId+")]").isNotEmpty());
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        String today=LocalDateTime.now(clock).toLocalDate().toString();
        mvc.perform(get("/api/admin/reports/route-performance").header("Authorization","Bearer "+adminToken)
                        .param("startDate",today).param("endDate",today).param("departureCity","北京").param("arrivalCity","广州"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.routeLabel == '北京 - 广州')]").isNotEmpty());
        String departureDay=pair.departure.toLocalDate().toString();
        mvc.perform(get("/api/admin/reports/flight-load-factor").header("Authorization","Bearer "+adminToken)
                        .param("startDate",departureDay).param("endDate",departureDay).param("limit","50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.flightId == "+pair.secondFlight+" && @.soldPassengerCount == 1)]").isNotEmpty());
        mvc.perform(get("/api/admin/dashboard/hot-routes").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.routeLabel == '北京 - 广州')]").isNotEmpty());
        mvc.perform(post("/api/admin/orders/connecting").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test void connectingChangeRejectsSameRequestIdWithDifferentReplacement() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        long first=flight("DIR"+System.nanoTime(),3,5,pair.departure.plusHours(4),pair.departure.plusHours(7));
        long firstSeat=seat(first); UUID request=UUID.randomUUID();
        String original=json.writeValueAsString(java.util.Map.of("clientRequestId",request.toString(),"reason","首次改签",
                "segments",java.util.List.of(java.util.Map.of("flightId",first,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",firstSeat))))));
        response(post("/api/orders/"+orderId+"/connecting-change"),original);
        response(post("/api/orders/"+orderId+"/connecting-change"),original);
        long other=flight("DIR"+System.nanoTime(),3,5,pair.departure.plusDays(1),pair.departure.plusDays(1).plusHours(3));
        long otherSeat=seat(other);
        String conflict=json.writeValueAsString(java.util.Map.of("clientRequestId",request.toString(),"reason","冲突重试",
                "segments",java.util.List.of(java.util.Map.of("flightId",other,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",otherSeat))))));
        mvc.perform(post("/api/orders/"+orderId+"/connecting-change").header("Authorization","Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content(conflict))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(30007));
    }

    @Test void adminConnectingChangeUsesAuditChainAndForceAction() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        long replacement=flight("DIR"+System.nanoTime(),3,5,pair.departure.plusHours(4),pair.departure.plusHours(7));
        long replacementSeat=seat(replacement);
        String body=json.writeValueAsString(java.util.Map.of("clientRequestId",UUID.randomUUID().toString(),"reason","管理员保障改签","force",true,
                "segments",java.util.List.of(java.util.Map.of("flightId",replacement,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",replacementSeat))))));
        mvc.perform(post("/api/admin/orders/"+orderId+"/connecting-change").header("Authorization","Bearer "+adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.journeyType").value("DIRECT"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE target_type='ORDER' AND target_id=? AND action='CHANGE_FORCE' AND reason='管理员保障改签'",Integer.class,orderId));
        mvc.perform(get("/api/admin/orders/"+orderId+"/detail").header("Authorization","Bearer "+adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.timeline[?(@.eventType == 'CONNECTING_CHANGED')]").isNotEmpty());
    }

    @Test void connectingChangeOptionsSupportExplicitFutureWindow() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        LocalDateTime later=pair.departure.plusDays(4);
        long replacement=flight("DIR"+System.nanoTime(),3,5,later,later.plusHours(3));
        mvc.perform(get("/api/orders/"+orderId+"/connecting-change-options").header("Authorization","Bearer "+token)
                        .param("startDate",pair.departure.toLocalDate().toString())
                        .param("endDate",pair.departure.toLocalDate().plusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.segments[0].id == "+replacement+")]").isNotEmpty());
    }

    @Test void concurrentWholeItineraryChangeAndRefund_onlyOneSucceedsWithoutNullResponse() throws Exception {
        Pair pair=createPair(120); long orderId=createOrder(pair);
        mvc.perform(post("/api/orders/"+orderId+"/pay").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        long replacement=flight("DIR"+System.nanoTime(),3,5,pair.departure.plusHours(5),pair.departure.plusHours(8));
        long replacementSeat=seat(replacement);
        String changeBody=json.writeValueAsString(java.util.Map.of("clientRequestId",UUID.randomUUID().toString(),"reason","并发整段改签",
                "segments",java.util.List.of(java.util.Map.of("flightId",replacement,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",replacementSeat))))));

        jdbc.execute("DROP TRIGGER IF EXISTS test_slow_connecting_change_status");
        jdbc.execute("CREATE TRIGGER test_slow_connecting_change_status BEFORE UPDATE ON ticket_order FOR EACH ROW " +
                "BEGIN IF OLD.id="+orderId+" AND NEW.status='CHANGED' THEN DO SLEEP(1); END IF; END");
        CountDownLatch start=new CountDownLatch(1),done=new CountDownLatch(2);
        AtomicReference<Integer> changeStatus=new AtomicReference<>(),refundStatus=new AtomicReference<>();
        AtomicReference<String> changeResponse=new AtomicReference<>(),refundResponse=new AtomicReference<>();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        ExecutorService executor=Executors.newFixedThreadPool(2);
        executor.submit(()->{
            try { start.await(); var response=mvc.perform(post("/api/orders/"+orderId+"/connecting-change")
                    .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(changeBody)).andReturn().getResponse();
                changeStatus.set(response.getStatus()); changeResponse.set(response.getContentAsString());
            } catch(Throwable throwable){ failure.compareAndSet(null,throwable); } finally { done.countDown(); }
        });
        executor.submit(()->{
            try { start.await(); var response=mvc.perform(post("/api/admin/orders/"+orderId+"/refund")
                    .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"并发退款\",\"force\":false}"))
                    .andReturn().getResponse();
                refundStatus.set(response.getStatus()); refundResponse.set(response.getContentAsString());
            } catch(Throwable throwable){ failure.compareAndSet(null,throwable); } finally { done.countDown(); }
        });
        try {
            start.countDown();
            assertEquals(true,done.await(15,TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            jdbc.execute("DROP TRIGGER IF EXISTS test_slow_connecting_change_status");
        }
        if(failure.get()!=null) throw new AssertionError(failure.get());
        assertEquals(java.util.List.of(200,400),java.util.stream.Stream.of(changeStatus.get(),refundStatus.get()).sorted().toList());
        String successfulResponse=changeStatus.get()==200?changeResponse.get():refundResponse.get();
        String rejectedResponse=changeStatus.get()==400?changeResponse.get():refundResponse.get();
        assertEquals(false,successfulResponse.contains("\"data\":null"));
        assertEquals(40001,json.readTree(rejectedResponse).path("code").asInt());

        String finalStatus=jdbc.queryForObject("SELECT status FROM ticket_order WHERE id=?",String.class,orderId);
        assertEquals(true,"CHANGED".equals(finalStatus)||"REFUNDED".equals(finalStatus));
        int refunds=jdbc.queryForObject("SELECT COUNT(*) FROM refund_record WHERE order_id=?",Integer.class,orderId);
        int changes=jdbc.queryForObject("SELECT COUNT(*) FROM connecting_change_record WHERE order_id=?",Integer.class,orderId);
        assertEquals(1,refunds+changes);
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,pair.firstSeat));
        assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,pair.secondSeat));
        assertEquals("CHANGED".equals(finalStatus)?"SOLD":"AVAILABLE",
                jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,replacementSeat));
        java.util.List<Long> segmentFlights=jdbc.queryForList(
                "SELECT flight_id FROM ticket_order_segment WHERE order_id=? ORDER BY segment_no",Long.class,orderId);
        if("CHANGED".equals(finalStatus)){
            assertEquals(0,refunds); assertEquals(1,changes);
            assertEquals(java.util.List.of(replacement),segmentFlights);
            assertEquals(4,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,pair.firstFlight));
            assertEquals(4,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,pair.secondFlight));
            assertEquals(3,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,replacement));
        }else{
            assertEquals(1,refunds); assertEquals(0,changes);
            assertEquals(java.util.List.of(pair.firstFlight,pair.secondFlight),segmentFlights);
            assertEquals(4,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,pair.firstFlight));
            assertEquals(4,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,pair.secondFlight));
            assertEquals(4,jdbc.queryForObject("SELECT remaining_seats FROM flight WHERE id=?",Integer.class,replacement));
        }
        int refundAudits=jdbc.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE target_type='ORDER' AND target_id=? AND action IN ('REFUND','REFUND_FORCE')",Integer.class,orderId);
        assertEquals(refundStatus.get()==200?1:0,refundAudits);
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,String body)throws Exception{return json.readTree(mvc.perform(request.header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private long createOrder(Pair pair)throws Exception{return response(post("/api/orders/connecting"),connectingRequest(pair,UUID.randomUUID())).path("data").path("id").asLong();}
    private String connectingRequest(Pair p,UUID request)throws Exception{return json.writeValueAsString(java.util.Map.of("clientRequestId",request.toString(),"segments",java.util.List.of(java.util.Map.of("flightId",p.firstFlight,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",p.firstSeat))),java.util.Map.of("flightId",p.secondFlight,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",p.secondSeat))))));}
    private Pair createPair(int transfer){LocalDateTime dep=LocalDateTime.now(clock).plusDays(9).withHour(8).withMinute(0).withSecond(0).withNano(0);long first=flight("CNX"+System.nanoTime(),3,1,dep,dep.plusHours(2));long second=flight("CNX"+System.nanoTime(),1,5,dep.plusHours(2).plusMinutes(transfer),dep.plusHours(4).plusMinutes(transfer));jdbc.update("INSERT INTO connecting_itinerary(first_flight_id,second_flight_id,publish_status) VALUES(?,?,'PUBLISHED')",first,second);return new Pair(first,second,seat(first),seat(second),dep);}
    private long flight(String no,long from,long to,LocalDateTime dep,LocalDateTime arr){KeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO flight(flight_no,airline_id,departure_airport_id,arrival_airport_id,departure_time,arrival_time,duration_minutes,base_price,remaining_seats,total_seats,status,publish_status,direct_flag) VALUES(?,1,?,?,?,?,120,500,4,4,'ON_TIME','PUBLISHED',1)",Statement.RETURN_GENERATED_KEYS);p.setString(1,no);p.setLong(2,from);p.setLong(3,to);p.setObject(4,dep);p.setObject(5,arr);return p;},k);return k.getKey().longValue();}
    private long seat(long flight){KeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO flight_seat(flight_id,seat_no,cabin_class,seat_type,price,status) VALUES(?,'1A','ECONOMY','WINDOW',500,'AVAILABLE')",Statement.RETURN_GENERATED_KEYS);p.setLong(1,flight);return p;},k);return k.getKey().longValue();}
    record Pair(long firstFlight,long secondFlight,long firstSeat,long secondSeat,LocalDateTime departure){}
}
