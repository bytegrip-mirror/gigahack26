package com.pistonpunk.gigahack26.repository;

import com.pistonpunk.gigahack26.domain.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByCompanyIdIn(Collection<UUID> companyIds);

    List<Evidence> findByCompanyId(UUID companyId);

    long countByCompanyId(UUID companyId);
}
