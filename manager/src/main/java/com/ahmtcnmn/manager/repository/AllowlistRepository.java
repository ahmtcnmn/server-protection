package com.ahmtcnmn.manager.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ahmtcnmn.manager.model.AllowlistEntry;

public interface AllowlistRepository extends JpaRepository<AllowlistEntry, Long> {

    List<AllowlistEntry> findAllByOrderByCreatedAtDesc();

    boolean existsByValue(String value);

    List<AllowlistEntry> findByExpiresAtBefore(Instant now);
}
