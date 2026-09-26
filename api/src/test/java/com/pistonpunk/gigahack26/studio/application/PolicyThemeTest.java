package com.pistonpunk.gigahack26.studio.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolicyThemeTest {

    @Test
    void oneTopicHasOneSpellingWhateverTheTypistWrote() {
        assertEquals("efficiency", PolicyService.normaliseTheme("Efficiency"));
        assertEquals("efficiency", PolicyService.normaliseTheme("  efficiency "));
        assertEquals("operating-model", PolicyService.normaliseTheme("Operating Model"));
        assertEquals("operating-model", PolicyService.normaliseTheme("operating_model"));
        assertEquals("operating-model", PolicyService.normaliseTheme("operating---model"));
        assertEquals("data-and-ai", PolicyService.normaliseTheme("data-and-ai"));
    }

    @Test
    void anEmptyThemeStillNamesSomething() {
        assertEquals("custom", PolicyService.normaliseTheme(null));
        assertEquals("custom", PolicyService.normaliseTheme("   "));
        assertEquals("custom", PolicyService.normaliseTheme("---"));
    }
}
