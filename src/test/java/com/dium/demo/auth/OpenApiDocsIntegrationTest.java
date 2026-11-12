package com.dium.demo.auth;

import com.dium.demo.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void swaggerDescribesTwoFactorEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/2fa/verify'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/2fa/setup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/2fa/enable'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/2fa/disable'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/2fa/backup-codes'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }
}
