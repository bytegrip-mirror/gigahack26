package com.pistonpunk.gigahack26.repository;

import com.pistonpunk.gigahack26.domain.Signal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SignalRepository extends JpaRepository<Signal, UUID> {

    List<Signal> findByCompanyId(UUID companyId);

    List<Signal> findByCompanyIdIn(Collection<UUID> companyIds);

    List<Signal> findByQuestionId(UUID questionId);

    long countByQuestionId(UUID questionId);

    @Modifying
    @Query("delete from Signal s where s.questionId = :questionId")
    void deleteByQuestionId(UUID questionId);

    @Modifying
    @Query("delete from Signal s where s.companyId = :companyId and s.questionId = :questionId")
    void deleteByCompanyIdAndQuestionId(UUID companyId, UUID questionId);
}
