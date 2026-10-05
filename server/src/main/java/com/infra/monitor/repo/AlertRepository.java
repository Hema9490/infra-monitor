package com.infra.monitor.repo;

import com.infra.monitor.model.Alert;
import com.infra.monitor.model.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findFirstByAlertKeyAndStatus(String alertKey, AlertStatus status);

    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    List<Alert> findTop100ByOrderByCreatedAtDesc();
}
