package com.bengu.springblog.repositories;

import com.bengu.springblog.entities.LastFmProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LastFmProfileRepository extends JpaRepository<LastFmProfile, Long> {
    Optional<LastFmProfile> findByUser_GoogleSub(String googleSub);
}
