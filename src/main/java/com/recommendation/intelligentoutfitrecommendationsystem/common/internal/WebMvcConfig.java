package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import com.recommendation.intelligentoutfitrecommendationsystem.common.logging.MdcRequestIdInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalApiInterceptor internalApiInterceptor;
    private final MdcRequestIdInterceptor mdcRequestIdInterceptor;

    public WebMvcConfig(
            InternalApiInterceptor internalApiInterceptor,
            MdcRequestIdInterceptor mdcRequestIdInterceptor
    ) {
        this.internalApiInterceptor = internalApiInterceptor;
        this.mdcRequestIdInterceptor = mdcRequestIdInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // requestId 需要先进入 MDC，后续 internal 鉴权失败时日志也能带上同一个链路标识。
        registry.addInterceptor(mdcRequestIdInterceptor)
                .addPathPatterns("/**")
                .order(0);
        registry.addInterceptor(internalApiInterceptor)
                .addPathPatterns("/internal/**")
                .order(1);
    }
}
