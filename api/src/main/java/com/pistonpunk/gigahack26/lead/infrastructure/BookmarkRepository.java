package com.pistonpunk.gigahack26.lead.infrastructure;

import com.pistonpunk.gigahack26.lead.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookmarkRepository extends JpaRepository<Bookmark, UUID> {

    Optional<Bookmark> findByCompanyIdAndServiceLine(UUID companyId, String serviceLine);

    List<Bookmark> findAllByOrderByCreatedAtDesc();
}
