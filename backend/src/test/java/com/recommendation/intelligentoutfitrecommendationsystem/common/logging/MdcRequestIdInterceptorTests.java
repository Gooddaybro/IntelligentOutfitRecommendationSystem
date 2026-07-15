package com.recommendation.intelligentoutfitrecommendationsystem.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MdcRequestIdInterceptorTests {

    @Test
    void preHandleUsesIncomingRequestIdAndWritesResponseHeader() throws Exception {
        MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "req-test-001");

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("requestId")).isEqualTo("req-test-001");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-test-001");

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void preHandleGeneratesRequestIdWhenHeaderIsMissing() throws Exception {
        MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("requestId")).isNotBlank();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(MDC.get("requestId"));

        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void preHandleUsesValidTraceparentAndWritesResponseHeader() throws Exception {
        MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        request.addHeader("traceparent", traceparent);

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("traceparent")).isEqualTo(traceparent);
        assertThat(response.getHeader("traceparent")).isEqualTo(traceparent);
        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(MDC.get("traceparent")).isNull();
    }

    @Test
    void preHandleGeneratesTraceparentWhenIncomingValueIsInvalid() throws Exception {
        MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("traceparent", "00-00000000000000000000000000000000-0000000000000000-01");

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("traceparent"))
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01")
                .isEqualTo(response.getHeader("traceparent"));
        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void preHandleReplacesRequestIdContainingLogInjectionCharacters() throws Exception {
        MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "trusted\nforged-log-line");

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("requestId"))
                .isNotEqualTo("trusted\nforged-log-line")
                .matches("[0-9a-f-]{36}");
        interceptor.afterCompletion(request, response, new Object(), null);
    }

    @Test
    void preHandleReplacesRequestIdLongerThan128Characters() throws Exception {
        MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Request-Id", "a".repeat(129));

        interceptor.preHandle(request, response, new Object());

        assertThat(MDC.get("requestId")).hasSize(36);
        interceptor.afterCompletion(request, response, new Object(), null);
    }
}
