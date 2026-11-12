package com.dium.demo.security;

import com.dium.demo.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Ограничивает частоту запросов к эндпоинтам, через которые можно перебирать пароли/коды
 * или рассылать WhatsApp-сообщения. Лимит считается отдельно для каждой пары «IP + эндпоинт».
 * <p>
 * Защиту от перебора конкретного кода дополняет {@code OtpService}
 * (5 попыток на код, 60 секунд между отправками на один номер).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String TWO_FACTOR_PREFIX = AUTH_PREFIX + "2fa/";

    private static final Set<String> LIMITED_PATHS = Set.of(
            AUTH_PREFIX + "register",
            AUTH_PREFIX + "login",
            AUTH_PREFIX + "activate",
            AUTH_PREFIX + "resend-code",
            AUTH_PREFIX + "forgot-password",
            AUTH_PREFIX + "reset-password"
    );

    private final RateLimitProperties properties;
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Math.max(properties.period().toSeconds() * 2, 60), TimeUnit.SECONDS)
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.enabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !isLimited(path(request));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String key = clientIp(request) + "|" + path(request);
        Bucket bucket = buckets.get(key, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format(
                "{\"status\":429,\"message\":\"Слишком много запросов. Попробуйте через %d сек.\",\"timestamp\":%d}",
                retryAfter, System.currentTimeMillis()));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(properties.capacity())
                        .refillGreedy(properties.capacity(), properties.period())
                        .build())
                .build();
    }

    static boolean isLimited(String path) {
        return LIMITED_PATHS.contains(path) || path.startsWith(TWO_FACTOR_PREFIX);
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        // "/login/" и "/login" — один и тот же эндпоинт
        return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.trustForwardedHeader()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
