package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.IcpProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IcpProfileRepository extends JpaRepository<IcpProfile, UUID> {

    List<IcpProfile> findByOrderBySortOrderAscNameAsc();
}
