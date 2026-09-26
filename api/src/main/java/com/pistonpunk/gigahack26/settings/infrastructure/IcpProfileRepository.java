package com.pistonpunk.gigahack26.settings.infrastructure;

import com.pistonpunk.gigahack26.settings.domain.IcpProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IcpProfileRepository extends JpaRepository<IcpProfile, UUID> {

    List<IcpProfile> findByOrderBySortOrderAscNameAsc();
}
