package com.infra.monitor.repo;

import com.infra.monitor.model.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    @Query("select r from CheckResult r where r.checkedAt = "
            + "(select max(r2.checkedAt) from CheckResult r2 where r2.name = r.name) order by r.name")
    List<CheckResult> findLatestPerCheck();

    @Modifying
    @Transactional
    @Query("delete from CheckResult r where r.checkedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
