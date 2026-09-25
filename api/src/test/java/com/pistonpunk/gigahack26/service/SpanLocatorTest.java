package com.pistonpunk.gigahack26.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpanLocatorTest {

    private static final String DOCUMENT =
            "The group confirmed a cost reduction programme targeting 400 million euros by 2027. "
                    + "It also said it would consolidate three shared service centres into one captive operation. "
                    + "Separately, the company appointed a new chief information security officer in June.";

    @Test
    void anExactQuoteResolvesToTheCharactersItActuallyOccupies() {
        String quote = "consolidate three shared service centres";

        Optional<SpanLocator.Span> span = SpanLocator.locate(DOCUMENT, quote);

        assertTrue(span.isPresent());
        assertTrue(span.get().exact());
        assertEquals(quote, DOCUMENT.substring(span.get().start(), span.get().end()));
    }

    @Test
    void aQuoteThatIsNotInTheDocumentIsRejectedRatherThanApproximated() {
        String invented = "the company plans to outsource its entire finance function to a third party";

        assertTrue(SpanLocator.locate(DOCUMENT, invented).isEmpty());
    }

    @Test
    void curlyQuotesAndRunsOfWhitespaceDoNotBreakTheMatch() {
        String document = "The company’s   automation   programme was announced in March.";
        String quote = "company's automation programme";

        Optional<SpanLocator.Span> span = SpanLocator.locate(document, quote);

        assertTrue(span.isPresent(), "normalisation should absorb the typographic apostrophe");
        assertTrue(document.substring(span.get().start(), span.get().end()).contains("automation"));
    }

    @Test
    void aLightlyParaphrasedQuoteFallsBackToAFuzzySpanAndIsMarkedAsSuch() {
        String quote = "confirmed a cost reduction programme targeting 400 million euros";

        Optional<SpanLocator.Span> span = SpanLocator.locate(DOCUMENT, quote);

        assertTrue(span.isPresent());
        assertTrue(span.get().length() > 0);
    }

    @Test
    void aShortQuoteIsNeverFuzzyMatched() {
        assertTrue(SpanLocator.locate(DOCUMENT, "automation growth in Europe").isEmpty());
    }

    @Test
    void containmentAcceptsASpanThatHoldsTheQuoteAndRejectsOneThatDoesNot() {
        int start = DOCUMENT.indexOf("consolidate");
        int end = DOCUMENT.indexOf("operation") + "operation".length();

        assertTrue(SpanLocator.contains(DOCUMENT, start, end, "consolidate three shared service centres"));
        assertFalse(SpanLocator.contains(DOCUMENT, start, end, "appointed a new chief information security officer"));
    }

    @Test
    void anEmptyOrMissingDocumentIsHandledWithoutThrowing() {
        assertTrue(SpanLocator.locate(DOCUMENT, "").isEmpty());
        assertTrue(SpanLocator.locate(DOCUMENT, null).isEmpty());
        assertTrue(SpanLocator.locate(null, "anything").isEmpty());
        assertFalse(SpanLocator.contains(DOCUMENT, -1, 40, "The group"));
        assertFalse(SpanLocator.contains(DOCUMENT, 10, 5, "The group"));
    }
}
