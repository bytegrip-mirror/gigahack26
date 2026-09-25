package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.PolicyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyScoreRepository extends JpaRepository<PolicyScore, UUID> {

    List<PolicyScore> findByServiceLine(String serviceLine);

    Optional<PolicyScore> findByCompanyIdAndServiceLine(UUID companyId, String serviceLine);

    @Modifying
    @Query("delete from PolicyScore s where s.serviceLine = :serviceLine")
    void deleteByServiceLine(String serviceLine);

    @Query("select distinct s.serviceLine from PolicyScore s order by s.serviceLine")
    List<String> findServiceLines();
}
