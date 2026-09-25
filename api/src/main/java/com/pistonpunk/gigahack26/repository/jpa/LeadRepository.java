package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.Lead;
import com.pistonpunk.gigahack26.domain.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByCompanyIdAndServiceLine(UUID companyId, String serviceLine);

    List<Lead> findByCompanyIdIn(Collection<UUID> companyIds);

    List<Lead> findByServiceLine(String serviceLine);

    long countByStatus(LeadStatus status);
}
