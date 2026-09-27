package com.pistonpunk.gigahack26.service;

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
                        PolicyLibrary.DIGITAL,
                        PolicyLibrary.DIGITAL,
                        "Customer-facing channels: portals, commerce and the journeys through them.",
                        "we ship the channel a customer actually finishes, and measure it after launch",
                        50));
    }
}
