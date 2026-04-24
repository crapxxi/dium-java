package com.dium.demo.controllers;

import com.dium.demo.dto.requests.OrderItemRequest;
import com.dium.demo.dto.requests.OrderRequest;
import com.dium.demo.dto.responses.OrderResponse;
import com.dium.demo.enums.OrderStatus;
import com.dium.demo.services.CustomUserDetailsService;
import com.dium.demo.services.JwtService;
import com.dium.demo.services.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean(name = "userDetailsService")
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void create_Success() throws Exception {
        OrderItemRequest itemRequest = new OrderItemRequest(1L, 2, List.of(10L));
        OrderRequest request = new OrderRequest(1L, List.of(itemRequest), "Address", "Comment", BigDecimal.TEN, "Card");
        OrderResponse response = new OrderResponse(100L, "Venue Name", BigDecimal.valueOf(2500), OrderStatus.PENDING, 1234, LocalDateTime.now(), List.of(), "Address", "Comment", BigDecimal.TEN, "Card", "87777777777");

        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.totalSum").value(2500));
    }

    @Test
    void create_ValidationFailed_Returns400() throws Exception {
        OrderRequest invalidRequest = new OrderRequest(null, List.of(), "", null, null, "");

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserOrders_Success() throws Exception {
        OrderResponse response = new OrderResponse(100L, "V", BigDecimal.ONE, OrderStatus.PENDING, 1, LocalDateTime.now(), List.of(), "A", "C", BigDecimal.ZERO, "P", "87777777777");
        when(orderService.getOrdersByUserId()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/orders/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100L));
    }

    @Test
    void updateStatus_Success() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/1/status"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_Success() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/1/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void getKitchenOrders_Success() throws Exception {
        when(orderService.getKitchenOrders()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/kitchen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getOrderHistory_Success() throws Exception {
        when(orderService.getOrderHistory()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/order-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getKaspiUrl_Success() throws Exception {
        String expectedUrl = "https://kaspi.kz/pay/test";
        when(orderService.getKaspiUrl(eq(1L))).thenReturn(expectedUrl);

        mockMvc.perform(get("/api/v1/orders/1/kaspi-url"))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedUrl));
    }
}