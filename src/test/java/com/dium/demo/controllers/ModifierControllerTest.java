package com.dium.demo.controllers;

import com.dium.demo.dto.requests.ModifierGroupRequest;
import com.dium.demo.dto.requests.ModifierRequest;
import com.dium.demo.dto.responses.ModifierGroupResponse;
import com.dium.demo.dto.responses.ModifierResponse;
import com.dium.demo.services.CustomUserDetailsService;
import com.dium.demo.services.JwtService;
import com.dium.demo.services.ModifierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ModifierController.class)
@AutoConfigureMockMvc(addFilters = false)
class ModifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ModifierService modifierService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean(name = "userDetailsService")
    private CustomUserDetailsService userDetailsService;

    @Test
    void updateGroup_Success() throws Exception {
        ModifierRequest modRequest = new ModifierRequest( "Test Modifier", BigDecimal.ZERO, true);

        ModifierResponse modResponse = new ModifierResponse(1L, "Test Modifier", BigDecimal.ZERO, true);

        ModifierGroupRequest request = new ModifierGroupRequest(
                "Group Name",
                true,
                1,
                3,
                List.of(modRequest)
        );

        ModifierGroupResponse response = new ModifierGroupResponse(
                1L,
                "Group Name",
                true,
                1,
                3,
                List.of(modResponse)
        );

        when(modifierService.editModifierGroup(eq(1L), any(ModifierGroupRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/modifiers/groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Group Name"))
                .andExpect(jsonPath("$.modifiers").isArray())
                .andExpect(jsonPath("$.modifiers[0].name").value("Test Modifier"));
    }

    @Test
    void updateModifier_Success() throws Exception {
        ModifierRequest request = new ModifierRequest("Extra Cheese", BigDecimal.valueOf(200), true);
        ModifierResponse response = new ModifierResponse(10L, "Extra Cheese", BigDecimal.valueOf(200), true);

        when(modifierService.editModifier(eq(10L), any(ModifierRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/modifiers/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.name").value("Extra Cheese"));
    }

    @Test
    void deleteModifierGroup_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/modifiers/groups/1"))
                .andExpect(status().isOk());
    }

    @Test
    void toggle_Success() throws Exception {
        mockMvc.perform(patch("/api/v1/modifiers/groups/1/toggleRequired"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteModifier_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/modifiers/1"))
                .andExpect(status().isOk());
    }

    @Test
    void toggleStock_Success() throws Exception {
        mockMvc.perform(patch("/api/v1/modifiers/1"))
                .andExpect(status().isOk());
    }

    @Test
    void updateModifier_ValidationFailed_Returns400() throws Exception {
        ModifierRequest invalidRequest = new ModifierRequest("", null, true);

        mockMvc.perform(put("/api/v1/modifiers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}