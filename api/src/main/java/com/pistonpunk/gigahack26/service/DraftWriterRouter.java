package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.web.dto.OutreachDraft;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DraftWriterRouter {

    private final ModelDraftWriter model;
    private final TemplateDraftWriter template;

    public DraftWriterRouter(ModelDraftWriter model, TemplateDraftWriter template) {
        this.model = model;
        this.template = template;
    }

    public DraftWriter.Composed write(DraftWriter.Brief brief, List<OutreachDraft.Citation> citations) {
        DraftWriter.Composed composed = template.write(brief, citations);
        if (!model.available()) {
            return new DraftWriter.Composed(composed.subject(), composed.body(),
                    composed.engine() + ", no model configured");
        }
        DraftWriter.Composed written = model.write(brief, citations);
        if (written != null) {
            return written;
        }
        return new DraftWriter.Composed(composed.subject(), composed.body(),
                composed.engine() + ", model call failed");
    }

    public boolean modelAvailable() {
        return model.available();
    }
}
