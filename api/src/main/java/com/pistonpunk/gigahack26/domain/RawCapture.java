package com.pistonpunk.gigahack26.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "raw_capture")
public class RawCapture {

    @Id
    private String id;

    @Indexed(unique = true)
    private String evidenceKey;

    private String sourceUrl;

    private String publisher;

    private String contentHash;

    private Instant fetchedAt;

    private String text;

    private Map<String, Object> metadata;

    protected RawCapture() {
    }

    public RawCapture(String id, String evidenceKey, String sourceUrl, String publisher, String contentHash,
                      Instant fetchedAt, String text, Map<String, Object> metadata) {
        this.id = id;
        this.evidenceKey = evidenceKey;
        this.sourceUrl = sourceUrl;
        this.publisher = publisher;
        this.contentHash = contentHash;
        this.fetchedAt = fetchedAt;
        this.text = text;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public String getEvidenceKey() {
        return evidenceKey;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getText() {
        return text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
