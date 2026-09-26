package com.pistonpunk.gigahack26.evidence.application;

import com.pistonpunk.gigahack26.evidence.domain.Evidence;
import com.pistonpunk.gigahack26.evidence.infrastructure.CaptureStore;
import com.pistonpunk.gigahack26.evidence.infrastructure.EvidenceRepository;
import com.pistonpunk.gigahack26.evidence.infrastructure.RawCaptureRepository;
import com.pistonpunk.gigahack26.evidence.infrastructure.SignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CorpusEviction {

    private final EvidenceRepository evidence;
    private final SignalRepository signals;
    private final RawCaptureRepository captures;
    private final CaptureStore captureStore;

    public CorpusEviction(EvidenceRepository evidence, SignalRepository signals,
                          RawCaptureRepository captures, CaptureStore captureStore) {
        this.evidence = evidence;
        this.signals = signals;
        this.captures = captures;
        this.captureStore = captureStore;
    }

    @Transactional
    public int drop(List<Evidence> stale) {
        if (stale.isEmpty()) {
            return 0;
        }
        signals.deleteByEvidenceIdIn(stale.stream().map(Evidence::getId).toList());
        evidence.deleteAll(stale);
        for (Evidence item : stale) {
            captures.deleteByEvidenceKey(item.getKey());
            captureStore.forget(item.getKey());
        }
        return stale.size();
    }
}
