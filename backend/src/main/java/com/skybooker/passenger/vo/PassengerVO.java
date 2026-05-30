package com.skybooker.passenger.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PassengerVO {
    private Long id;
    private String name;
    private String idCardNo;
    private String passengerType;
    private String phone;
}
