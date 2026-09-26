package com.pistonpunk.gigahack26.policy.infrastructure;

import com.pistonpunk.gigahack26.policy.domain.PolicyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyQuestionRepository extends JpaRepository<PolicyQuestion, UUID> {

    Optional<PolicyQuestion> findByKey(String key);

    List<PolicyQuestion> findByServiceLineOrderByWeightTierDesc(String serviceLine);

    List<PolicyQuestion> findByActiveTrue();

    long countByActiveTrue();

    List<PolicyQuestion> findByActiveTrueAndServiceLine(String serviceLine);
}
