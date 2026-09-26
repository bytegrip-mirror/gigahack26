package com.pistonpunk.gigahack26.evidence.infrastructure;

import com.pistonpunk.gigahack26.evidence.domain.RawCapture;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RawCaptureRepository extends MongoRepository<RawCapture, String> {

    Optional<RawCapture> findByEvidenceKey(String evidenceKey);

    void deleteByEvidenceKey(String evidenceKey);
}
