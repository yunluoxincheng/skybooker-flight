package com.skybooker.admin.vo;

import com.skybooker.flight.vo.FlightVO;

public record ConnectingFlightPairVO(
        FlightVO firstSegment,
        FlightVO secondSegment,
        long transferMinutes
) {
}
