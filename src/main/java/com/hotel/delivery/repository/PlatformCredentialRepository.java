package com.hotel.delivery.repository;

import com.hotel.delivery.entity.PlatformCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformCredentialRepository extends JpaRepository<PlatformCredential, Long> {
    Optional<PlatformCredential> findByPlatformId(Long platformId);
}
