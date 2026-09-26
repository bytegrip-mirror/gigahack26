package com.pistonpunk.gigahack26.board.application;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class MarketService {

    public record Slice(String key, long companies, long inMarket, long signals, long withEvidence,
                        double medianFit, double topFit, Double medianAgeDays) {
    }

    public record Market(String serviceLine, long companies, long inMarket, long signals, long blindSpots,
                         List<Slice> countries, List<Slice> industries, List<Slice> sizeBands) {
    }

    private static final int MIN_COMPANIES = 3;

    private final BoardService board;

    public MarketService(BoardService board) {
        this.board = board;
    }

    public Market market(String serviceLine) {
        List<BoardRow> rows = board.board(serviceLine, "ALL", "rank", null, null, null);
        return new Market(
                serviceLine,
                rows.size(),
                rows.stream().filter(MarketService::inMarket).count(),
                rows.stream().mapToLong(BoardRow::signalCount).sum(),
                rows.stream().filter(row -> row.evidenceCount() == 0).count(),
                slice(rows, BoardRow::country),
                slice(rows, BoardRow::industry),
                slice(rows, BoardRow::sizeBand));
    }

    /**
     * A market still being worked is a market with companies in it that have not
     * been called yet; the two bands that mean "worth a look" are the ones the
     * buyer is asking about, so the slice reports them and not the raw score.
     */
    private static boolean inMarket(BoardRow row) {
        return "PRIORITY".equals(row.band()) || "NURTURE".equals(row.band());
    }

    private static List<Slice> slice(List<BoardRow> rows, Function<BoardRow, String> key) {
        Map<String, List<BoardRow>> grouped = new LinkedHashMap<>();
        for (BoardRow row : rows) {
            String value = key.apply(row);
            if (value == null || value.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(value, ignored -> new ArrayList<>()).add(row);
        }

        List<Slice> slices = new ArrayList<>();
        for (Map.Entry<String, List<BoardRow>> entry : grouped.entrySet()) {
            List<BoardRow> members = entry.getValue();
            if (members.size() < MIN_COMPANIES) {
                continue;
            }
            List<Double> fits = members.stream().map(BoardRow::probability).sorted().toList();
            List<Double> ages = members.stream()
                    .map(BoardRow::newestSignalAgeDays)
                    .filter(age -> age != null)
                    .sorted()
                    .toList();
            slices.add(new Slice(
                    entry.getKey(),
                    members.size(),
                    members.stream().filter(MarketService::inMarket).count(),
                    members.stream().mapToLong(BoardRow::signalCount).sum(),
                    members.stream().filter(row -> row.evidenceCount() > 0).count(),
                    round(median(fits)),
                    round(fits.isEmpty() ? 0.0 : fits.get(fits.size() - 1)),
                    ages.isEmpty() ? null : round(median(ages))));
        }

        slices.sort(Comparator.comparingLong(Slice::inMarket).reversed()
                .thenComparing(Comparator.comparingLong(Slice::companies).reversed())
                .thenComparing(Slice::key));
        return slices;
    }

    private static double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
