package com.pistonpunk.gigahack26.seeding.infrastructure;

import com.pistonpunk.gigahack26.seeding.domain.CorpusState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CorpusStateRepository extends JpaRepository<CorpusState, UUID> {
}
