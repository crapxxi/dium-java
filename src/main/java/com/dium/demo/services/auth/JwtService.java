package com.dium.demo.services.auth;

import com.dium.demo.config.TwoFactorProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {
    /** Пометка временного токена, который выдаётся после пароля, но до проверки второго фактора. */
    public static final String CLAIM_TWO_FACTOR_PENDING = "2fa_pending";

    @Value("${jwt.secret}")
    private String key;

    private final TwoFactorProperties twoFactorProperties;

    /** Полноценный JWT (24 часа). Выдаётся только после всех проверок, включая второй фактор. */
    public String generateToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), 1000L * 60 * 60 * 24);
    }

    /**
     * Короткоживущий токен «пароль принят, ждём второй фактор».
     * Не даёт доступа к API: {@code JwtAuthenticationFilter} отклоняет его на всех эндпоинтах,
     * кроме /api/v1/auth/2fa/verify.
     */
    public String generateTwoFactorPendingToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TWO_FACTOR_PENDING, true);
        return buildToken(claims, userDetails.getUsername(), twoFactorProperties.pendingTokenTtl().toMillis());
    }

    private String buildToken(Map<String, Object> claims, String subject, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** @throws JwtException если токен повреждён, подписан другим ключом или истёк */
    public boolean isTwoFactorPending(String token) {
        return Boolean.TRUE.equals(extractClaim(token, claims -> claims.get(CLAIM_TWO_FACTOR_PENDING, Boolean.class)));
    }

    /**
     * Проверяет временный токен и возвращает телефон пользователя.
     *
     * @throws BadCredentialsException если токен недействителен, истёк или это не временный 2FA-токен
     */
    public String extractTwoFactorPendingSubject(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!Boolean.TRUE.equals(claims.get(CLAIM_TWO_FACTOR_PENDING, Boolean.class))
                    || claims.getSubject() == null) {
                throw new BadCredentialsException("Сессия входа недействительна. Войдите заново.");
            }
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Сессия входа истекла. Войдите заново.");
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(key);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
