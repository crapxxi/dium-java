package com.dium.demo.security;

import com.dium.demo.services.auth.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /** Единственное место, где допустим временный 2FA-токен (и то — без аутентификации запроса). */
    static final String TWO_FACTOR_VERIFY_PATH = "/api/v1/auth/2fa/verify";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {


        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        final String userPhone;
        final boolean twoFactorPending;

        try {
            twoFactorPending = jwtService.isTwoFactorPending(jwt);
            userPhone = jwtService.extractUsername(jwt);
        } catch (JwtException | IllegalArgumentException e) {
            // Просроченный/повреждённый токен: запрос идёт дальше как анонимный
            // (публичные эндпоинты работают, защищённые вернут 403)
            filterChain.doFilter(request, response);
            return;
        }

        if (twoFactorPending) {
            if (isTwoFactorVerifyRequest(request)) {
                // Сам токен проверяет TwoFactorService из тела запроса; здесь он никого не аутентифицирует
                filterChain.doFilter(request, response);
                return;
            }
            rejectPendingToken(response);
            return;
        }

        if (userPhone != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            final UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userPhone);
            } catch (UsernameNotFoundException e) {
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isTwoFactorVerifyRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri.equals(TWO_FACTOR_VERIFY_PATH) || uri.startsWith(TWO_FACTOR_VERIFY_PATH + "/");
    }

    private static void rejectPendingToken(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(String.format(
                "{\"status\":401,\"message\":\"Вход не завершён: подтвердите второй фактор\",\"timestamp\":%d}",
                System.currentTimeMillis()));
    }
}
