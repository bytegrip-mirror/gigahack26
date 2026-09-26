package com.pistonpunk.gigahack26.person.domain;

import com.pistonpunk.gigahack26.lead.domain.LeadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "person")
public class Person {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String role;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Column(name = "span_start", nullable = false)
    private int spanStart;

    @Column(name = "span_end", nullable = false)
    private int spanEnd;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(length = 200)
    private String publisher;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "linkedin_url", length = 300)
    private String linkedinUrl;

    /** Work address only, and only from somewhere that gave it: a licensed
     * provider, or a rep who typed it. Never guessed from a name and a domain. */
    @Column(length = 200)
    private String email;

    @Column(length = 160)
    private String headline;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LeadStatus status;

    /**
     * Boxed, and read through a null-safe getter, because the column was added to
     * a table that already held people: Hibernate widens the table with the column
     * empty, and a primitive int cannot hold the null that leaves behind. It threw
     * on every read of every person until this was boxed.
     */
    @Column(name = "contact_count")
    private Integer contactCount;

    @Column(name = "last_contacted_at")
    private Instant lastContactedAt;

    @Column(length = 40)
    private String channel;

    @Column(name = "outreach_subject", length = 300)
    private String outreachSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Person() {
    }

    public Person(UUID id, UUID companyId, String name, String role, UUID evidenceId, int spanStart, int spanEnd,
                  String sourceUrl, String publisher, Instant publishedAt, Instant createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.role = role;
        this.evidenceId = evidenceId;
        this.spanStart = spanStart;
        this.spanEnd = spanEnd;
        this.sourceUrl = sourceUrl;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.status = LeadStatus.NEW;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPublisher() {
        return publisher;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Nothing but an address, and nothing but one line of it.
     *
     * The value reaches a `mailto:` and a mail client's header, so a newline in it
     * is a header injection rather than a typo. It is refused here rather than at
     * the call site so every path that sets one gets the same answer.
     */
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            this.email = null;
            return;
        }
        String cleaned = email.replaceAll("[\\r\\n\\t]", "").trim();
        this.email = cleaned.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}") ? cleaned : null;
    }

    public String getHeadline() {
        return headline;
    }

    public Instant getEnrichedAt() {
        return enrichedAt;
    }

    public LeadStatus getStatus() {
        return status == null ? LeadStatus.NEW : status;
    }

    public int getContactCount() {
        return contactCount == null ? 0 : contactCount;
    }

    public Instant getLastContactedAt() {
        return lastContactedAt;
    }

    public String getChannel() {
        return channel;
    }

    public String getOutreachSubject() {
        return outreachSubject;
    }

    /**
     * A person is worked, not a company: the same transition rules the company
     * lead follows, so the two boards read the same way — the count moves on a real
     * move and not on a re-save.
     */
    public void moveTo(LeadStatus next, String viaChannel, String subject, Instant now) {
        if (next != this.status) {
            this.contactCount = getContactCount() + 1;
            this.lastContactedAt = now;
        }
        this.status = next;
        if (viaChannel != null && !viaChannel.isBlank()) {
            this.channel = viaChannel;
        }
        if (subject != null && !subject.isBlank()) {
            this.outreachSubject = subject;
        }
    }

    public void enrich(String linkedinUrl, String headline, Instant now) {
        this.linkedinUrl = linkedinUrl;
        this.headline = headline;
        this.enrichedAt = now;
    }
}
