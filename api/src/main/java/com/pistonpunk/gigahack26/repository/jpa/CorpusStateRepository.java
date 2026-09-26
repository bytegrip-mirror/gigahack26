package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.CorpusState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CorpusStateRepository extends JpaRepository<CorpusState, UUID> {
}
