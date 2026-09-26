import { withHalfLife, type TriggerSeed } from './rules';
import type { Icp, Rubric } from './types';

type IcpSeed = Omit<Icp, 'triggers' | 'targetRoles'> & { triggers: TriggerSeed[] };

function build(list: IcpSeed[]): Icp[] {
	return list.map((icp) => ({
		...icp,
		triggers: withHalfLife(icp.triggers),
		targetRoles: (icp.personas ?? []).map((persona) => persona.role)
	}));
}

function rubric(counts: string, doesNotCount: string, countsExample: string, failsExample: string): Rubric {
	return { counts, doesNotCount, countsExample, failsExample };
}

export const icps: Icp[] = build([
	{
		id: 'ia',
		name: 'Intelligent Automation, large enterprises in Europe',
		serviceLine: 'Intelligent Automation',
		serviceId: 'ia',
		regions: ['DACH', 'Benelux', 'France', 'UK & Ireland', 'Nordics'],
		industries: ['Airlines', 'Automotive', 'Banking', 'IT Services', 'Logistics', 'Pharmaceuticals'],
		sizes: ['5001-10000', '10001+'],
		complexity: 'Many manual, repeated processes across several countries, such as shared services, finance operations, customer service and order handling.',
		personas: [
			{ id: 'p-ia-coo', role: 'Chief Operating Officer', why: 'Owns the operating cost and efficiency targets that automation moves.' },
			{ id: 'p-ia-cio', role: 'Chief Information Officer', why: 'Owns the systems the automations run on and the IT budget.' },
			{ id: 'p-ia-cdo', role: 'Head of Digital Transformation', why: 'Runs the transformation program and picks the use cases.' },
			{ id: 'p-ia-pex', role: 'Head of Process Excellence', why: 'Owns the backlog of processes to automate and measures the result.' },
			{ id: 'p-ia-auto', role: 'Head of Automation', why: 'Runs the automation team or center of excellence and picks the tools.' },
			{ id: 'p-ia-cfo', role: 'Chief Financial Officer', why: 'Signs off savings targets and often owns finance shared services.' }
		],
		triggers: [
			{
				id: 't-ia-cost',
				text: 'Announces a cost or efficiency program',
				dimension: 'NEED',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['COST_PROGRAM', 'STRATEGY'],
				fade: 'PROGRAM',
				rubric: rubric(
					'A named program with a savings target, a scope or a deadline, run by the company itself.',
					'General talk about efficiency, or a program at a supplier or a customer.',
					'The group will cut operating costs by 1.2 billion euros by 2028 through its efficiency program.',
					'Efficiency remains important to us in a changing market.'
				)
			},
			{
				id: 't-ia-admin',
				text: 'Plans to cut or merge back-office and admin work',
				dimension: 'NEED',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['RESTRUCTURING'],
				fade: 'PROGRAM',
				rubric: rubric(
					'A plan to reduce administrative roles or merge back-office functions, with a number or a date.',
					'Headcount facts with no plan, or cuts in factories or the field.',
					'We plan to reduce around 3,000 administrative roles by 2030 through digitalization and process consolidation.',
					'The company employs about 3,000 people in administration.'
				)
			},
			{
				id: 't-ia-usecase',
				text: 'Starts to automate a named process, such as quotes, invoices or customer service',
				dimension: 'NEED',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['TECH_ADOPTION', 'PARTNERSHIP'],
				fade: 'PROGRAM',
				rubric: rubric(
					'An AI, RPA, agentic AI or process mining project on a named business process.',
					'A product feature for customers, or a pilot with no named process.',
					'The company now uses AI agents to process incoming requests for quotes in its freight business.',
					'We are exploring what AI can do for us.'
				)
			},
			{
				id: 't-ia-shared',
				text: 'Consolidates shared services or moves work into a shared service center',
				dimension: 'NEED',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['RESTRUCTURING'],
				fade: 'PROGRAM',
				rubric: rubric(
					'Finance, HR, procurement or IT work moves into one shared service center or a smaller set of them.',
					'A new office opening with no process scope.',
					'Finance and HR processes from twelve countries will move to the new shared service center in Porto next year.',
					'The company opened a new office in Porto.'
				)
			},
			{
				id: 't-ia-tools',
				text: 'Already uses RPA, process mining or workflow tools',
				dimension: 'NEED',
				direction: 1,
				tier: 1,
				flag: false,
				eventTypes: ['TECH_ADOPTION'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company names an automation or process mining tool it runs today.',
					'A vendor case study with no date, or a tool that a subsidiary sold off.',
					'Our finance team runs more than 200 software robots on the RPA platform we adopted in 2023.',
					'Robotic process automation is a growing market.'
				)
			},
			{
				id: 't-ia-done',
				text: 'Just finished an automation rollout',
				dimension: 'NEED',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['TECH_ADOPTION'],
				fade: 'PROGRAM',
				rubric: rubric(
					'The company says an automation program is complete and names the scope.',
					'A milestone in a program that still runs.',
					'We completed the automation of all accounts payable processes across Europe in June.',
					'The first phase of our automation program went live.'
				)
			},
			{
				id: 't-ia-profit',
				text: 'Reports rising profit or raises its forecast',
				dimension: 'BUDGET',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['FINANCIAL_RESULTS'],
				fade: 'EVENT',
				rubric: rubric(
					'Reported profit or margin went up, or the company raised its guidance.',
					'Revenue growth alone, or an analyst forecast.',
					'Operating profit rose 18 percent in the second quarter and the group raised its full-year outlook.',
					'Analysts expect the company to grow next year.'
				)
			},
			{
				id: 't-ia-invest',
				text: 'Commits money to a transformation, IT or hiring program',
				dimension: 'BUDGET',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['STRATEGY', 'HIRING_SURGE', 'VENDOR_CONTRACT'],
				fade: 'PROGRAM',
				rubric: rubric(
					'An amount of money or a number of hires tied to a transformation, IT or automation program.',
					'An investment in planes, plants or real estate with no IT part.',
					'The company will invest 400 million euros in digitalization and automation over the next three years.',
					'We continue to invest in our future.'
				)
			},
			{
				id: 't-ia-squeeze',
				text: 'Reports losses or thin margins, or freezes hiring',
				dimension: 'BUDGET',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['FINANCIAL_RESULTS', 'HIRING_FREEZE'],
				fade: 'EVENT',
				rubric: rubric(
					'A reported loss, a margin near zero, or a hiring freeze for office staff.',
					'A one-off charge the company says will not repeat.',
					'The group posted a net loss for the half year and paused all white-collar hiring.',
					'Results included a one-off tax charge.'
				)
			},
			{
				id: 't-ia-exec',
				text: 'Appoints a new CEO, CIO, COO or head of transformation',
				dimension: 'TIMING',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['EXECUTIVE_CHANGE'],
				fade: 'EVENT',
				rubric: rubric(
					'A named person starts in one of these roles in the last 12 months.',
					'A reshuffle below board level, or a rumour.',
					'The board appointed a new Chief Operating Officer, who starts on 1 March.',
					'The company is said to be looking for a new COO.'
				)
			},
			{
				id: 't-ia-reorg',
				text: 'Starts a reorganisation or turnaround this year',
				dimension: 'TIMING',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['RESTRUCTURING', 'STRATEGY'],
				fade: 'PROGRAM',
				rubric: rubric(
					'A reorganisation or turnaround plan that starts or runs in the current year.',
					'A plan that ended, or one set for a later year with no action now.',
					'IT and digital units will be merged into one organisation during 2026.',
					'The 2019 reorganisation is complete.'
				)
			},
			{
				id: 't-ia-ai',
				text: 'Names AI or automation as a current priority',
				dimension: 'TIMING',
				direction: 1,
				tier: 1,
				flag: false,
				eventTypes: ['TECH_ADOPTION', 'STRATEGY'],
				fade: 'PROGRAM',
				rubric: rubric(
					'The company puts AI, automation or process efficiency in its current strategy or plan.',
					'A general statement about the industry.',
					'AI and automation are one of the four pillars of our strategy to 2030.',
					'AI will change the logistics industry.'
				)
			},
			{
				id: 't-ia-hiring',
				text: 'Hires for automation roles, such as RPA developers, process excellence or AI engineers',
				dimension: 'TIMING',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['HIRING_SURGE'],
				fade: 'EVENT',
				rubric: rubric(
					'Open roles for RPA, process mining, process excellence, business analysis or AI engineering.',
					'One generic IT role, or roles at a recruiter with no company named.',
					'We are hiring six RPA developers and two process mining analysts for our finance transformation team.',
					'Join a fast-growing team.'
				)
			},
			{
				id: 't-ia-lock',
				text: 'Just signed a multi-year AI or automation deal with another provider',
				dimension: 'TIMING',
				direction: -1,
				tier: 3,
				flag: false,
				eventTypes: ['VENDOR_CONTRACT'],
				fade: 'PROGRAM',
				rubric: rubric(
					'A contract for automation or AI services with a term. It counts until the end date. After that, the renewal is a window.',
					'A software licence with no services part.',
					'The group signed a five-year contract with a global IT provider to run its intelligent automation program.',
					'The company renewed its office software licences.'
				)
			},
			{
				id: 't-ia-newlead',
				text: 'Names a new COO, CIO, CDO or head of automation',
				dimension: 'ACCESS',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['EXECUTIVE_CHANGE'],
				fade: 'EVENT',
				rubric: rubric(
					'A named person in a role that buys automation, new in the last 12 months.',
					'A role with no name, or an interim for less than three months.',
					'Maria Example joins as Head of Process Excellence and reports to the COO.',
					'The company plans to hire a head of automation.'
				)
			},
			{
				id: 't-ia-owner',
				text: 'A named executive speaks for the change in public',
				dimension: 'ACCESS',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['EXECUTIVE_STATEMENT', 'EXECUTIVE_CHANGE', 'RESTRUCTURING', 'STRATEGY'],
				fade: 'PROGRAM',
				rubric: rubric(
					'A named person is quoted as the owner of the efficiency, AI or automation work.',
					'A quote from a spokesperson with no role, or from a vendor.',
					'"We will automate every process that we can," said the Chief Digital Officer.',
					'A company spokesperson declined to comment.'
				)
			},
			{
				id: 't-ia-rolecut',
				text: 'The role that owns the change is cut',
				dimension: 'ACCESS',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['RESTRUCTURING'],
				fade: 'EVENT',
				rubric: rubric(
					'The role or unit that owns transformation or automation is removed.',
					'A person leaves and the role stays.',
					'The digital transformation office will be closed and its tasks moved to the business units.',
					'The head of digital left the company.'
				)
			},
			{
				id: 't-ia-partners',
				text: 'Partners with outside providers for digital work',
				dimension: 'OPENNESS',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['PARTNERSHIP', 'VENDOR_CONTRACT'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company says it buys digital, IT or AI work from outside providers.',
					'A sales partnership where the company is the seller.',
					'We combine internal development with third-party AI solutions to speed up adoption.',
					'The company resells software from a partner.'
				)
			},
			{
				id: 't-ia-comp',
				text: 'Sells AI or automation services, so it competes with Orange',
				dimension: 'OPENNESS',
				direction: -1,
				tier: 3,
				flag: true,
				eventTypes: ['STRATEGY'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company sells automation, AI or IT consulting services to other companies.',
					'The company uses AI in its own products.',
					'We help clients automate their business processes with AI and RPA.',
					'Our app uses AI to recommend flights.'
				)
			},
			{
				id: 't-ia-inhouse',
				text: 'Says it builds all automation in-house',
				dimension: 'OPENNESS',
				direction: -1,
				tier: 3,
				flag: true,
				eventTypes: ['IN_HOUSE_BUILD'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company states that it builds and runs automation only with its own staff.',
					'It builds some tools and buys others.',
					'All automation is designed and built by our internal automation center, with no outside providers.',
					'Our team built a new internal tool.'
				)
			},
			{
				id: 't-ia-team',
				text: 'Has a strong internal IT or digital team',
				dimension: 'OPENNESS',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['IN_HOUSE_BUILD', 'RESTRUCTURING'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'A large internal IT, digital or automation team, with a number, or a plan to grow internal expertise.',
					'A small team that coordinates outside providers.',
					'Around 8,000 IT specialists work in the group, and digital units will be merged to grow internal expertise.',
					'A small IT team manages our providers.'
				)
			},
			{
				id: 't-ia-incumbent',
				text: 'Already works with a large automation provider',
				dimension: 'OPENNESS',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['VENDOR_CONTRACT', 'PARTNERSHIP'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'A current provider runs automation or AI services for the company.',
					'A provider for a different service, such as hosting.',
					'A global consultancy has run our RPA center of excellence since 2022.',
					'Our data centers are hosted by an outside provider.'
				)
			}
		]
	},
	{
		id: 'cyber',
		name: 'Cybersecurity services, industrial Europe',
		serviceLine: 'Cybersecurity services',
		serviceId: 'cyber',
		regions: ['DACH', 'Benelux', 'Nordics'],
		industries: ['Industrial Conglomerate', 'Industrial Machinery', 'Postal'],
		sizes: ['1001-5000', '5001-10000', '10001+'],
		complexity: 'Many sites and plants, operational technology next to office IT, and a supply chain that attackers can use.',
		personas: [
			{ id: 'p-cy-ciso', role: 'Chief Information Security Officer', why: 'Owns security risk and the budget for security services.' },
			{ id: 'p-cy-cio', role: 'Chief Information Officer', why: 'Owns the systems that must be protected and signs larger contracts.' },
			{ id: 'p-cy-itops', role: 'Head of IT Operations', why: 'Runs the day-to-day monitoring that a managed SOC replaces.' },
			{ id: 'p-cy-risk', role: 'Chief Risk Officer', why: 'Answers to regulators for NIS2 and DORA deadlines.' }
		],
		triggers: [
			{
				id: 't-cy-incident',
				text: 'Suffers a breach, a ransomware attack or a data leak',
				dimension: 'NEED',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['SECURITY_INCIDENT'],
				fade: 'EVENT',
				rubric: rubric(
					'The company confirms an attack, a breach or a data leak on its own systems.',
					'An attack on a supplier or on the industry in general.',
					'The company confirmed a ransomware attack that stopped production at two plants for four days.',
					'Ransomware attacks on manufacturers are rising.'
				)
			},
			{
				id: 't-cy-rules',
				text: 'Falls under a new security rule, such as NIS2 or DORA',
				dimension: 'NEED',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['COMPLIANCE'],
				fade: 'PROGRAM',
				rubric: rubric(
					'The company says a named security rule applies to it, or names a compliance program for it.',
					'An article that explains the rule with no link to the company.',
					'As an essential entity under NIS2, the group started a program to meet the new reporting rules.',
					'NIS2 affects thousands of companies in Europe.'
				)
			},
			{
				id: 't-cy-deadline',
				text: 'Faces a NIS2 or DORA deadline in the next 12 months',
				dimension: 'TIMING',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['COMPLIANCE'],
				fade: 'EVENT',
				rubric: rubric(
					'A dated compliance deadline for the company, or an audit date, in the next 12 months.',
					'A deadline that already passed with no finding.',
					'The group must pass its first NIS2 audit by June next year.',
					'The rule came into force in 2024.'
				)
			},
			{
				id: 't-cy-buys',
				text: 'Buys managed security services',
				dimension: 'NEED',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['VENDOR_CONTRACT'],
				fade: 'PROGRAM',
				rubric: rubric(
					'The company buys security testing, monitoring or response from an outside provider.',
					'A security product licence with no service.',
					'The company appointed an outside provider for penetration testing across all plants.',
					'The company bought new firewalls.'
				)
			},
			{
				id: 't-cy-profit',
				text: 'Reports stable or rising profit',
				dimension: 'BUDGET',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['FINANCIAL_RESULTS'],
				fade: 'EVENT',
				rubric: rubric('Reported profit or margin held or went up.', 'Revenue growth alone, or an analyst forecast.', 'Adjusted operating profit rose 6 percent in the first half.', 'Analysts see upside for the stock.')
			},
			{
				id: 't-cy-invest',
				text: 'Invests in new sites, systems or IT',
				dimension: 'BUDGET',
				direction: 1,
				tier: 1,
				flag: false,
				eventTypes: ['STRATEGY'],
				fade: 'PROGRAM',
				rubric: rubric(
					'An amount committed to new plants, new systems or IT, which widens what must be secured.',
					'A plan with no amount and no date.',
					'The company will spend 250 million euros on two new connected plants by 2027.',
					'We keep investing in growth.'
				)
			},
			{
				id: 't-cy-squeeze',
				text: 'Reports losses or freezes hiring',
				dimension: 'BUDGET',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['FINANCIAL_RESULTS', 'HIRING_FREEZE'],
				fade: 'EVENT',
				rubric: rubric('A reported loss, or a hiring freeze for office staff.', 'A one-off charge the company says will not repeat.', 'The group reported a net loss and froze hiring outside production.', 'Results included a one-off write-down.')
			},
			{
				id: 't-cy-recent',
				text: 'Had an incident in the last 12 months',
				dimension: 'TIMING',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['SECURITY_INCIDENT'],
				fade: 'EVENT',
				rubric: rubric(
					'A confirmed incident dated in the last 12 months. The board tends to act while the memory is fresh.',
					'An incident from several years ago.',
					'Last month the company disclosed unauthorized access to its customer portal.',
					'The company was hacked in 2017.'
				)
			},
			{
				id: 't-cy-hiring',
				text: 'Hires security staff, such as SOC analysts or security architects',
				dimension: 'TIMING',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['HIRING_SURGE'],
				fade: 'EVENT',
				rubric: rubric(
					'Several open security roles at the company at the same time.',
					'One security role that is a replacement.',
					'We are building a security team and hiring five SOC analysts and an OT security architect.',
					'We are looking for an IT support technician.'
				)
			},
			{
				id: 't-cy-lock',
				text: 'Just signed a multi-year SOC or managed security contract',
				dimension: 'TIMING',
				direction: -1,
				tier: 3,
				flag: false,
				eventTypes: ['VENDOR_CONTRACT'],
				fade: 'PROGRAM',
				rubric: rubric(
					'An outsourced SOC or managed security contract with a term. It counts until the end date. The months before renewal are a window.',
					'A short project, such as one penetration test.',
					'The group signed a four-year contract with a managed security provider to run its 24/7 SOC.',
					'The company ran a one-week penetration test.'
				)
			},
			{
				id: 't-cy-newlead',
				text: 'Names a new CISO or CIO',
				dimension: 'ACCESS',
				direction: 1,
				tier: 3,
				flag: false,
				eventTypes: ['EXECUTIVE_CHANGE'],
				fade: 'EVENT',
				rubric: rubric(
					'A named person starts as CISO or CIO in the last 12 months. New leaders review providers in their first year.',
					'A role with no name.',
					'The company appointed Alex Example as Chief Information Security Officer from 1 September.',
					'The company is recruiting a CISO.'
				)
			},
			{
				id: 't-cy-owner',
				text: 'A named IT or business leader speaks for the change in public',
				dimension: 'ACCESS',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['EXECUTIVE_STATEMENT'],
				fade: 'PROGRAM',
				rubric: rubric(
					'A named leader is quoted on security, resilience or compliance at the company.',
					'A quote from a vendor about the company.',
					'"Security is now a board topic for us," said the CIO at the results call.',
					'A vendor said the company is a valued customer.'
				)
			},
			{
				id: 't-cy-rolegap',
				text: 'Has no security leader, or the role is vacant',
				dimension: 'ACCESS',
				direction: -1,
				tier: 1,
				flag: false,
				eventTypes: ['EXECUTIVE_CHANGE'],
				fade: 'EVENT',
				rubric: rubric(
					'The company says the security lead role is vacant or removed.',
					'A leader who moves to a new title with the same scope.',
					'The CISO left in May and the role remains open.',
					'The CISO now reports to the CFO.'
				)
			},
			{
				id: 't-cy-outside',
				text: 'Buys security or IT services from outside providers',
				dimension: 'OPENNESS',
				direction: 1,
				tier: 2,
				flag: false,
				eventTypes: ['VENDOR_CONTRACT', 'PARTNERSHIP'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company says it works with outside providers for security or IT operations.',
					'The company sells security to others.',
					'We rely on specialist partners for security monitoring outside office hours.',
					'We offer security services to our customers.'
				)
			},
			{
				id: 't-cy-comp',
				text: 'Sells security services itself',
				dimension: 'OPENNESS',
				direction: -1,
				tier: 3,
				flag: true,
				eventTypes: ['STRATEGY'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company sells security services to other companies, so it competes with Orange.',
					'The company sells products with security features.',
					'Our managed security business protects more than 500 customers.',
					'Our machines ship with encrypted controls.'
				)
			},
			{
				id: 't-cy-soc',
				text: 'Runs its own security operations center',
				dimension: 'OPENNESS',
				direction: -1,
				tier: 2,
				flag: false,
				eventTypes: ['IN_HOUSE_BUILD'],
				fade: 'STRUCTURAL',
				rubric: rubric(
					'The company runs a SOC with its own staff.',
					'A SOC run by a provider on the company sites.',
					'Our in-house security operations center monitors all plants around the clock.',
					'A provider runs the SOC from our Munich site.'
				)
			}
		]
	}
]);
