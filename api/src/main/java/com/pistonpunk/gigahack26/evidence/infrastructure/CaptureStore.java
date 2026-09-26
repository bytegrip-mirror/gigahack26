package com.pistonpunk.gigahack26.evidence.infrastructure;

import com.pistonpunk.gigahack26.evidence.domain.RawCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptureStore {

    private static final Logger log = LoggerFactory.getLogger(CaptureStore.class);

    private final RawCaptureRepository captures;
    private final Map<String, String> texts = new ConcurrentHashMap<>();

    public CaptureStore(RawCaptureRepository captures) {
        this.captures = captures;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        long started = System.nanoTime();
        for (RawCapture capture : captures.findAll()) {
            texts.put(capture.getEvidenceKey(), capture.getText());
        }
        log.info("loaded {} raw captures in {} ms", texts.size(), (System.nanoTime() - started) / 1_000_000);
    }

    public void put(String evidenceKey, String text) {
        texts.put(evidenceKey, text);
    }

    public String text(String evidenceKey) {
        String cached = texts.get(evidenceKey);
        if (cached != null) {
            return cached;
        }
        return captures.findByEvidenceKey(evidenceKey)
                .map(capture -> {
                    texts.put(evidenceKey, capture.getText());
                    return capture.getText();
                })
                .orElse(null);
    }

    public void forget(String evidenceKey) {
        texts.remove(evidenceKey);
    }

    public int size() {
        return texts.size();
    }
}
