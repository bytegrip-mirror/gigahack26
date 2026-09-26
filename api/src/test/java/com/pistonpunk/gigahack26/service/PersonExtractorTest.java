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
    void aFragmentThatIsNotANameIsRefused() {
        assertTrue(PersonExtractor.find("Americas US.CA,MEDIAandPR, head of communications, said.").isEmpty(),
                "punctuation inside a token means the pattern caught a place name or a headline");
        assertTrue(PersonExtractor.find("Kris Licht.The, ceo, said.").isEmpty(),
                "a sentence break inside the name means it was two sentences");
        assertTrue(PersonExtractor.find("The IEA, ceo, said.").isEmpty(),
                "an all-capitals token is an organisation, not a person");
    }

    @Test
    void aTitleBeforeTheNameIsNotPartOfIt() {
        List<PersonExtractor.Found> found = PersonExtractor.find("Prof. Vartika Saxena, head of community medicine, said.");
        assertEquals(1, found.size());
        assertEquals("Vartika Saxena", found.get(0).name());
    }

    @Test
    void theSamePersonIsReturnedOnceWithTheirFullestTitle() {
        String text = "Ana Silva, chief executive officer, said. Later Ana Silva, chief executive officer of the group, added.";
        List<PersonExtractor.Found> found = PersonExtractor.find(text);
        assertEquals(1, found.size());
    }
}
