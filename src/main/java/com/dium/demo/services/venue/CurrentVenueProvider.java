package com.dium.demo.services.venue;

import com.dium.demo.models.User;
import com.dium.demo.models.Venue;
import com.dium.demo.repositories.VenueRepository;
import com.dium.demo.services.auth.CustomUserDetailsService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Заведение текущего пользователя — единая точка вместо повторяющегося поиска в сервисах. */
@Component
@RequiredArgsConstructor
public class CurrentVenueProvider {
    private final CustomUserDetailsService userDetailsService;
    private final VenueRepository venueRepository;

    /** @throws EntityNotFoundException если у текущего пользователя ещё нет заведения */
    public Venue getCurrentVenue() {
        User user = userDetailsService.getCurrentUser();
        return venueRepository.findByOwner_Id(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("User doesn't have a venue"));
    }
}
