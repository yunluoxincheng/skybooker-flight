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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConnectingOrderIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired Clock clock;
    String token;

    @BeforeEach void login() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user1@example.com\",\"password\":\"User@123456\"}"))
                .andExpect(status().isOk()).andReturn();
        token=json.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    @AfterEach void cleanupFeatureData() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_connecting_second_leg");
        jdbc.update("DELETE cs FROM connecting_change_segment cs JOIN connecting_change_record cr ON cr.id=cs.change_record_id WHERE cr.user_id=2");
        jdbc.update("DELETE FROM connecting_change_record WHERE user_id=2");
        jdbc.update("DELETE p FROM order_segment_passenger p JOIN ticket_order_segment s ON s.id=p.order_segment_id JOIN ticket_order o ON o.id=s.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE s FROM ticket_order_segment s JOIN ticket_order o ON o.id=s.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE r FROM refund_record r JOIN ticket_order o ON o.id=r.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE p FROM order_passenger p JOIN ticket_order o ON o.id=p.order_id WHERE o.client_request_id IS NOT NULL");
        jdbc.update("DELETE FROM ticket_order WHERE client_request_id IS NOT NULL");
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
        assertEquals("SOLD",jdbc.queryForObject("SELECT status FROM flight_seat WHERE id=?",String.class,replacementSeat));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM flight_seat WHERE id IN (?,?) AND status='AVAILABLE'",Integer.class,pair.firstSeat,pair.secondSeat));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM connecting_change_segment cs JOIN connecting_change_record cr ON cr.id=cs.change_record_id WHERE cr.order_id=?",Integer.class,orderId));
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,String body)throws Exception{return json.readTree(mvc.perform(request.header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private long createOrder(Pair pair)throws Exception{return response(post("/api/orders/connecting"),connectingRequest(pair,UUID.randomUUID())).path("data").path("id").asLong();}
    private String connectingRequest(Pair p,UUID request)throws Exception{return json.writeValueAsString(java.util.Map.of("clientRequestId",request.toString(),"segments",java.util.List.of(java.util.Map.of("flightId",p.firstFlight,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",p.firstSeat))),java.util.Map.of("flightId",p.secondFlight,"items",java.util.List.of(java.util.Map.of("passengerId",1,"seatId",p.secondSeat))))));}
    private Pair createPair(int transfer){LocalDateTime dep=LocalDateTime.now(clock).plusDays(9).withSecond(0).withNano(0);long first=flight("CNX"+System.nanoTime(),3,1,dep,dep.plusHours(2));long second=flight("CNX"+System.nanoTime(),1,5,dep.plusHours(2).plusMinutes(transfer),dep.plusHours(4).plusMinutes(transfer));return new Pair(first,second,seat(first),seat(second),dep);}
    private long flight(String no,long from,long to,LocalDateTime dep,LocalDateTime arr){KeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO flight(flight_no,airline_id,departure_airport_id,arrival_airport_id,departure_time,arrival_time,duration_minutes,base_price,remaining_seats,total_seats,status,publish_status,direct_flag) VALUES(?,1,?,?,?,?,120,500,4,4,'ON_TIME','PUBLISHED',1)",Statement.RETURN_GENERATED_KEYS);p.setString(1,no);p.setLong(2,from);p.setLong(3,to);p.setTimestamp(4,Timestamp.valueOf(dep));p.setTimestamp(5,Timestamp.valueOf(arr));return p;},k);return k.getKey().longValue();}
    private long seat(long flight){KeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO flight_seat(flight_id,seat_no,cabin_class,seat_type,price,status) VALUES(?,'1A','ECONOMY','WINDOW',500,'AVAILABLE')",Statement.RETURN_GENERATED_KEYS);p.setLong(1,flight);return p;},k);return k.getKey().longValue();}
    record Pair(long firstFlight,long secondFlight,long firstSeat,long secondSeat,LocalDateTime departure){}
}
