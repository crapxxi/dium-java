package com.dium.demo.repositories;

import com.dium.demo.models.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findAllByIsWorkingTrue();
    Optional<Venue> findByOwnerId(Long ownerId);
}
