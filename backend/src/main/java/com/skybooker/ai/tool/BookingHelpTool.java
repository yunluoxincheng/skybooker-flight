package com.skybooker.ai.tool;

import org.springframework.stereotype.Service;

@Service
public class BookingHelpTool {

    public String answer(String message) {
        String text = message == null ? "" : message;
        if (text.contains("退票") || text.contains("退款")) {
            return "退票或退款请在 SkyBooker 订单详情中发起。系统会按订单状态和平台规则处理，具体可退金额以订单页面展示为准。";
        }
        if (text.contains("改签") || text.contains("改期")) {
            return "改签请从订单详情进入改签流程，选择可改签航班并按页面提示确认差价或退款信息。";
        }
        if (text.contains("候补")) {
            return "候补用于目标航班暂不可订时提交排队请求。候补结果、支付和取消请以 SkyBooker 候补订单页面展示为准。";
        }
        if (text.contains("乘机人") || text.contains("乘客")) {
            return "乘机人信息请在个人中心或下单流程中维护。提交订单前请核对姓名、证件号和联系方式。";
        }
        if (text.contains("订单") || text.contains("支付")) {
            return "订单和支付状态请在 SkyBooker 订单列表或订单详情中查看。未支付订单需在页面提示的有效期内完成支付。";
        }
        if (text.contains("账号") || text.contains("账户") || text.contains("登录")) {
            return "账号相关操作请使用 SkyBooker 登录、注册和个人中心页面完成；请勿在聊天中发送密码、验证码或证件照片。";
        }
        if (text.contains("选座")) {
            return "选座请在 SkyBooker 订单详情或值机流程中完成，具体可选座位以航班和舱位规则为准。";
        }
        return "SkyBooker 支持订票、退票、改签、候补、选座、乘机人和订单相关流程。请告诉我您想了解哪个流程，我会按平台固定规则说明。";
    }
}
