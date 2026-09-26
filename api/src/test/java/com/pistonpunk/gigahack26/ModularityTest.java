package com.pistonpunk.gigahack26;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesHaveNoCyclesAndUseOnlyExposedTypes() {
        ApplicationModules.of(Gigahack26Application.class).verify();
    }
}
