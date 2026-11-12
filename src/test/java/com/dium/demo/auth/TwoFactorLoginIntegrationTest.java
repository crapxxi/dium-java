package com.dium.demo.auth;

import com.dium.demo.enums.TwoFactorMethod;
import com.dium.demo.enums.UserRole;
import com.dium.demo.exceptions.MessageDeliveryException;
import com.dium.demo.models.User;
import com.dium.demo.services.auth.JwtService;
import com.dium.demo.services.auth.TotpService;
import com.dium.demo.support.AbstractIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Главное свойство: при включённой 2FA настоящий JWT нельзя получить без действующего кода.
 */
class TwoFactorLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TotpService totpService;
    @Autowired
    private JwtService jwtService;
    @Value("${jwt.secret}")
    private String jwtSecret;

    // ------------------------------------------------------------------ helpers

    private String loginWithoutTwoFactor(String phone) throws Exception {
        MvcResult result = login(phone, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return json(result, "$.token");
    }

    /** Вход при включённой 2FA: проверяет, что JWT не выдан, и возвращает временный токен. */
    private String loginExpectingChallenge(String phone, String expectedMethod) throws Exception {
        MvcResult result = login(phone, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tempToken").isNotEmpty())
                .andExpect(jsonPath("$.twoFactorMethod").value(expectedMethod))
                .andReturn();
        return json(result, "$.tempToken");
    }

    /** Включает WhatsApp-2FA через API и возвращает выданные резервные коды. */
    private List<String> enableWhatsAppTwoFactor(String phone, String jwt) throws Exception {
        postJson("/api/v1/auth/2fa/setup", "{\"method\":\"WHATSAPP\"}", jwt).andExpect(status().isOk());
        String code = lastCodeSentTo(phone);
        MvcResult result = postJson("/api/v1/auth/2fa/enable",
                "{\"method\":\"WHATSAPP\",\"code\":\"" + code + "\"}", jwt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes", hasSize(10)))
                .andReturn();
        cacheManager.getCache("otpCodes").clear(); // чтобы cooldown не мешал следующему входу
        return json(result, "$.backupCodes");
    }

    /** Включает TOTP через API. Возвращает [секрет, резервные коды...]. */
    private TotpSetup enableTotp(String jwt) throws Exception {
        MvcResult setup = postJson("/api/v1/auth/2fa/setup", "{\"method\":\"TOTP\"}", jwt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri", startsWith("otpauth://totp/")))
                .andExpect(jsonPath("$.qrCodeDataUri", startsWith("data:image/png;base64,")))
                .andReturn();
        String secret = json(setup, "$.secret");

        MvcResult enabled = postJson("/api/v1/auth/2fa/enable",
                "{\"method\":\"TOTP\",\"code\":\"" + totpService.currentCode(secret) + "\"}", jwt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes", hasSize(10)))
                .andReturn();
        return new TotpSetup(secret, json(enabled, "$.backupCodes"));
    }

    private record TotpSetup(String secret, List<String> backupCodes) { }

    // ------------------------------------------------------------------ tests

    @Test
    void loginWithoutTwoFactorReturnsJwtImmediately() throws Exception {
        createUser("77010000001", UserRole.CLIENT);

        String jwt = loginWithoutTwoFactor("77010000001");

        getWithToken("/api/v1/auth/me", jwt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("77010000001"));
    }

    @Test
    void whatsAppTwoFactor_noJwtWithoutValidCode() throws Exception {
        String phone = "77010000002";
        createUser(phone, UserRole.CLIENT);
        enableWhatsAppTwoFactor(phone, loginWithoutTwoFactor(phone));

        // 1. Пароль верный, но JWT не выдан — только временный токен
        String tempToken = loginExpectingChallenge(phone, "WHATSAPP");
        String code = lastCodeSentTo(phone);

        // 2. Временный токен не даёт доступа ни к одному эндпоинту
        getWithToken("/api/v1/auth/me", tempToken).andExpect(status().isUnauthorized());
        getWithToken("/api/v1/auth/2fa/status", tempToken).andExpect(status().isUnauthorized());
        getWithToken("/api/v1/orders/user", tempToken).andExpect(status().isUnauthorized());
        getWithToken("/api/v1/venues", tempToken).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/2fa/disable", "{\"password\":\"" + PASSWORD + "\",\"code\":\"" + code + "\"}", tempToken)
                .andExpect(status().isUnauthorized());

        // 3. Неверный код — JWT по-прежнему нет
        verifyTwoFactor(tempToken, wrongCodeFor(code), "WHATSAPP")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.token").doesNotExist());

        // 4. Верный код — выдан настоящий JWT, и он работает
        MvcResult verified = verifyTwoFactor(tempToken, code, "WHATSAPP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String jwt = json(verified, "$.token");
        assertThat(jwtService.isTwoFactorPending(jwt)).isFalse();

        getWithToken("/api/v1/auth/me", jwt).andExpect(status().isOk());

        // 5. Код одноразовый: повторно тем же кодом войти нельзя
        verifyTwoFactor(tempToken, code, "WHATSAPP").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void totpTwoFactor_fullFlowWithBackupCodes() throws Exception {
        String phone = "77010000003";
        createUser(phone, UserRole.CLIENT);
        TotpSetup totp = enableTotp(loginWithoutTwoFactor(phone));

        // секрет хранится зашифрованным, резервные коды — только хэшами
        User stored = userRepository.findByPhone(phone).orElseThrow();
        assertThat(stored.getTwoFactorMethod()).isEqualTo(TwoFactorMethod.TOTP);
        assertThat(stored.getTotpSecret()).startsWith("v1:").doesNotContain(totp.secret());
        for (String backupCode : totp.backupCodes()) {
            assertThat(stored.getBackupCodeHashes())
                    .doesNotContain(backupCode)
                    .doesNotContain(backupCode.replace("-", ""));
        }

        // вход: JWT нет, WhatsApp-код для TOTP-пользователя не отправляется
        String tempToken = loginExpectingChallenge(phone, "TOTP");
        verify(senderService, never()).sendMessage(anyString(), anyString());

        verifyTwoFactor(tempToken, "000000".equals(totpService.currentCode(totp.secret())) ? "111111" : "000000", "TOTP")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.token").doesNotExist());

        verifyTwoFactor(tempToken, totpService.currentCode(totp.secret()), "TOTP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // резервный код работает ровно один раз
        String backupCode = totp.backupCodes().get(0);
        verifyTwoFactor(loginExpectingChallenge(phone, "TOTP"), backupCode, "BACKUP_CODE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        verifyTwoFactor(loginExpectingChallenge(phone, "TOTP"), backupCode, "BACKUP_CODE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void totpUserCanFallBackToWhatsAppCode() throws Exception {
        String phone = "77010000004";
        createUser(phone, UserRole.CLIENT);
        enableTotp(loginWithoutTwoFactor(phone));

        String tempToken = loginExpectingChallenge(phone, "TOTP");

        postJson("/api/v1/auth/2fa/verify/send-code", "{\"tempToken\":\"" + tempToken + "\"}")
                .andExpect(status().isOk());

        verifyTwoFactor(tempToken, lastCodeSentTo(phone), "WHATSAPP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void verifyRejectsAnythingThatIsNotAValidPendingToken() throws Exception {
        String phone = "77010000005";
        createUser(phone, UserRole.CLIENT);
        String realJwt = loginWithoutTwoFactor(phone);
        enableWhatsAppTwoFactor(phone, realJwt);

        loginExpectingChallenge(phone, "WHATSAPP");
        String code = lastCodeSentTo(phone);

        // настоящий JWT нельзя подставить вместо временного токена
        verifyTwoFactor(realJwt, code, "WHATSAPP").andExpect(status().isUnauthorized());
        // мусор
        verifyTwoFactor("not-a-token", code, "WHATSAPP").andExpect(status().isUnauthorized());
        // просроченный временный токен
        verifyTwoFactor(expiredPendingToken(phone), code, "WHATSAPP").andExpect(status().isUnauthorized());
        // токен, подписанный чужим ключом
        verifyTwoFactor(pendingTokenSignedWithOtherKey(phone), code, "WHATSAPP").andExpect(status().isUnauthorized());
    }

    @Test
    void pendingTokenExpiresInFiveMinutes() throws Exception {
        String phone = "77010000006";
        createUser(phone, UserRole.CLIENT);
        enableWhatsAppTwoFactor(phone, loginWithoutTwoFactor(phone));

        String tempToken = loginExpectingChallenge(phone, "WHATSAPP");

        Date expiration = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))).build()
                .parseClaimsJws(tempToken).getBody().getExpiration();
        long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        assertThat(jwtService.isTwoFactorPending(tempToken)).isTrue();
        assertThat(ttlSeconds).isBetween(290L, 300L);
    }

    @Test
    void twoFactorIsMandatoryForVenueOwnerAndAdmin() throws Exception {
        for (UserRole role : List.of(UserRole.VENUE_OWNER, UserRole.ADMIN)) {
            String phone = role == UserRole.ADMIN ? "77010000007" : "77010000008";
            createUser(phone, role); // сам пользователь 2FA не включал

            String tempToken = loginExpectingChallenge(phone, "WHATSAPP");
            String code = lastCodeSentTo(phone);

            getWithToken("/api/v1/auth/me", tempToken).andExpect(status().isUnauthorized());

            verifyTwoFactor(tempToken, wrongCodeFor(code), null)
                    .andExpect(status().isUnprocessableEntity());

            MvcResult verified = verifyTwoFactor(tempToken, code, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn();

            getWithToken("/api/v1/auth/2fa/status", json(verified, "$.token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.required").value(true))
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.loginMethod").value("WHATSAPP"));
        }
    }

    @Test
    void loginStillRequiresSecondFactorWhenWhatsAppIsDown() throws Exception {
        String phone = "77010000009";
        createUser(phone, UserRole.CLIENT);
        List<String> backupCodes = enableWhatsAppTwoFactor(phone, loginWithoutTwoFactor(phone));

        doThrow(new MessageDeliveryException("WhatsApp недоступен"))
                .when(senderService).sendMessage(anyString(), anyString());

        // сбой доставки не превращается во «вход без кода»
        MvcResult result = login(phone, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.codeSent").value(false))
                .andReturn();
        String tempToken = json(result, "$.tempToken");

        // повторная отправка честно сообщает об ошибке
        postJson("/api/v1/auth/2fa/verify/send-code", "{\"tempToken\":\"" + tempToken + "\"}")
                .andExpect(status().isBadGateway());

        // но резервным кодом войти можно
        verifyTwoFactor(tempToken, backupCodes.get(0), "BACKUP_CODE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void repeatedWrongCodesLockVerification() throws Exception {
        String phone = "77010000010";
        createUser(phone, UserRole.CLIENT);
        TotpSetup totp = enableTotp(loginWithoutTwoFactor(phone));

        String tempToken = loginExpectingChallenge(phone, "TOTP");
        String wrong = "000000".equals(totpService.currentCode(totp.secret())) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verifyTwoFactor(tempToken, wrong, "TOTP").andExpect(status().isUnprocessableEntity());
        }

        // после 5 ошибок даже верный код временно не принимается
        verifyTwoFactor(tempToken, totpService.currentCode(totp.secret()), "TOTP")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void disableRequiresPasswordAndCode() throws Exception {
        String phone = "77010000011";
        createUser(phone, UserRole.CLIENT);
        String firstJwt = loginWithoutTwoFactor(phone);
        TotpSetup totp = enableTotp(firstJwt);

        // неверный пароль
        postJson("/api/v1/auth/2fa/disable",
                "{\"password\":\"wrong-password\",\"code\":\"" + totpService.currentCode(totp.secret()) + "\"}", firstJwt)
                .andExpect(status().isUnprocessableEntity());
        // верный пароль, неверный код
        postJson("/api/v1/auth/2fa/disable",
                "{\"password\":\"" + PASSWORD + "\",\"code\":\"abc\"}", firstJwt)
                .andExpect(status().isUnprocessableEntity());
        assertThat(userRepository.findByPhone(phone).orElseThrow().hasTwoFactor()).isTrue();

        // пароль + код
        postJson("/api/v1/auth/2fa/disable",
                "{\"password\":\"" + PASSWORD + "\",\"code\":\"" + totpService.currentCode(totp.secret()) + "\"}", firstJwt)
                .andExpect(status().isOk());

        User stored = userRepository.findByPhone(phone).orElseThrow();
        assertThat(stored.hasTwoFactor()).isFalse();
        assertThat(stored.getTotpSecret()).isNull();
        assertThat(stored.getBackupCodeHashes()).isNull();

        loginWithoutTwoFactor(phone);
    }

    @Test
    void backupCodesCanBeRegeneratedOnlyWithPassword() throws Exception {
        String phone = "77010000012";
        createUser(phone, UserRole.CLIENT);
        String jwt = loginWithoutTwoFactor(phone);
        TotpSetup totp = enableTotp(jwt);

        postJson("/api/v1/auth/2fa/backup-codes", "{\"password\":\"wrong-password\"}", jwt)
                .andExpect(status().isUnprocessableEntity());

        MvcResult regenerated = postJson("/api/v1/auth/2fa/backup-codes", "{\"password\":\"" + PASSWORD + "\"}", jwt)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes", hasSize(10)))
                .andReturn();
        List<String> newCodes = json(regenerated, "$.backupCodes");

        // старые коды больше не действуют, новые — действуют
        verifyTwoFactor(loginExpectingChallenge(phone, "TOTP"), totp.backupCodes().get(0), "BACKUP_CODE")
                .andExpect(status().isUnprocessableEntity());
        verifyTwoFactor(loginExpectingChallenge(phone, "TOTP"), newCodes.get(0), "BACKUP_CODE")
                .andExpect(status().isOk());
    }

    @Test
    void twoFactorManagementRequiresAuthentication() throws Exception {
        postJson("/api/v1/auth/2fa/setup", "{\"method\":\"TOTP\"}").andExpect(status().isForbidden());
        postJson("/api/v1/auth/2fa/enable", "{\"method\":\"TOTP\",\"code\":\"123456\"}").andExpect(status().isForbidden());
        postJson("/api/v1/auth/2fa/disable", "{\"password\":\"x\",\"code\":\"123456\"}").andExpect(status().isForbidden());
        postJson("/api/v1/auth/2fa/backup-codes", "{\"password\":\"x\"}").andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordNeverStartsTwoFactorChallenge() throws Exception {
        String phone = "77010000013";
        createUser(phone, UserRole.ADMIN);

        login(phone, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.tempToken").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(senderService, never()).sendMessage(anyString(), anyString());
    }

    // ------------------------------------------------------------------ token forgery helpers

    private String expiredPendingToken(String phone) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim(JwtService.CLAIM_TWO_FACTOR_PENDING, true)
                .setSubject(phone)
                .setIssuedAt(new Date(now - 600_000))
                .setExpiration(new Date(now - 300_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)), SignatureAlgorithm.HS256)
                .compact();
    }

    private String pendingTokenSignedWithOtherKey(String phone) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim(JwtService.CLAIM_TWO_FACTOR_PENDING, true)
                .setSubject(phone)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 300_000))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256), SignatureAlgorithm.HS256)
                .compact();
    }
}
