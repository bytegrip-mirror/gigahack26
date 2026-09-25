package com.pistonpunk.gigahack26.repository.jpa;

import com.pistonpunk.gigahack26.domain.ClassificationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassificationCacheRepository extends JpaRepository<ClassificationCache, UUID> {

    Optional<ClassificationCache> findByKey(String key);

    List<ClassificationCache> findByKeyIn(Collection<String> keys);

    long countByEngine(String engine);

    @Query("select coalesce(sum(c.hitCount), 0) from ClassificationCache c")
    long totalHits();
}
