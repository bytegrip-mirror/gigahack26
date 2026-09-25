package com.pistonpunk.gigahack26.repository;

import com.pistonpunk.gigahack26.domain.RawCapture;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RawCaptureRepository extends MongoRepository<RawCapture, String> {

    Optional<RawCapture> findByEvidenceKey(String evidenceKey);
}
