package com.recommendation.intelligentoutfitrecommendationsystem.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个 HTTP 请求注入 requestId。
 *
 * requestId 会进入日志 MDC，并写回响应头，便于 Reqable、前端日志和服务端日志互相定位。
 */
@Component
public class MdcRequestIdInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_TRACEPARENT = "traceparent";

    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]+");
    private static final Pattern SAFE_TRACEPARENT = Pattern.compile(
            "00-(?!0{32})[0-9a-f]{32}-(?!0{16})[0-9a-f]{16}-[0-9a-f]{2}");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!isSafeRequestId(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (!isSafeTraceparent(traceparent)) {
            traceparent = newTraceparent();
        }
        MDC.put(MDC_TRACEPARENT, traceparent);
        response.setHeader(TRACEPARENT_HEADER, traceparent);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_TRACEPARENT);
    }

    private boolean isSafeRequestId(String requestId) {
        return requestId != null
                && !requestId.isBlank()
                && requestId.length() <= MAX_REQUEST_ID_LENGTH
                && SAFE_REQUEST_ID.matcher(requestId).matches();
    }

    private boolean isSafeTraceparent(String traceparent) {
        return traceparent != null && SAFE_TRACEPARENT.matcher(traceparent).matches();
    }

    private String newTraceparent() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
