package ru.nocode.recurlybilling.components.yoocassa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class YooKassaSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public boolean isValid(String signature, String requestBody, String secretKey) {
        if (signature == null || signature.isBlank() || requestBody == null || secretKey == null) {
            log.warn("Missing parameters for YooKassa signature validation");
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);

            byte[] expectedHash = mac.doFinal(requestBody.getBytes(StandardCharsets.UTF_8));

            String cleanSignature = signature.startsWith("hmac-sha256=")
                    ? signature.substring("hmac-sha256=".length())
                    : signature;

            byte[] providedHash = java.util.HexFormat.of().parseHex(cleanSignature);
            return MessageDigest.isEqual(expectedHash, providedHash);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Crypto error during YooKassa signature validation", e);
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid hex format in YooKassa signature: {}", signature);
            return false;
        }
    }
}