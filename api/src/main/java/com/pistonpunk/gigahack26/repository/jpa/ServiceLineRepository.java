package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.ServiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceLineRepository extends JpaRepository<ServiceLine, UUID> {

    Optional<ServiceLine> findByKey(String key);

    List<ServiceLine> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<ServiceLine> findAllByOrderBySortOrderAscNameAsc();
}
