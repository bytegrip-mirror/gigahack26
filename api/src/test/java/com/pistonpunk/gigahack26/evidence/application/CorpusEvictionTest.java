package com.pistonpunk.gigahack26.evidence.application;

import com.pistonpunk.gigahack26.evidence.domain.Evidence;
import com.pistonpunk.gigahack26.evidence.infrastructure.CaptureStore;
import com.pistonpunk.gigahack26.seeding.application.CorpusSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusEvictionTest {

    @Test
    void theEvictionRunsInItsOwnTransaction() throws Exception {
        Method drop = CorpusEviction.class.getMethod("drop", java.util.List.class);

        Transactional annotation = drop.getAnnotation(Transactional.class);
        assertNotNull(annotation,
                "a bulk delete needs an active transaction, and the seeder that calls this has none: "
                        + "without it the application fails to start and crash-loops");

        Transactional onClass = CorpusEviction.class.getAnnotation(Transactional.class);
        assertTrue(onClass != null || annotation != null);
    }

    @Test
    void theSeederHasNoDeleteMethodOfItsOwn() {
        for (Method method : CorpusSeeder.class.getDeclaredMethods()) {
            assertTrue(!method.getName().startsWith("delete"),
                    "a bulk delete belongs behind the transactional bean, not in the runner: " + method.getName());
        }
    }

    @Test
    void emptyInputIsNotATransactionAwayFromHarm() {
        CorpusEviction eviction = new CorpusEviction(null, null, null, new CaptureStore(null));
        assertTrue(eviction.drop(java.util.List.<Evidence>of()) == 0);
    }
}
