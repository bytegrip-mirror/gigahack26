package com.pistonpunk.gigahack26.lead.application;

import com.pistonpunk.gigahack26.company.domain.Company;
import com.pistonpunk.gigahack26.company.infrastructure.CompanyRepository;
import com.pistonpunk.gigahack26.lead.domain.Bookmark;
import com.pistonpunk.gigahack26.lead.infrastructure.BookmarkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookmarkService {

    private final BookmarkRepository bookmarks;
    private final CompanyRepository companies;
    private final Clock clock;

    public BookmarkService(BookmarkRepository bookmarks, CompanyRepository companies, Clock clock) {
        this.bookmarks = bookmarks;
        this.companies = companies;
        this.clock = clock;
    }

    public record Saved(UUID companyId, String company, String domain, String country, String serviceLine,
                        String note, java.time.Instant createdAt) {
    }

    public List<Saved> list() {
        return bookmarks.findAllByOrderByCreatedAtDesc().stream()
                .map(this::view)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<Saved> find(UUID companyId, String serviceLine) {
        return bookmarks.findByCompanyIdAndServiceLine(companyId, serviceLine).flatMap(this::view);
    }

    private Optional<Saved> view(Bookmark bookmark) {
        return companies.findById(bookmark.getCompanyId())
                .map(company -> new Saved(company.getId(), company.getName(), company.getDomain(),
                        company.getCountry(), bookmark.getServiceLine(), bookmark.getNote(),
                        bookmark.getCreatedAt()));
    }

    @Transactional
    public Saved add(UUID companyId, String serviceLine, String note) {
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("No such company"));
        Bookmark existing = bookmarks.findByCompanyIdAndServiceLine(companyId, serviceLine).orElse(null);
        if (existing != null) {
            existing.note(note);
            return view(bookmarks.save(existing)).orElseThrow();
        }
        Bookmark created = bookmarks.save(new Bookmark(UUID.randomUUID(), companyId, serviceLine, note,
                clock.instant()));
        return new Saved(company.getId(), company.getName(), company.getDomain(), company.getCountry(),
                serviceLine, created.getNote(), created.getCreatedAt());
    }

    @Transactional
    public boolean remove(UUID companyId, String serviceLine) {
        Bookmark existing = bookmarks.findByCompanyIdAndServiceLine(companyId, serviceLine).orElse(null);
        if (existing == null) {
            return false;
        }
        bookmarks.delete(existing);
        return true;
    }
}
