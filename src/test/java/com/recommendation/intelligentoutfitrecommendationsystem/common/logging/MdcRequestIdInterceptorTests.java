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
}
