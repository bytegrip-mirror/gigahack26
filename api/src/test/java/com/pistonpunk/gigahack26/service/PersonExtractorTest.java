package com.pistonpunk.gigahack26.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonExtractorTest {

    @Test
    void aNameWithARoleBesideItIsAPerson() {
        List<PersonExtractor.Found> found =
                PersonExtractor.find("Bastien Parizot, senior vice president of global business services, said the plan would continue.");

        assertEquals(1, found.size());
        assertEquals("Bastien Parizot", found.get(0).name());
        assertTrue(found.get(0).role().toLowerCase().contains("senior vice president"));
    }

    @Test
    void aNameTheRoleIsWrittenAfterIsAlsoAPerson() {
        List<PersonExtractor.Found> found =
                PersonExtractor.find("The chief executive officer Jane Doe told analysts the target was unchanged.");

        assertEquals(1, found.size());
        assertEquals("Jane Doe", found.get(0).name());
    }

    @Test
    void aTitleWithoutANameIsNotAPerson() {
        assertTrue(PersonExtractor.find("The company said its chief financial officer would leave in March.").isEmpty(),
                "a role on its own tells a rep nothing — there is nobody to write to");
    }

    @Test
    void aNameOnItsOwnIsNotAPersonEither() {
        assertTrue(PersonExtractor.find("Volkswagen Group reported results in Wolfsburg, Germany.").isEmpty(),
                "a proper noun is not a contact");
    }

    @Test
    void aTitleWrittenAsInitialsKeepsItsInitials() {
        List<PersonExtractor.Found> found = PersonExtractor.find("Ana Silva, ceo, said the target was unchanged.");
        assertEquals(1, found.size());
        assertEquals("CEO", found.get(0).role());
    }

    @Test
    void aHeadOfStateIsNotAContact() {
        assertTrue(PersonExtractor.find("Recep Tayyip Erdoğan, president, opened the terminal.").isEmpty(),
                "a bare president is as likely to run a country as a company, and this desk cannot tell");
        assertEquals(1, PersonExtractor.find("Ana Silva, president of the logistics division, said.").size(),
                "a president of a named part of the business is a contact");
    }

    @Test
    void theSamePersonIsReturnedOnceWithTheirFullestTitle() {
        String text = "Ana Silva, chief executive officer, said. Later Ana Silva, chief executive officer of the group, added.";
        List<PersonExtractor.Found> found = PersonExtractor.find(text);
        assertEquals(1, found.size());
    }
}
