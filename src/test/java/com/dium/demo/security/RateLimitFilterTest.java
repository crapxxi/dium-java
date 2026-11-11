package com.dium.demo.security;

import com.dium.demo.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private final RateLimitFilter filter =
            new RateLimitFilter(new RateLimitProperties(true, 3, Duration.ofMinutes(1), true));

    private MockHttpServletResponse call(String method, String path, String ip, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void blocksAfterCapacityIsExhausted() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1", chain).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("POST", "/api/v1/auth/login", "10.0.0.1", chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("\"status\":429");
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void limitsAreSeparatePerIpAndPerEndpoint() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 3; i++) {
            call("POST", "/api/v1/auth/login", "10.0.0.1", chain);
        }
        assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1", chain).getStatus()).isEqualTo(429);

        // другой IP и другой эндпоинт не затронуты
        assertThat(call("POST", "/api/v1/auth/login", "10.0.0.2", chain).getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/auth/register", "10.0.0.1", chain).getStatus()).isEqualTo(200);
    }

    @Test
    void coversAllSensitiveAuthEndpoints() {
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/register")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/activate")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/resend-code")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/forgot-password")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/reset-password")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/login")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/2fa/verify")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/2fa/enable")).isTrue();
        assertThat(RateLimitFilter.isLimited("/api/v1/auth/2fa/disable")).isTrue();

        assertThat(RateLimitFilter.isLimited("/api/v1/venues")).isFalse();
        assertThat(RateLimitFilter.isLimited("/api/v1/orders")).isFalse();
    }

    @Test
    void doesNotLimitOtherEndpointsOrGetRequests() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/v1/venues", "10.0.0.1", chain).getStatus()).isEqualTo(200);
            assertThat(call("GET", "/api/v1/auth/me", "10.0.0.1", chain).getStatus()).isEqualTo(200);
        }
    }

    @Test
    void usesFirstForwardedAddressBehindProxy() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("172.16.0.1"); // адрес прокси одинаковый для всех
            request.addHeader("X-Forwarded-For", "203.0.113." + i + ", 172.16.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
