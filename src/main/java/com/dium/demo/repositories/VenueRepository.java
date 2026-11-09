package com.dium.demo.repositories;

import com.dium.demo.models.Venue;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends BaseRepository<Venue, Long> {
    List<Venue> findAll();
    Optional<Venue> findByOwner_Id(Long ownerId);

    Boolean existsByOwner_Id(Long ownerId);
}
