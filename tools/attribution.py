"""Whether a document is about the company it is filed under.

This is the one check the whole platform rests on and the one that was missing.
The crawlers matched a company name to a document with `if variant.lower() in
text.lower()`, so the corpus shipped a New Zealand yachting report under SEB
("Seb" Menzies), a Czech cruise-missile story and a Nigerian election story
under LPP, "Best Orange Cake Ever" under Orange, a physics-AI startup's job ads
under VINCI, and a pension scheme's registry row under AstraZeneca. The scoring
ledger, the theme cap and the span verifier are all precise machines computing
over documents that, in a material fraction of cases, were about somebody else.

The rules live here so the crawlers, the sanitiser and the CI gate all use the
same ones.
"""

import re
import unicodedata

ATS_HOSTS = ("greenhouse.io", "lever.co", "ashbyhq.com", "smartrecruiters.com", "workday.com")

# Names whose lowercase form is an ordinary given name or word, so a
# case-insensitive match would file a yachtsman under a bank.
CASE_SENSITIVE_SHORT = {"SEB", "KONE"}


def fold(text):
    """Lowercase and strip diacritics, so Komerční matches Komercni."""
    if not text:
        return ""
    decomposed = unicodedata.normalize("NFKD", text)
    stripped = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return stripped.lower()


def variants_for(company):
    values = [company.get("name") or ""]
    values.extend(company.get("aliases") or [])
    return [v.strip() for v in values if v and v.strip()]


def mentions_company(company_or_variants, text):
    """A whole-token match of the company's name or one of its aliases.

    Whole-token because `nexi` matched "NexImmune" and `lpp` matched a cruise
    missile. Folded because "Komerční banka" is "Komercni banka" to a crawler.
    """
    if isinstance(company_or_variants, dict):
        values = variants_for(company_or_variants)
    else:
        values = [v for v in company_or_variants if v]
    if not text or not values:
        return False
    folded = fold(text)
    for value in values:
        if value.upper() in CASE_SENSITIVE_SHORT:
            if re.search(r"(?<![A-Za-z0-9])" + re.escape(value) + r"(?![A-Za-z0-9])", text):
                return True
            continue
        needle = fold(value)
        if needle and re.search(r"(?<![a-z0-9])" + re.escape(needle) + r"(?![a-z0-9])", folded):
            return True
    return False


def host_of(url):
    match = re.match(r"https?://([^/]+)", url or "")
    return match.group(1).lower() if match else ""


def board_slug(url):
    """The first path segment, which is the board name on every ATS we read.

    jobs.ashbyhq.com/vinci/..., job-boards.greenhouse.io/abinbev/jobs/... and
    jobs.smartrecruiters.com/kpn/744... all put the company in the path.
    """
    match = re.match(r"https?://[^/]+/([^/?#]+)", url or "")
    return fold(match.group(1)) if match else ""


def domain_labels(company):
    labels = set(re.findall(r"[a-z0-9]+", fold(company.get("domain") or "")))
    return labels - {"com", "net", "org", "co", "eu", "io", "de", "fr", "nl", "uk", "www",
                     "group", "grp", "the"}


def ats_board_belongs_to(company, url, text):
    """Whether an ATS board is this company's.

    The board list was guessed from the company name and the first slug that
    answered won, which is how a physics-AI startup's postings landed under
    VINCI and a Canadian telehealth company's under Phoenix Group. A board is
    accepted only when its slug is part of the company's own domain, or when
    the posting itself names the company.
    """
    host = host_of(url)
    if not any(host.endswith(ats) for ats in ATS_HOSTS):
        return True
    slug = board_slug(url)
    if slug and slug in domain_labels(company):
        return True
    return mentions_company(company, text)
