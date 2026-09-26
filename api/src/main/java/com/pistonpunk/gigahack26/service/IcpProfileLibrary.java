package com.pistonpunk.gigahack26.service;

import com.pistonpunk.gigahack26.domain.IcpProfile;
import com.pistonpunk.gigahack26.repository.jpa.IcpProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ten books of business, so a database that has just been created opens on a
 * board that means something and nobody has to click ten profiles together after
 * every deploy.
 *
 * They are matched by name and only added when missing. A profile that is already
 * there is left exactly as it is, edits and all — this class restores what is
 * absent, it does not overwrite what an operator has made their own. The one
 * consequence worth knowing: deleting one of these by hand brings it back on the
 * next start.
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
        Set<String> held = new HashSet<>();
        for (IcpProfile profile : profiles.list()) {
            held.add(profile.getName());
        }
        int added = 0;
        for (IcpProfileService.Draft draft : examples()) {
            if (held.contains(draft.name())) {
                continue;
            }
            profiles.create(draft);
            added += 1;
        }
        if (added > 0) {
            log.info("placed {} example customer profiles", added);
        }
        return added;
    }

    private static List<IcpProfileService.Draft> examples() {
        return List.of(
                new IcpProfileService.Draft("DACH banks and insurers", "#2f6f4f", "", "dach",
                        "Banking,Insurance", 500, "", true, false, true),
                new IcpProfileService.Draft("Nordics telecom and banking", "#1f4e79", "", "nordics",
                        "Banking,Telecom", 1000, "", false, true, true),
                new IcpProfileService.Draft("North America telecom and retail", "#7a3fa0", "", "north-america",
                        "Telecom,Retail", 5000, "5001-10000,10001+", false, false, true),
                new IcpProfileService.Draft("UK and Ireland financial services", "#0f6f7a", "", "uk-ireland",
                        "Banking,Insurance,Payments,Financial Infrastructure", 1000, "", true, false, true),
                new IcpProfileService.Draft("Benelux logistics and postal", "#4f5f2f", "", "benelux",
                        "Logistics,Postal", 500, "", false, false, true),
                new IcpProfileService.Draft("Southern Europe retail and apparel", "#3f3f6f", "", "southern-europe",
                        "Retail,Apparel", 1000, "", false, false, true),
                new IcpProfileService.Draft("Central Europe automotive and machinery", "#5f3f1f", "", "central-europe",
                        "Automotive,Industrial Machinery,Industrial Engineering", 1000, "", false, false, true),
                new IcpProfileService.Draft("France pharmaceuticals and medical devices", "#2f4f6f", "", "france",
                        "Pharmaceuticals,Medical Devices,Biotechnology", 500, "", false, false, true),
                new IcpProfileService.Draft("South-East Europe IT services", "#6f1f5f", "", "southeast-europe",
                        "IT Services,Business Process Outsourcing,Software", 200, "", false, false, true),
                new IcpProfileService.Draft("United States pharmaceuticals and life sciences", "#4f6f7f", "",
                        "united-states", "Pharmaceuticals,Biotechnology,Medical Devices", 1000, "", false, false,
                        true));
    }
}
