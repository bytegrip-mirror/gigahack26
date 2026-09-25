package com.pistonpunk.gigahack26.repository;

import com.pistonpunk.gigahack26.domain.SubmissionDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SubmissionDetailRepository extends MongoRepository<SubmissionDetail, String> {

    List<SubmissionDetail> findBySubmissionIdIn(Collection<UUID> submissionIds);
}
