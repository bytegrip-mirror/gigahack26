package com.pistonpunk.gigahack26.region.infrastructure;

import com.pistonpunk.gigahack26.region.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegionRepository extends JpaRepository<Region, UUID> {

    Optional<Region> findByKey(String key);

    List<Region> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<Region> findAllByOrderBySortOrderAscNameAsc();

    List<Region> findByParentKeyOrderBySortOrderAscNameAsc(String parentKey);
}
