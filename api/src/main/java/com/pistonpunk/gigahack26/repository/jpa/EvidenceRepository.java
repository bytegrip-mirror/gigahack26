package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByCompanyIdIn(Collection<UUID> companyIds);

    List<Evidence> findByCompanyId(UUID companyId);

    long countByCompanyId(UUID companyId);

    @Query("select count(distinct e.companyId) from Evidence e")
    long countDistinctCompanies();

    @Query("select max(e.observedAt) from Evidence e")
    Instant newestObservedAt();
}
