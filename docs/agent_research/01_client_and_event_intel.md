# Client and Event Intelligence

Researched 2026-09-25, day 1 of the hackathon. Everything here is either from the brief or from public sources with URLs — no assumptions.

## 1. Who is actually judging

**The challenge provider evaluates its own challenge.** GigaHack's model is that each participating company evaluates the prototypes submitted to it and decides how to take them forward. For this track that means the room is **Orange Systems**, not a neutral panel of generalist judges.

Two consequences that change how the work should be built and pitched:

1. **Optimise for adoption, not for applause.** The stated follow-on is that Orange Systems continues collaboration with the winning team to turn the AI solution into a real product. The demo should look like the first sprint of a product they already want, not a science-fair exhibit.
2. **They will recognise their own vocabulary.** Orange Systems people will react to *their* service names, *their* markets, *their* compliance realities. Generic sales-intelligence language will land flat.

**Prize discrepancy worth knowing before it is discovered on stage:** the public event site lists the AI/ML track (provided by Orange) at **30,000 MDL** — roughly €1,500 — while the challenge brief in `docs/MAIN_CHALLENGE.md` states **€30,000 cash for the winning team**. The same site lists 30,000 MDL for most tracks and 90,000 MDL for the cybersecurity track. The brief is the challenge-specific document and is the more authoritative source, but the two do not agree and it is worth confirming with the organisers rather than assuming.

## 2. Orange Systems — who the platform is for

- The IT hub of Orange Moldova, Chișinău (8, Calea Ieșilor), an IT Park resident, **850+ employees**, one of the largest IT hubs in Moldova. General Director Olga Surugiu since April 2022.
- Sells full-cycle software delivery and advanced IT services **internationally** — delivery across Europe and the AMEA region (Asia, Middle East, Africa), working with Orange Group entities (MasOrange, Orange Jordan) and external partners (Mobile Zone in Switzerland, Aggranda).
- Service lines named publicly: solution design and development, QA, business intelligence, project management and business analysis, data and reporting, **RPA**, **process modelling**, **cybersecurity**. 400+ applications managed.
- Automation is the strategic centre of gravity, not a side line.

### Their flagship: InSight KYC

This is the single most useful fact for this challenge. InSight KYC is Orange Systems' own AI + RPA product for customer identification and compliance:

- Cut client onboarding from **8 hours to under 1 hour**
- Saved **over 2 million hours** of manual work annually
- Delivered across **5 countries in AMEA**, reaching 45M+ customers
- Won **Best Service Innovation, MITP Awards 2025** (Dec 2025)
- On the strength of it, the Moldova hub became the **Center of Excellence in RPA competencies for the entire Orange Group**

They also run an **RPA Academy** (UiPath) and a **JUMP internship** with a Cyber Security track.

**Why this matters:** Orange Systems has already proven it buys into automation products with measurable hour savings, and it sells compliance/onboarding automation into regulated industries. The signals their sales team most needs to detect are therefore not generic "digital transformation" — they are the specific pressures that make a company need *what Orange Systems actually sells*: RPA and process automation, KYC/AML and onboarding cost, cybersecurity posture. A signal taxonomy rooted in InSight KYC's value story is a taxonomy no competitor would bring.

## 3. Their buyers and markets (implied by their own portfolio)

Telcos, banks and financial institutions, and public sector bodies across Europe, the Middle East and Africa — organisations with (a) large manual back-office volumes, (b) regulatory onboarding obligations, (c) an existing digital transformation budget. A credible ICP for the demo is one of these, described using their language.

## 4. Gaps in what we were given

- **Annex 1: Participant Reference Pack is referenced by the brief but not present** in the repo (`docs/` contains only the brief and the hypothesis). It is described as containing the current manual sales process and two illustrative examples — exactly the raw material that would let the demo mirror the team's real workflow rather than an invented one. Worth requesting from the organisers; if it arrives, `40_mvp_spec.md` should be revisited, because a demo that recreates their *actual* two examples is far stronger than one that recreates a plausible one.
- The event site publishes no rubric and no jury list. The scoring weights in `MAIN_CHALLENGE.md` (25/20/20/15/10/10) are therefore the only rubric we have, and should be treated as exact.
- No published schedule beyond Day 3 "Final showcase → judging → awards", with a mentorship checkpoint and project upload before it.

## 5. Sources

- Challenge brief and scoring: `docs/MAIN_CHALLENGE.md`
- Orange Systems company profile, services, leadership: [invest.gov.md ICT sector report](https://invest.gov.md/wp-content/uploads/2024/06/11-06-ICT%202024.pdf), [Orange Systems management report 2025](https://www.orange.md/files/pdfs/other/rapoarte/2025/6_Raportul_Conducerii_Orange_Systems_2025.pdf)
- InSight KYC, RPA Center of Excellence, MITP award: [Orange Systems Best Service Innovation award](https://jobs.orange.md/?l=1&p=1&c=1&sc=12&n=1784), [privesc.eu on the AI solution award](https://www.privesc.eu/article/110233/3df05db5-d0ca-4348-909c-926ea12ea2ff)
- RPA roles and UiPath focus: [orange.jobs RPA Developer posting](https://orange.jobs/fr/fr/job/ERC-542191-20136094/)
- Event format, tracks, prizes, provider-evaluates model: [gigahack.md](https://gigahack.md/), [Technovator announcement](https://technovator.world/deeptech-gigahack-returns-for-its-fourth-edition-with-online-participation-for-the-first-time-and-a-new-visual-identity/)
