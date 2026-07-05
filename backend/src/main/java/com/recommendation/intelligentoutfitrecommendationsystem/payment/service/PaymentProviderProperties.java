package com.recommendation.intelligentoutfitrecommendationsystem.payment.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 支付服务商本地配置。
 *
 * Stage 6 只需要回调签名密钥，不保存生产商户号或私钥；真实 SDK 接入时应继续把敏感
 * 凭证放在环境变量或密钥管理系统中。
 */
@Data
@ConfigurationProperties(prefix = "app.payment.provider")
public class PaymentProviderProperties {

    private Map<String, String> callbackSecrets = new HashMap<>();

    public String callbackSecret(String channel) {
        if (channel == null) {
            return null;
        }
        return callbackSecrets.get(channel.trim().toLowerCase(Locale.ROOT));
    }
}
