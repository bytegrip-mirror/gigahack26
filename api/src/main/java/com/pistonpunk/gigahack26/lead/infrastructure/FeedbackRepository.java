package com.pistonpunk.gigahack26.lead.infrastructure;

import com.pistonpunk.gigahack26.lead.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    List<Feedback> findByCompanyId(UUID companyId);
}
