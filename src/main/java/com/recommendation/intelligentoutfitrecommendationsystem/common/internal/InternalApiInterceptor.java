package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Internal API 鉴权拦截器，通过 X-Internal-Token 保护 Java 后端暴露给 Python AI 服务的内部接口。
 */
@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-Internal-Token";

    private final InternalApiProperties properties;

    public InternalApiInterceptor(InternalApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expectedToken = properties.token();
        String actualToken = request.getHeader(HEADER_NAME);
        if (expectedToken != null && expectedToken.equals(actualToken)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"data\":null,\"errorCode\":\"internal_unauthorized\",\"message\":\"invalid internal token\"}");
        return false;
    }
}
