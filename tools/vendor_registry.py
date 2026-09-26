"""Who sells what we sell.

Mirrors api/src/main/java/com/pistonpunk/gigahack26/service/VendorRegistry.java.
The two must agree: the corpus tool stamps the flag onto the company record and
the scoring gate re-derives it, so a disagreement would show as a company that
is flagged in the data and not in the queue, or the reverse. There is a test in
the API that pins the Java side; this file is what the corpus build uses.

Only product vendors of the three service lines are named. Industrial automation
suppliers and general enterprise software are buyers, not competitors — flagging
Siemens or Dassault Systemes would be a factual error a juror can see.
"""

VENDOR_NAMES = {
    "uipath", "nintex", "celonis", "appian", "automation anywhere", "pegasystems",
    "blue prism", "kryon", "workfusion", "ssc", "ssc technologies", "software ag",
    "bizagi", "camunda", "signavio", "kofax", "tungsten automation", "servicenow",
    "abbyy", "hyperscience", "instabase", "bitdefender", "crowdstrike", "sentinelone",
    "darktrace", "zscaler", "netskope", "okta", "cyberark", "qualys", "tenable",
    "rapid7", "withsecure", "trend micro", "sophos", "veeam", "splunk", "dataiku",
    "datarobot", "c3 ai", "alteryx", "informatica", "talend", "collibra",
    "stibo systems", "ataccama", "databricks", "confluent",
}

VENDOR_INDUSTRIES = {
    "automation software", "process mining", "robotic process automation", "rpa",
    "automation platforms", "intelligent document processing", "cybersecurity software",
}

LEGAL_SUFFIXES = {
    "inc", "incorporated", "ltd", "limited", "plc", "llc", "corp", "corporation",
    "co", "company", "ag", "se", "sa", "nv", "bv", "gmbh", "ab", "asa", "oyj",
    "oy", "spa", "srl", "pte", "kk", "aps", "holding", "holdings", "group",
    "uk", "us", "usa", "de", "fr", "nl", "dk", "no", "fi", "ie", "it",
    "es", "pl", "ro", "md", "ch", "at", "be", "ca", "eu", "emea", "international",
    "global", "europe", "deutschland", "sas", "sl", "as", "sro",
}


def strip_legal(value):
    if not value:
        return ""
    out = []
    for raw in value:
        c = raw.lower()
        if c.isalnum():
            out.append(c)
        elif c != "&" and out and out[-1] != " ":
            out.append(" ")
    return "".join(out).strip()


def named_vendor(name):
    normalised = strip_legal(name)
    while normalised:
        if normalised in VENDOR_NAMES:
            return True
        cut = normalised.rfind(" ")
        if cut < 0 or normalised[cut + 1:] not in LEGAL_SUFFIXES:
            return False
        normalised = normalised[:cut]
    return False


def vendor_industry(industry):
    return bool(industry) and strip_legal(industry) in VENDOR_INDUSTRIES


def is_vendor(name, industry):
    return named_vendor(name) or vendor_industry(industry)
