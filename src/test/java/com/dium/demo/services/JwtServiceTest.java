package com.dium.demo.services;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    @Mock
    private UserDetails userDetails;

    private final String TEST_PHONE = "+79991234567";

    private final String TEST_SECRET_KEY = "VGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3Itand0LXRlc3RpbmctcHVycG9zZXM=";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "key", TEST_SECRET_KEY);
    }

    @Test
    void generateToken_ShouldReturnValidJwtToken() {
        when(userDetails.getUsername()).thenReturn(TEST_PHONE);

        String token = jwtService.generateToken(userDetails);

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        when(userDetails.getUsername()).thenReturn(TEST_PHONE);
        String token = jwtService.generateToken(userDetails);

        String extractedUsername = jwtService.extractUsername(token);

        assertThat(extractedUsername).isEqualTo(TEST_PHONE);
    }

    @Test
    void isTokenValid_WithCorrectUserAndNotExpired_ShouldReturnTrue() {
        when(userDetails.getUsername()).thenReturn(TEST_PHONE);
        String token = jwtService.generateToken(userDetails);

        boolean isValid = jwtService.isTokenValid(token, userDetails);

        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_WithDifferentUser_ShouldReturnFalse() {
        when(userDetails.getUsername()).thenReturn(TEST_PHONE);
        String token = jwtService.generateToken(userDetails);

        UserDetails anotherUser = org.mockito.Mockito.mock(UserDetails.class);
        when(anotherUser.getUsername()).thenReturn("+78880001122");

        boolean isValid = jwtService.isTokenValid(token, anotherUser);

        assertThat(isValid).isFalse();
    }

    @Test
    void methods_WithExpiredToken_ShouldThrowExpiredJwtException() {
        byte[] keyBytes = Decoders.BASE64.decode(TEST_SECRET_KEY);
        String expiredToken = Jwts.builder()
                .setSubject(TEST_PHONE)
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 10))
                .setExpiration(new Date(System.currentTimeMillis() - 1000 * 60 * 5))
                .signWith(Keys.hmacShaKeyFor(keyBytes), SignatureAlgorithm.HS256)
                .compact();


        assertThatThrownBy(() -> jwtService.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }
}