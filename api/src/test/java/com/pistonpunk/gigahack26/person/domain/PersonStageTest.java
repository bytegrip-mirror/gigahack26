package com.pistonpunk.gigahack26.person.domain;

import com.pistonpunk.gigahack26.lead.domain.LeadStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The night this broke: the stage columns were added to a table that already held
 * 1140 people, Hibernate widened it leaving them empty, and a primitive int cannot
 * hold the null that leaves behind — so every read of every person threw and the
 * whole People board went to a 500.
 */
class PersonStageTest {

    private static Person person() {
        return new Person(UUID.randomUUID(), UUID.randomUUID(), "Bastien Parizot", "SVP",
                UUID.randomUUID(), 0, 10, "https://example.com", "Example", Instant.now(), Instant.now());
    }

    @Test
    void aPersonFromBeforeTheStageColumnsExistedHasNoNullToTripOn() {
        Person legacy = person();
        assertEquals(0, legacy.getContactCount());
        assertEquals(LeadStatus.NEW, legacy.getStatus());
    }

    @Test
    void anAddressIsAnAddressOrItIsNothing() {
        Person person = person();
        person.setEmail("bastien.parizot@reckitt.com");
        assertEquals("bastien.parizot@reckitt.com", person.getEmail());

        person.setEmail("not an address");
        assertEquals(null, person.getEmail());

        person.setEmail("a@b.co");
        assertEquals("a@b.co", person.getEmail());
    }

    @Test
    void aNewlineInAnAddressIsRefusedRatherThanMailed() {
        Person person = person();
        person.setEmail("someone@example.com\r\nBcc: leak@elsewhere.com");

        assertEquals(null, person.getEmail(),
                "the address reaches a mailto: header — a newline in it is an injection");
    }

    @Test
    void theCountMovesOnARealTransitionAndNotOnAReSave() {
        Person person = person();
        Instant now = Instant.now();

        person.moveTo(LeadStatus.CONTACTED, null, null, now);
        assertEquals(1, person.getContactCount());
        person.moveTo(LeadStatus.CONTACTED, null, null, now);
        assertEquals(1, person.getContactCount(), "re-saving the same stage is not another approach");
        person.moveTo(LeadStatus.REPLIED, null, null, now);
        assertEquals(2, person.getContactCount());
    }
}
