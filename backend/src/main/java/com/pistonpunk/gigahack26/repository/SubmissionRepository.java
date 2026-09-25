package com.pistonpunk.gigahack26.repository;

import com.pistonpunk.gigahack26.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
}
