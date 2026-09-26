package com.pistonpunk.gigahack26.web.dto;

import java.util.List;
import java.util.UUID;

public record RescoreResponse(
        String serviceLine,
        long tookMs,
        int companies,
        int documentsScanned,
        int signalsProduced,
        String engine,
        String question,
        int movedRows,
        int changedTop,
        List<Move> moves,
        List<BoardRow> board,
        List<String> bands,
        boolean keptPrevious) {

    public record Move(
            UUID companyId,
            String company,
            int beforeRank,
            int afterRank,
            int delta,
            Double beforeProbability,
            double afterProbability,
            String band,
            String direction) {
    }
}
