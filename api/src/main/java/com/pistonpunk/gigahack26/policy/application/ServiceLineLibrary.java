package com.pistonpunk.gigahack26.policy.application;

import java.util.List;

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
                        20));
    }
}
