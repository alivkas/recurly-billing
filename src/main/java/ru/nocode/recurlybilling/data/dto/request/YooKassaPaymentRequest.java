package ru.nocode.recurlybilling.data.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class YooKassaPaymentRequest {

    private Amount amount;
    private String description;
    @JsonProperty("confirmation")
    private Confirmation confirmation;
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    @JsonProperty("capture")
    private Boolean capture = true;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("payment_method_data")
    private PaymentMethodData paymentMethodData;

    @JsonProperty("save_payment_method")
    private Boolean savePaymentMethod;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Amount {
        private Long value;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    public static class Confirmation {
        private String type = "redirect";
        @JsonProperty("return_url")
        private String returnUrl;

        public Confirmation(String returnUrl) {
            this.returnUrl = returnUrl;
        }
    }

    @Data
    public static class PaymentMethodData {
        private String type;

        public PaymentMethodData(String type) {
            this.type = type;
        }
    }
}
