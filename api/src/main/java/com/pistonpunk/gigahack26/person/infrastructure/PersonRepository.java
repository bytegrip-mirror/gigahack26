package com.pistonpunk.gigahack26.person.infrastructure;

import com.pistonpunk.gigahack26.person.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    List<Person> findByCompanyIdIn(List<UUID> companyIds);

    List<Person> findByEnrichedAtIsNull();

    long countByCompanyId(UUID companyId);

    void deleteByCompanyId(UUID companyId);
}
