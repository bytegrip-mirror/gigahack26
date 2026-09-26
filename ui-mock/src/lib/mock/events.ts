import type { CompanyEvent } from './types';

export const events: CompanyEvent[] = [
	{
		"id": "e-lh-jobs",
		"companyId": "lufthansa",
		"type": "RESTRUCTURING",
		"date": "2025-09-29",
		"summary": "Plans to cut about 4,000 jobs by 2030, mostly administrative roles",
		"documentId": "d-lh-cmd",
		"quote": "the Lufthansa Group plans to reduce its workforce by approximately 4,000 jobs worldwide by 2030, with the majority of these cuts expected to occur in Germany. This will be done in consultation with the social partners. The focus will be on administrative rather than operational roles."
	},
	{
		"id": "e-lh-ai",
		"companyId": "lufthansa",
		"type": "STRATEGY",
		"date": "2025-09-29",
		"summary": "Names digitalization and AI as the drivers of process efficiency",
		"documentId": "d-lh-cmd",
		"quote": "In particular, the profound changes brought about by digitalization and the increased use of artificial intelligence will lead to greater efficiency in many areas and processes."
	},
	{
		"id": "e-lh-cargo",
		"companyId": "lufthansa",
		"type": "STRATEGY",
		"date": "2025-09-29",
		"summary": "Invests 600 million euros in the Frankfurt cargo hub, with more automation",
		"documentId": "d-lh-cmd",
		"quote": "significant investments of 600 million euros in the central cargo hub at Frankfurt Airport, and the consistent expansion of automation and digitalization are increasing Lufthansa Cargo’s profitability"
	},
	{
		"id": "e-lh-digital",
		"companyId": "lufthansa",
		"type": "IN_HOUSE_BUILD",
		"date": "2025-09-29",
		"summary": "Moves all IT under one board member and merges its digital units",
		"documentId": "d-lh-cmd",
		"quote": "By consolidating all IT functions in one Executive Board department and combining the digital units and competencies from the ‘Digital Hangar’ with the ‘Innovation & Tech Factory’ in a new central role, the aim is to expand digital expertise significantly."
	},
	{
		"id": "e-lh-profit",
		"companyId": "lufthansa",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-04-23",
		"summary": "Strategy chief says profit is good and must get better",
		"documentId": "d-lh-profit",
		"quote": "We have a good profit… We want to have a better profit"
	},
	{
		"id": "e-lh-itreorg",
		"companyId": "lufthansa",
		"type": "RESTRUCTURING",
		"date": "2026-04-23",
		"summary": "Reorganises its 8,000 IT staff and consolidates back-office IT and AI",
		"documentId": "d-lh-reorg",
		"quote": "consolidating back-office functions, such as IT and AI to achieve operational efficiency. Mr Goudarzi Pour added: \"We have 8000 people in IT only, and we are streamlining, and we are reorganising this completely\""
	},
	{
		"id": "e-lh-infosys",
		"companyId": "lufthansa",
		"type": "VENDOR_CONTRACT",
		"date": "2025-02-18",
		"summary": "Signs a long-term collaboration with Infosys and opens a capability center in India",
		"documentId": "d-lh-infosys",
		"quote": "today announced a strategic, long-term collaboration with Lufthansa Group (LHG), a global aviation group, and Lufthansa Systems GmbH (LSY), leading airline IT provider, to accelerate digital transformation and drive innovation in the aviation industry"
	},
	{
		"id": "e-lh-amadeus",
		"companyId": "lufthansa",
		"type": "VENDOR_CONTRACT",
		"date": "2026-01-07",
		"summary": "Selects Amadeus as a key technology provider for airline retailing",
		"documentId": "d-lh-amadeus",
		"quote": "Amadeus has been selected as a key technology provider by the Lufthansa Group for the next stage of its retailing transformation."
	},
	{
		"id": "e-dhl-q2",
		"companyId": "dhl",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-09-24",
		"summary": "Second-quarter EBIT up 30 percent to EUR 1.9 billion",
		"documentId": "d-dhl-q2",
		"quote": "The central operating figure is second-quarter EBIT of EUR 1.9 billion, an increase of 30 percent from EUR 1.4 billion in the same quarter of 2025."
	},
	{
		"id": "e-dhl-forecast",
		"companyId": "dhl",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-09-24",
		"summary": "Raises its 2026 operating profit forecast above EUR 6.5 billion",
		"documentId": "d-dhl-q2",
		"quote": "DHL raised its 2026 operating profit forecast to more than EUR 6.5 billion"
	},
	{
		"id": "e-dhl-accio",
		"companyId": "dhl",
		"type": "PARTNERSHIP",
		"date": "2026-09-10",
		"summary": "Signs an MoU with Alibaba.com to automate freight quotes and booking through an AI agent",
		"documentId": "d-dhl-alibaba",
		"quote": "Through an agentic approach, Accio will be able to connect to DHL Global Forwarding's Quotation & Booking capabilities, enabling businesses to obtain real-time freight forwarding quotes, evaluate shipping options and book shipments more seamlessly."
	},
	{
		"id": "e-dhl-suite",
		"companyId": "dhl",
		"type": "TECH_ADOPTION",
		"date": "2026-09-10",
		"summary": "Plans a suite of capabilities to automate key business tasks",
		"documentId": "d-dhl-alibaba",
		"quote": "The new capability is the first in a planned suite of DHL logistics capabilities designed to automate key business tasks, improve operational efficiency and enable SMEs to scale more effectively."
	},
	{
		"id": "e-dhl-radar",
		"companyId": "dhl",
		"type": "STRATEGY",
		"date": "2026-09-24",
		"summary": "Says AI in logistics is moving from assistance to autonomy",
		"documentId": "d-dhl-radar",
		"quote": "AI in logistics is evolving from assistance to autonomy."
	},
	{
		"id": "e-dhl-tradenav",
		"companyId": "dhl",
		"type": "IN_HOUSE_BUILD",
		"date": "2026-09-17",
		"summary": "Runs its own AI analytics tool, TradeNavigator",
		"documentId": "d-dhl-customs",
		"quote": "DHL TradeNavigator is an AI-based analytics tool in DHL Global Forwarding's TradeConnect environment."
	},
	{
		"id": "e-dhl-openmarket",
		"companyId": "dhl",
		"type": "M_AND_A",
		"date": "2026-09-15",
		"summary": "Agrees to buy Open Market, a logistics operator in Colombia",
		"documentId": "d-dhl-openmarket",
		"quote": "DHL Supply Chain today announced it has entered into an agreement to acquire Open Market, a logistics and transport operator in Colombia."
	},
	{
		"id": "e-gsk-cost",
		"companyId": "gsk",
		"type": "COST_PROGRAM",
		"date": "2026-07-29",
		"summary": "Launches a plan to save £1.9bn by 2029, with job cuts",
		"documentId": "d-gsk-plan",
		"quote": "Through its new approach, the British pharma giant is hoping to save £1.9bn ($2.5bn) between now and 2029"
	},
	{
		"id": "e-gsk-ceo",
		"companyId": "gsk",
		"type": "EXECUTIVE_CHANGE",
		"date": "2026-07-29",
		"summary": "New CEO Luke Miels sets the direction",
		"documentId": "d-gsk-plan",
		"quote": "as recently appointed CEO, Luke Miels, looks to balance the impact of key drug patent expiries"
	},
	{
		"id": "e-gsk-q2",
		"companyId": "gsk",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-07-28",
		"summary": "Second-quarter revenue of £8.41bn, above forecasts",
		"documentId": "d-gsk-q2",
		"quote": "The company posted quarterly revenue of £8.41 billion, outpacing the Street consensus of £8.24 billion."
	},
	{
		"id": "e-gsk-simplify",
		"companyId": "gsk",
		"type": "RESTRUCTURING",
		"date": "2026-07-28",
		"summary": "CEO says the plan will simplify the organisation",
		"documentId": "d-gsk-q2",
		"quote": "Chief Executive Officer Luke Miels stated the initiative will “simplify the organisation and reallocate capital and resources” to bolster the company’s late-stage development portfolio."
	},
	{
		"id": "e-gsk-rd",
		"companyId": "gsk",
		"type": "STRATEGY",
		"date": "2026-07-28",
		"summary": "Commits £400m to a new R&D centre in the UK",
		"documentId": "d-gsk-q2",
		"quote": "GSK also announced a £400 million investment commitment in the United Kingdom, which encompasses a new research and development facility."
	},
	{
		"id": "e-stla-cost",
		"companyId": "stellantis",
		"type": "COST_PROGRAM",
		"date": "2026-09-25",
		"summary": "Value Creation Program looks for billions in savings",
		"documentId": "d-stla-hiring",
		"quote": "Cost reduction is now a top priority for the company. The Value Creation Program seeks to uncover billions of dollars in potential savings."
	},
	{
		"id": "e-stla-freeze",
		"companyId": "stellantis",
		"type": "HIRING_FREEZE",
		"date": "2026-09-25",
		"summary": "Pauses white-collar hiring at its Auburn Hills headquarters",
		"documentId": "d-stla-hiring",
		"quote": "Stellantis has paused white-collar hiring at its Auburn Hills headquarters after an unprecedented hiring push during the first half of 2026."
	},
	{
		"id": "e-stla-margin",
		"companyId": "stellantis",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-09-25",
		"summary": "Expects a low single-digit operating margin in 2026",
		"documentId": "d-stla-hiring",
		"quote": "the company continues to target modest revenue growth in 2026 and expects an adjusted operating income margin in the low single digits"
	},
	{
		"id": "e-stla-plan",
		"companyId": "stellantis",
		"type": "STRATEGY",
		"date": "2026-09-25",
		"summary": "Savings program is part of the CEO turnaround plan to 2030",
		"documentId": "d-stla-hiring",
		"quote": "this cost-cutting initiative is a crucial part of CEO Antonio Filosa’s turnaround plan"
	},
	{
		"id": "e-stla-hiring",
		"companyId": "stellantis",
		"type": "HIRING_SURGE",
		"date": "2026-06-30",
		"summary": "Planned to hire 2,000 engineers in the first half of 2026",
		"documentId": "d-stla-hiring",
		"quote": "During the first half of 2026, Stellantis announced plans to hire 2,000 engineers"
	},
	{
		"id": "e-bnp-google",
		"companyId": "bnp",
		"type": "VENDOR_CONTRACT",
		"date": "2026-09-24",
		"summary": "Signs a five-year AI and cloud partnership with Google Cloud",
		"documentId": "d-bnp-google",
		"quote": "BNP Paribas and Google Cloud announce five-year partnership for AI and cloud innovation"
	},
	{
		"id": "e-bnp-mistral",
		"companyId": "bnp",
		"type": "VENDOR_CONTRACT",
		"date": "2026-05-01",
		"summary": "Extends its generative AI partnership with Mistral AI for three years",
		"documentId": "d-bnp-google",
		"quote": "In May 2026, BNP Paribas extended its partnership with Mistral AI for three more years, focusing on custom generative AI solutions across multiple divisions of the bank."
	},
	{
		"id": "e-bnp-ibm",
		"companyId": "bnp",
		"type": "VENDOR_CONTRACT",
		"date": "2025-04-01",
		"summary": "Renews IBM Cloud for another 10 years",
		"documentId": "d-bnp-google",
		"quote": "In April 2025, BNP Paribas renewed its collaboration with IBM Cloud for an additional 10 years"
	},
	{
		"id": "e-bnp-usecases",
		"companyId": "bnp",
		"type": "TECH_ADOPTION",
		"date": "2026-09-24",
		"summary": "Targets nearly 1,000 AI use cases by 2026",
		"documentId": "d-bnp-google",
		"quote": "BNP Paribas is targeting nearly 1,000 AI use cases by 2026."
	},
	{
		"id": "e-bnp-dora",
		"companyId": "bnp",
		"type": "COMPLIANCE",
		"date": "2026-09-24",
		"summary": "Falls under DORA rules for digital resilience",
		"documentId": "d-bnp-google",
		"quote": "The European Digital Operational Resilience Act, known as DORA, imposes strict requirements on financial institutions regarding their digital infrastructure, third-party risk management, and incident reporting."
	},
	{
		"id": "e-cap-ai",
		"companyId": "capgemini",
		"type": "STRATEGY",
		"date": "2026-09-21",
		"summary": "Moves from labor-based IT services to software and AI",
		"documentId": "d-cap-ai",
		"quote": "Capgemini is moving away from purely labor-based IT services toward software and artificial intelligence solutions"
	},
	{
		"id": "e-cap-sale",
		"companyId": "capgemini",
		"type": "M_AND_A",
		"date": "2026-09-12",
		"summary": "Agrees to sell Capgemini Government Solutions",
		"documentId": "d-cap-ai",
		"quote": "the planned sale of Capgemini Government Solutions to ITC Federal"
	},
	{
		"id": "e-val-elisa",
		"companyId": "valmet",
		"type": "VENDOR_CONTRACT",
		"date": "2026-04-01",
		"summary": "Picks Elisa as its managed security and SOC provider on a multi-year deal",
		"documentId": "d-val-elisa",
		"quote": "Valmet has selected Elisa as its new provider of cyber security services, a global cyber security operations center (SOC), and related expert services."
	},
	{
		"id": "e-val-guidance",
		"companyId": "valmet",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-04-28",
		"summary": "Guides flat sales and flat or higher EBITA for 2026",
		"documentId": "d-val-q1",
		"quote": "Comparable EBITA in 2026 will remain at the previous year's level or increase in comparison with 2025 (EUR 620 million)"
	},
	{
		"id": "e-bp-breach",
		"companyId": "bpost",
		"type": "SECURITY_INCIDENT",
		"date": "2025-12-03",
		"summary": "Company and consumer data leaked through a supplier platform",
		"documentId": "d-bp-breach",
		"quote": "Due to a ‘cyber incident’ at Belgian postal services provider Bpost, data from an unknown number of companies and consumers has been leaked."
	},
	{
		"id": "e-bp-bbox",
		"companyId": "bpost",
		"type": "STRATEGY",
		"date": "2025-12-02",
		"summary": "Opens bbox boutique parcel hubs in Brussels and Antwerp",
		"documentId": "d-bp-bbox",
		"quote": "bpost is launching the bbox boutique, an innovative hub designed to reinvent urban parcel logistics"
	},
	{
		"id": "e-tk-ransom",
		"companyId": "thyssenkrupp",
		"type": "SECURITY_INCIDENT",
		"date": "2026-07-18",
		"summary": "Ransomware group claims 1 TB stolen from its naval unit",
		"documentId": "d-tk-ransom",
		"quote": "The cyber-incident surfaced on July 18, when a group calling itself “The Gentlemen” listed Thyssenkrupp Marine Systems on its darknet leak site, claiming to have stolen more than a terabyte of data."
	},
	{
		"id": "e-tk-loss",
		"companyId": "thyssenkrupp",
		"type": "FINANCIAL_RESULTS",
		"date": "2026-08-11",
		"summary": "Nucera unit widens its 2026 loss outlook",
		"documentId": "d-tk-nucera",
		"quote": "Thyssenkrupp Nucera now expects an earnings before interest and taxes (EBIT) loss of €105 million to €75 million ($86.52 million) in fiscal 2026"
	},
	{
		"id": "e-tk-freeze",
		"companyId": "thyssenkrupp",
		"type": "HIRING_FREEZE",
		"date": "2026-05-15",
		"summary": "Nucera froze hiring in high-cost countries",
		"documentId": "d-tk-nucera",
		"quote": "In May, Thyssenkrupp Nucera froze hiring in high-cost countries as part of broader savings measures"
	},
	{
		"id": "e-tk-cuts",
		"companyId": "thyssenkrupp",
		"type": "RESTRUCTURING",
		"date": "2025-04-01",
		"summary": "Automotive body unit cuts up to 230 jobs",
		"documentId": "d-tk-ransom",
		"quote": "In spring 2025, Thyssenkrupp Automotive Body Solutions announced it would cut up to 230 jobs"
	},
	{
		"id": "e-lh-cio",
		"companyId": "lufthansa",
		"type": "EXECUTIVE_STATEMENT",
		"date": "2025-02-18",
		"summary": "CIO says AI and data will make operations more productive",
		"documentId": "d-lh-infosys",
		"quote": "Infosys' expertise in digitalization and innovation using AI and data-driven solutions will be instrumental in optimizing our operations"
	},
	{
		"id": "e-dhl-robertson",
		"companyId": "dhl",
		"type": "EXECUTIVE_STATEMENT",
		"date": "2026-09-10",
		"summary": "EVP Accelerated Digitalization backs AI for more efficient logistics processes",
		"documentId": "d-dhl-alibaba",
		"quote": "Through this collaboration, we will explore how advanced AI capabilities can support customers with faster access to information, greater transparency and more efficient logistics processes."
	},
	{
		"id": "e-val-huuskonen",
		"companyId": "valmet",
		"type": "EXECUTIVE_STATEMENT",
		"date": "2026-04-20",
		"summary": "VP IT Transformation explains why Valmet chose an outside provider",
		"documentId": "d-val-elisa",
		"quote": "Elisa has convinced us with its ability to offer modern services that flexibly meet our specific needs and help safeguard our business continuity."
	},
	{
		"id": "e-bp-peeters",
		"companyId": "bpost",
		"type": "EXECUTIVE_STATEMENT",
		"date": "2025-12-02",
		"summary": "CEO speaks for the new parcel hubs",
		"documentId": "d-bp-bbox",
		"quote": "With the bbox boutique, we are setting a new standard for urban parcel and retail services."
	}
];
