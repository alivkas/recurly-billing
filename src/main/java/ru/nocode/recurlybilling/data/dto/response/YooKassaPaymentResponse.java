package ru.nocode.recurlybilling.data.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class YooKassaPaymentResponse {

    private String id;
    private String status;
    private Amount amount;
    @JsonProperty("confirmation")
    private Confirmation confirmation;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("paid_at")
    private LocalDateTime paidAt;
    @JsonProperty("metadata")
    private java.util.Map<String, Object> metadata;
    private PaymentMethod paymentMethod;

    @Data
    public static class Amount {
        private String value;
        private String currency;
    }

    @Data
    public static class Confirmation {
        private String type;
        @JsonProperty("confirmation_url")
        private String confirmationUrl;
        @JsonProperty("return_url")
        private String returnUrl;
    }

    @Data
    public static class PaymentMethod {
        private String id;
        private String type;
    }
}
