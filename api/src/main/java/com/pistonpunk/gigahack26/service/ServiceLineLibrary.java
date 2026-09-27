package com.pistonpunk.gigahack26.service;

import java.util.List;
import java.util.Set;

public final class ServiceLineLibrary {

    public record Definition(String key, String name, String description, String valueProposition, int sortOrder) {
    }

    private ServiceLineLibrary() {
    }

    public static List<Definition> definitions() {
        return List.of(
                new Definition(
                        PolicyLibrary.AUTOMATION,
                        PolicyLibrary.AUTOMATION,
                        "Process automation, process mining and the operating-model work around them.",
                        "we run the automation programme end to end so your team keeps the process knowledge "
                                + "and we carry the delivery",
                        0),
                new Definition(
                        PolicyLibrary.SECURITY,
                        PolicyLibrary.SECURITY,
                        "Regulatory obligations, incident response and the security operating model.",
                        "we close the compliance gap against a dated obligation and hand over a running "
                                + "capability, not a report",
                        10),
                new Definition(
                        PolicyLibrary.DATA,
                        PolicyLibrary.DATA,
                        "Data platforms, governance and taking a named AI use case into production.",
                        "we take one named use case to production and leave a platform your team can extend",
                        20),
                new Definition(
                        PolicyLibrary.CLOUD,
                        PolicyLibrary.CLOUD,
                        "Cloud migration, platform engineering and the estate left behind on premises.",
                        "we move the estate without a freeze, and leave the platform team that runs it",
                        30),
                new Definition(
                        PolicyLibrary.ERP,
                        PolicyLibrary.ERP,
                        "Core system replacement — ERP, finance and the processes wrapped around them.",
                        "we land the core system on a dated cutover and stay through the first close",
                        40),
                new Definition(
                        PolicyLibrary.SOFTWARE,
                        PolicyLibrary.SOFTWARE,
                        "Delivery capacity for the software a company has decided to build itself.",
                        "we put a team behind the roadmap you have already committed to, and hand over the code, "
                                + "the tests and the people who wrote them",
                        50));
    }

    /**
     * Lines that were seeded once and are not any more.
     *
     * A line is never deleted: the signals, scores and feedback that point at it
     * are a record of what the board said at the time, and a row is cheaper to
     * keep than a cascade. But a renamed line leaves its old row behind with no
     * questions under it, and that row stays in the picker and returns an empty
     * board — so the ones that have been superseded are named here and switched
     * off. "Digital Customer Experience" became "Software Engineering": same slot,
     * same sort order, different thing sold.
     *
     * This is a list and not "retire whatever the library does not declare",
     * because a service line can be created by hand on the Service lines screen,
     * and that would switch off the user's own work on the next boot.
     */
    public static Set<String> retired() {
        return Set.of("Digital Customer Experience");
    }
}
