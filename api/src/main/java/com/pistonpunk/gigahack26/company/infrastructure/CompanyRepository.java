package com.pistonpunk.gigahack26.company.infrastructure;

import com.pistonpunk.gigahack26.company.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByKey(String key);

    List<Company> findByKeyIn(Collection<String> keys);
}
