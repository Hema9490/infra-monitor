package com.infra.monitor.repo;

import com.infra.monitor.model.Metric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface MetricRepository extends JpaRepository<Metric, Long> {

    List<Metric> findByHostOrderByCollectedAtDesc(String host, Pageable pageable);

    List<Metric> findAllByOrderByCollectedAtDesc(Pageable pageable);

    @Query("select m from Metric m where m.collectedAt = "
            + "(select max(m2.collectedAt) from Metric m2 where m2.host = m.host) order by m.host")
    List<Metric> findLatestPerHost();

    @Modifying
    @Transactional
    @Query("delete from Metric m where m.collectedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
