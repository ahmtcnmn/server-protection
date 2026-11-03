package com.ahmtcnmn.manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ahmtcnmn.manager.model.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
}
