package com.pistonpunk.gigahack26.service;

import java.util.Set;

public final class VendorRegistry {

    private static final Set<String> VENDOR_NAMES = Set.of(
            "uipath",
            "nintex",
            "celonis",
            "appian",
            "automation anywhere",
            "pegasystems",
            "blue prism",
            "kryon",
            "workfusion",
            "ssc",
            "ssc technologies",
            "software ag",
            "bizagi",
            "camunda",
            "signavio",
            "kofax",
            "tungsten automation",
            "servicenow",
            "abbyy",
            "hyperscience",
            "instabase",
            "bitdefender",
            "crowdstrike",
            "sentinelone",
            "darktrace",
            "zscaler",
            "netskope",
            "okta",
            "cyberark",
            "qualys",
            "tenable",
            "rapid7",
            "withsecure",
            "trend micro",
            "sophos",
            "veeam",
            "splunk",
            "dataiku",
            "datarobot",
            "c3 ai",
            "alteryx",
            "informatica",
            "talend",
            "collibra",
            "stibo systems",
            "ataccama",
            "databricks",
            "confluent");

    private static final Set<String> VENDOR_INDUSTRIES = Set.of(
            "automation software",
            "process mining",
            "robotic process automation",
            "rpa",
            "automation platforms",
            "intelligent document processing",
            "cybersecurity software");

    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "inc", "incorporated", "ltd", "limited", "plc", "llc", "corp", "corporation",
            "co", "company", "ag", "se", "sa", "nv", "bv", "gmbh", "ab", "asa", "oyj",
            "oy", "spa", "srl", "pte", "kk", "aps", "holding", "holdings", "group",
            "uk", "us", "usa", "de", "fr", "nl", "dk", "no", "fi", "ie", "it",
            "es", "pl", "ro", "md", "ch", "at", "be", "ca", "eu", "emea", "international",
            "global", "europe", "deutschland", "sas", "sl", "as", "sro");

    private VendorRegistry() {
    }

    public static boolean isVendor(String name, String industry) {
        return namedVendor(name) || vendorIndustry(industry);
    }

    public static boolean namedVendor(String name) {
        String normalised = strip(name);
        while (!normalised.isEmpty()) {
            if (VENDOR_NAMES.contains(normalised)) {
                return true;
            }
            int cut = normalised.lastIndexOf(' ');
            if (cut < 0 || !LEGAL_SUFFIXES.contains(normalised.substring(cut + 1))) {
                return false;
            }
            normalised = normalised.substring(0, cut);
        }
        return false;
    }

    public static boolean vendorIndustry(String industry) {
        return industry != null && VENDOR_INDUSTRIES.contains(strip(industry));
    }

    private static String strip(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char raw : value.toCharArray()) {
            char c = Character.toLowerCase(raw);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else if (c != '&' && out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                out.append(' ');
            }
        }
        return out.toString().trim();
    }
}
