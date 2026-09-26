package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.repository.jpa.IcpProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Three books of business so the board has something to filter by on a database
 * that has just been created.
 *
 * Seeded only when there is nothing there at all, which is why there is no key to
 * match on: a workspace that has profiles is one whose operator has decided what
 * they sell, and this class has no business adding to that or putting back
 * something they deleted.
 */
@Component
@Order(15)
public class IcpProfileLibrary implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IcpProfileLibrary.class);

    private final IcpProfileService profiles;

    public IcpProfileLibrary(IcpProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public int seed() {
        if (!profiles.list().isEmpty()) {
            return 0;
        }
        List<IcpProfileService.Draft> examples = List.of(
                new IcpProfileService.Draft("DACH banks and insurers", "#2f6f4f", "", "dach",
                        "Banking,Insurance", 500, "", true, false, true),
                new IcpProfileService.Draft("Nordics telecom and banking", "#1f4e79", "", "nordics",
                        "Banking,Telecom", 1000, "", false, true, true),
                new IcpProfileService.Draft("North America telecom and retail", "#7a3fa0", "", "north-america",
                        "Telecom,Retail", 5000, "5001-10000,10001+", false, false, true));
        for (IcpProfileService.Draft draft : examples) {
            profiles.create(draft);
        }
        log.info("placed {} example customer profiles", examples.size());
        return examples.size();
    }
}
