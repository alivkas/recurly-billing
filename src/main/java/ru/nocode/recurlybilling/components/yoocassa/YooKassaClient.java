package ru.nocode.recurlybilling.components.yoocassa;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.nocode.recurlybilling.data.dto.request.YooKassaPaymentRequest;
import ru.nocode.recurlybilling.data.dto.response.YooKassaPaymentResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YooKassaClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${yookassa.shop-id}")
    private String shopId;

    @Value("${yookassa.secret-key}")
    private String secretKey;

    @Value("${yookassa.api-url:https://api.yookassa.ru/v3}")
    private String apiUrl;

    public YooKassaPaymentResponse createPayment(YooKassaPaymentRequest request) {
        String url = apiUrl + "/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String auth = shopId + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

        ResponseEntity<YooKassaPaymentResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                YooKassaPaymentResponse.class
        );

        return response.getBody();
    }

    public YooKassaPaymentResponse getPayment(String paymentId) {
        String url = apiUrl + "/payments/" + paymentId;

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String auth = shopId + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<YooKassaPaymentResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                YooKassaPaymentResponse.class
        );

        return response.getBody();
    }

    public YooKassaPaymentResponse cancelPayment(String paymentId) {
        String url = apiUrl + "/payments/" + paymentId + "/cancel";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String auth = shopId + ":" + secretKey;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<YooKassaPaymentResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                YooKassaPaymentResponse.class
        );

        return response.getBody();
    }
}
