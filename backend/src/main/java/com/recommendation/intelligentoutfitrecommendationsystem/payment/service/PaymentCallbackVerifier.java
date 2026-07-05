package com.recommendation.intelligentoutfitrecommendationsystem.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.ProviderPaymentCallback;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 真实支付渠道回调的轻量验签器。
 *
 * 这里刻意只实现 HMAC-SHA256 骨架，避免 CI 依赖外部支付 SDK；生产接入某个服务商时，
 * 对应策略可以替换为该渠道官方验签实现，但仍需返回同一个已验证回调 DTO。
 */
@Component
public class PaymentCallbackVerifier {

    private static final String SIGNATURE_HEADER = "X-Payment-Signature";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PaymentProviderProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentCallbackVerifier(PaymentProviderProperties properties) {
        this.properties = properties;
    }

    public PaymentCallbackVerification verify(String channel, String rawBody, HttpServletRequest request) {
        if (rawBody == null || rawBody.isBlank()) {
            return PaymentCallbackVerification.invalid("callback body is blank");
        }
        String secret = properties.callbackSecret(channel);
        if (secret == null || secret.isBlank()) {
            return PaymentCallbackVerification.invalid("callback secret is not configured");
        }
        String signature = request == null ? null : request.getHeader(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank() || !secureEquals(signature, hmacHex(rawBody, secret))) {
            return PaymentCallbackVerification.invalid("invalid signature");
        }
        try {
            return PaymentCallbackVerification.valid(parseCallback(rawBody));
        } catch (RuntimeException exception) {
            return PaymentCallbackVerification.invalid("callback payload is invalid");
        }
    }

    private ProviderPaymentCallback parseCallback(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            return new ProviderPaymentCallback(
                    requiredText(root, "paymentNo"),
                    requiredText(root, "orderNo"),
                    requiredAmount(root, "amount"),
                    requiredText(root, "status"),
                    requiredText(root, "providerTradeNo"),
                    optionalText(root, "transactionId"),
                    rawBody,
                    optionalDateTime(root, "paidAt")
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("callback payload is invalid", exception);
        }
    }

    private String requiredText(JsonNode root, String field) {
        String value = optionalText(root, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private String optionalText(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private BigDecimal requiredAmount(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
    }

    private LocalDateTime optionalDateTime(JsonNode root, String field) {
        String value = optionalText(root, field);
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private String hmacHex(String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("payment callback signature cannot be calculated", exception);
        }
    }

    private boolean secureEquals(String provided, String expected) {
        return MessageDigest.isEqual(
                provided.trim().getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}
