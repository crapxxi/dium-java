package com.dium.demo.controllers;

import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.VenueResponse;
import com.dium.demo.services.CustomUserDetailsService;
import com.dium.demo.services.JwtService;
import com.dium.demo.services.VenueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VenueController.class)
@AutoConfigureMockMvc(addFilters = false)
class VenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean(name = "userDetailsService")
    private CustomUserDetailsService userDetailsService;

    @Test
    void getAll_Success() throws Exception {
        VenueResponse response = new VenueResponse(1L, "Venue", "Desc", "Addr", "url", true, "testUrl" , BigDecimal.TEN, true);
        when(venueService.getAllVenues()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Venue"));
    }

    @Test
    void getById_Success() throws Exception {
        VenueResponse response = new VenueResponse(1L, "Venue", "Desc", "Addr", "url", true, "testUrl" , BigDecimal.TEN, true);
        when(venueService.getVenueById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/venues/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void createVenue_Success() throws Exception {
        VenueRequest request = new VenueRequest("New Venue", "Desc", "Addr", null, true,BigDecimal.TEN);
        VenueResponse response = new VenueResponse(1L, "New Venue", "Desc", "Addr", "url", true, "testUrl" ,BigDecimal.TEN, true);

        MockMultipartFile venuePart = new MockMultipartFile("venueRequest", "", "application/json", objectMapper.writeValueAsBytes(request));

        when(venueService.createVenue(any(VenueRequest.class), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/venues/manage")
                        .file(venuePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Venue"));
    }

    @Test
    void updateVenue_Success() throws Exception {
        VenueRequest request = new VenueRequest("Updated Venue", "Desc", "Addr", null, true, BigDecimal.TEN);
        VenueResponse response = new VenueResponse(1L, "Updated Venue", "Desc", "Addr", "url", true, "testUrl" ,BigDecimal.TEN, true);

        MockMultipartFile venuePart = new MockMultipartFile("venueRequest", "", "application/json", objectMapper.writeValueAsBytes(request));

        when(venueService.updateVenue(any(VenueRequest.class), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/venues/manage")
                        .file(venuePart)
                        .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Venue"));
    }

    @Test
    void getUserVenue_Success() throws Exception {
        VenueResponse response = new VenueResponse(1L, "My Venue", "Desc", "Addr", "url", true, "testUrl" ,BigDecimal.TEN, true);
        when(venueService.getOwnerVenue()).thenReturn(response);

        mockMvc.perform(get("/api/v1/venues/manage/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Venue"));
    }

    @Test
    void toggleWork_Success() throws Exception {
        doNothing().when(venueService).toggleWork();

        mockMvc.perform(patch("/api/v1/venues/manage/toggleWork"))
                .andExpect(status().isOk());
    }

    @Test
    void createVenue_ValidationFailed_Returns400() throws Exception {
        VenueRequest invalidRequest = new VenueRequest("", "", "", null, null, BigDecimal.valueOf(-1));
        MockMultipartFile venuePart = new MockMultipartFile("venueRequest", "", "application/json", objectMapper.writeValueAsBytes(invalidRequest));

        mockMvc.perform(multipart("/api/v1/venues/manage")
                        .file(venuePart))
                .andExpect(status().isBadRequest());
    }
}