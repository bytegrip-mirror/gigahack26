package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.CompanyAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, UUID> {

    List<CompanyAlias> findByCompanyIdIn(Collection<UUID> companyIds);
}
