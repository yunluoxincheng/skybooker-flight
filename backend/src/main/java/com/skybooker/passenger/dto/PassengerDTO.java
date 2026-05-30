package com.skybooker.passenger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PassengerDTO {
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String name;

    @NotBlank(message = "证件号不能为空")
    @Size(max = 50, message = "证件号长度不能超过50")
    private String idCardNo;

    private String passengerType = "ADULT";

    @Size(max = 30, message = "手机号长度不能超过30")
    private String phone;
}
