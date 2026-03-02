package ru.nocode.recurlybilling.data.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InvoiceResponse {
    private String id;
    private Long amountCents;
    private String status;
    private String paymentMethod;
    private LocalDateTime createdAt;
}
