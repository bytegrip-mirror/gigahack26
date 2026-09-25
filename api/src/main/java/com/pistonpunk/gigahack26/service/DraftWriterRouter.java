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
        if (model.available()) {
            DraftWriter.Composed composed = model.write(brief, citations);
            if (composed != null) {
                return composed;
            }
        }
        return template.write(brief, citations);
    }

    public boolean modelAvailable() {
        return model.available();
    }
}
