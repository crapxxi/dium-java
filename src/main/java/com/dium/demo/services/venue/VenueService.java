package com.dium.demo.services.venue;

import com.dium.demo.dto.requests.VenueRequest;
import com.dium.demo.dto.responses.VenueResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface VenueService {
    VenueResponse createVenue(VenueRequest request, MultipartFile image) throws IOException;
    List<VenueResponse> getAllVenues();
    VenueResponse getVenueById(Long venueId);
    VenueResponse getOwnerVenue();
    VenueResponse updateVenue(VenueRequest request, MultipartFile image) throws IOException;
    void toggleWork();
}

