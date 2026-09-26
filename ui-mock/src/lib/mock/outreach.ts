import { DIMENSION_LABEL, owns, type Entry, type Lead, type Move } from './engine';
import type { Approach } from './approach';
import type { Company, Dimension, EventType, Person, Service } from './types';

export type Level = Approach['level'];

export const LEVELS: Level[] = ['COLD', 'AWARE', 'WARM'];

export const LEVEL_HINT: Record<Level, string> = {
	COLD: 'No relationship and no public tie to the topic. Ask for very little.',
	AWARE: 'New in the role or tied to one signal. Offer a short, specific next step.',
	WARM: 'Publicly engaged on the topic. A scoped working session is a fair ask.'
};

export type Cta = { id: string; level: Level; label: string; ask: (service: Service) => string };

export const CTAS: Cta[] = [
	{ id: 'diagnostic', level: 'COLD', label: 'Send the 1-page diagnostic', ask: () => 'Would it help if I sent the one-page version? No call needed.' },
	{ id: 'benchmark', level: 'COLD', label: 'Share a short benchmark', ask: () => 'Can I send a short benchmark of how programs like this are usually staged?' },
	{ id: 'owner', level: 'COLD', label: 'Ask who owns this topic', ask: () => 'If this sits with someone else on your team, who is the right person to send it to?' },
	{ id: 'walkthrough', level: 'AWARE', label: '15-minute walkthrough of what we found', ask: () => 'Would a 15-minute call next week to walk through what we found be useful?' },
	{ id: 'roundtable', level: 'AWARE', label: 'Invite to a peer roundtable', ask: () => 'We run a small roundtable for people with the same problem. Would you like an invite to the next one?' },
	{ id: 'assessment', level: 'AWARE', label: 'Offer a short assessment call', ask: () => 'Would a 30-minute assessment call, focused only on your case, be worth it?' },
	{ id: 'workshop', level: 'WARM', label: 'Scoped discovery workshop', ask: () => 'Would a half-day discovery workshop, scoped to one area you choose, be worth a look?' },
	{ id: 'pilot', level: 'WARM', label: 'Pilot proposal on one process', ask: () => 'Could I put together a pilot proposal for one process you pick?' },
	{ id: 'practice', level: 'WARM', label: 'Meeting with our practice lead', ask: (service) => `Would 30 minutes with our ${service.name} practice lead be useful?` }
];

export function ctasFor(level: Level): Cta[] {
	return CTAS.filter((cta) => cta.level === level);
}

export function ctaById(id: string): Cta {
	return CTAS.find((cta) => cta.id === id) ?? CTAS[0];
}

const AREA: Record<string, string> = { ia: 'automation', cyber: 'security', cloud: 'cloud', data: 'data' };

export const DIAGNOSIS: Record<string, Record<Dimension, string>> = {
	ia: {
		NEED: 'Efficiency programs usually stall in exception handling and hand-offs between systems, not in the happy path, so the savings arrive later than planned.',
		BUDGET: 'When money is set aside for efficiency, it often goes to tools first, and the process work that makes automation pay back gets squeezed.',
		TIMING: 'The first 90 days of a program decide which processes get picked. Teams that pick by volume, not by rework, tend to automate the wrong ones.',
		ACCESS: 'New owners of an operating model usually inherit a backlog of automation ideas with no shared way to rank them by effort and return.',
		OPENNESS: 'Working with several vendors at once often leaves automations that nobody owns end to end, so they break quietly when a system changes.'
	},
	cyber: {
		NEED: 'After a visible security event, the usual gap is detection and response time, not missing tools, and it shows up again in the next audit.',
		BUDGET: 'Security budgets set after a scare tend to fund products first, while the monitoring and response hours to run them stay short.',
		TIMING: 'Rules such as NIS2 and DORA leave less time than they seem to. The evidence trail for the audit is usually the part that runs late.',
		ACCESS: 'A new security or IT owner often finds that asset lists and risk registers are out of date, which makes the first board report hard to stand behind.',
		OPENNESS: 'When several providers share security work, alerts tend to fall between them, and nobody owns the response at 3 a.m.'
	},
	cloud: {
		NEED: 'Cloud moves meant to cut cost often lift old systems as they are, so the run cost moves to a new bill instead of going down.',
		BUDGET: 'Modernisation budgets are usually sized for the move itself, and the clean-up of old systems that follows is the part that runs out of money.',
		TIMING: 'Data center exits and contract end dates tend to force a lift and shift under time pressure, which locks in the cost the move was meant to remove.',
		ACCESS: 'New technology leaders usually inherit a mix of half-moved workloads, and the first hard question is which ones to stop paying for twice.',
		OPENNESS: 'With several providers in the stack, the costs that nobody is tagged to own are often the largest line on the cloud bill.'
	},
	data: {
		NEED: 'AI plans usually stall on the data under them, not the model. The first use case often waits months for data the business trusts.',
		BUDGET: 'Money for AI often goes to pilots that never reach daily work, because the cost to run them in production was not in the plan.',
		TIMING: 'When a data or AI strategy is announced, the first quarter decides the platform, and early shortcuts are hard to undo later.',
		ACCESS: 'A new data or digital owner usually finds several data copies of the same thing, each with its own numbers, and has to pick one to trust.',
		OPENNESS: 'With several vendors building models, the common gap is the path to production: who runs, monitors and retrains them.'
	}
};

export const DIAGNOSIS_BY_EVENT: Record<string, Partial<Record<EventType, string>>> = {
	ia: {
		RESTRUCTURING: 'Programs that cut admin roles usually stall in exception handling and hand-offs between systems, not the happy path, so the remaining team absorbs the gaps.',
		COST_PROGRAM: 'Cost programs that lean on automation often count the easy volume twice and miss the rework loops, where most of the manual effort sits.',
		HIRING_FREEZE: 'When hiring stops, the manual work stays. It moves to people who already have full jobs, and error rates in back-office steps tend to rise first.',
		M_AND_A: 'After an acquisition, the same process usually runs two ways on two systems, and automating either one before they are aligned doubles the work.'
	},
	cyber: {
		SECURITY_INCIDENT: 'After an incident, most teams fix the entry point. The slower gap is usually the time from first alert to a person acting on it.',
		COMPLIANCE: 'For NIS2 and DORA, the controls are rarely the problem. Proving them, with evidence an auditor accepts, is where the time goes.',
		M_AND_A: 'An acquired company usually brings its own network, identities and suppliers, and those links are where attackers look first.'
	},
	cloud: {
		VENDOR_CONTRACT: 'A new platform contract tends to set the target, but the older systems that feed it decide how fast the move can go and what it costs to run.',
		M_AND_A: 'After an acquisition, two infrastructure estates keep running side by side for years unless someone sets a clear exit plan for one of them.',
		TECH_ADOPTION: 'New platforms often land next to the old ones instead of replacing them, so run costs go up before they go down.'
	},
	data: {
		STRATEGY: 'Strategies that name AI as the driver usually hit the same wall first: the data for the first use case is spread across systems nobody has joined.',
		TECH_ADOPTION: 'Rolling out an AI tool is the quick part. Getting it to use company data safely, and measuring whether it helps, takes most of the effort.',
		IN_HOUSE_BUILD: 'In-house AI teams tend to ship fast pilots and then spend most of their time keeping them running, with less left for the next use case.'
	}
};

export const VALUE: Record<string, string> = {
	ia: 'We find the manual, repeated steps in a process and automate them with the client team. I can share a short note on where that work usually hides.',
	cyber: 'We assess security risk and help companies meet NIS2 and DORA. I can share a short checklist of the gaps that usually show up first.',
	cloud: 'We move and modernise systems so they cost less to run. I can share a short note on how to tell which workloads to move, rebuild or retire.',
	data: 'We build data platforms and put AI into daily work. I can share a short note on how to pick a first use case the data can actually support.'
};

function firstSentence(text: string): string {
	const match = text.match(/^[^.]+\./);
	return (match ? match[0] : text).trim();
}

export function diagnosisFor(service: Service, move: Move): string {
	const byEvent = DIAGNOSIS_BY_EVENT[service.id]?.[move.event.type];
	if (byEvent) return byEvent;
	const byDimension = DIAGNOSIS[service.id]?.[move.dimension];
	if (byDimension) return byDimension;
	const outcome = service.outcome ? ` The target is usually this: ${service.outcome.charAt(0).toLowerCase()}${service.outcome.slice(1)}` : '';
	return `Signals like this one usually mean the ${DIMENSION_LABEL[move.dimension].toLowerCase()} side is moving before the delivery plan is clear.${outcome}`;
}

export function valueFor(service: Service): string {
	if (VALUE[service.id]) return VALUE[service.id];
	const base = service.description ? `${firstSentence(service.description)} ` : '';
	return `${base}I can share a short diagnostic of where this usually breaks, before anything is decided.`;
}

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

export function monthYear(iso: string): string {
	return `${MONTH_NAMES[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`;
}

export function trimQuote(quote: string, cap: number): string {
	const clean = quote.trim().replace(/[\s.,;:]+$/, '');
	const words = clean.split(/\s+/);
	return words.length <= cap ? clean : `${words.slice(0, cap).join(' ').replace(/[\s.,;:]+$/, '')}...`;
}

function cite(quote: string): string {
	const text = `"${quote}"`;
	const closed = quote.endsWith('...') ? text : `${text}.`;
	return /^[a-z]/.test(quote) ? `that ${closed}` : `: ${closed}`;
}

function triggerSentence(entry: Entry, cap: number, previous: Entry | null): string {
	const { event, document } = entry;
	const quote = cite(trimQuote(event.quote, cap));
	const says = (subject: string) => `${subject} says${quote.startsWith(':') ? '' : ' '}${quote}`;
	if (previous && previous.document.id === document.id) return says('The same source also');
	const again = previous ? ' also' : '';
	if (!document.publishedAt) return says(`A page on ${document.publisher} that we captured in ${monthYear(document.capturedAt)}${again}`);
	const when = monthYear(document.publishedAt);
	if (document.kind === 'report') return says(`Your ${when} ${event.type === 'FINANCIAL_RESULTS' ? 'results' : 'report'} (${document.publisher})${again}`);
	if (document.kind === 'website') return says(`Your website (${document.publisher}, ${when})${again}`);
	if (document.kind === 'jobs') return says(`A ${when} job post on ${document.publisher}${again}`);
	return `I${again} read in ${document.publisher} (${when})${quote.startsWith(':') ? '' : ' '}${quote}`;
}

export function sourceMonth(entry: Entry): string {
	return monthYear(entry.document.publishedAt ?? entry.document.capturedAt);
}

const TOPIC: Record<EventType, (when: string | null) => string> = {
	COST_PROGRAM: () => 'Your cost program',
	RESTRUCTURING: () => 'The restructuring plan',
	EXECUTIVE_CHANGE: () => 'The leadership change',
	HIRING_SURGE: () => 'The hiring push',
	HIRING_FREEZE: () => 'The hiring freeze',
	VENDOR_CONTRACT: () => 'The new platform contract',
	PARTNERSHIP: () => 'The new partnership',
	TECH_ADOPTION: () => 'The new platform rollout',
	IN_HOUSE_BUILD: () => 'The in-house build',
	FINANCIAL_RESULTS: (when) => (when ? `Your ${when} results` : 'Your latest results'),
	M_AND_A: () => 'The acquisition',
	COMPLIANCE: () => 'The compliance deadline',
	SECURITY_INCIDENT: () => 'The recent incident',
	STRATEGY: (when) => (when ? `Your ${when} strategy update` : 'Your strategy update'),
	EXECUTIVE_STATEMENT: (when) => (when ? `The ${when} statement` : 'The recent statement')
};

export function strongestMove(entry: Entry): Move {
	return [...entry.moves].sort((a, b) => b.points - a.points)[0];
}

export function strength(entry: Entry): number {
	return entry.moves.reduce((sum, move) => sum + Math.max(0, move.points), 0);
}

export type SignalOption = { entry: Entry; owned: boolean };

export function signalOptions(person: Person, lead: Lead): SignalOption[] {
	return lead.entries
		.map((entry) => ({ entry, owned: owns(person, entry.event) }))
		.filter(({ entry, owned }) => owned || entry.moves.some((move) => move.base > 0))
		.sort((a, b) => Number(b.owned) - Number(a.owned) || strength(b.entry) - strength(a.entry));
}

export function defaultSignal(person: Person, lead: Lead): string | null {
	const mine = lead.contacts.find((contact) => contact.person.id === person.id)?.moves ?? [];
	const pick =
		[...mine].sort((a, b) => Math.abs(b.points) - Math.abs(a.points))[0] ??
		lead.moves.filter((move) => move.points > 0).sort((a, b) => b.points - a.points)[0];
	return pick?.event.id ?? null;
}

export type BlockKey = 'subject' | 'trigger' | 'diagnosis' | 'value' | 'cta' | 'signoff';

export const BLOCKS: { key: BlockKey; label: string; hint: string }[] = [
	{ key: 'subject', label: 'Subject', hint: 'Short and about the signal. No hype.' },
	{ key: 'trigger', label: 'Trigger', hint: 'What we noticed, in their own words, with the source and month.' },
	{ key: 'diagnosis', label: 'Diagnosis', hint: 'The problem this usually creates. A general pattern, not a claim about them.' },
	{ key: 'value', label: 'Value', hint: 'What we can give them now. No client names or numbers we cannot back.' },
	{ key: 'cta', label: 'Ask', hint: 'One plain ask.' },
	{ key: 'signoff', label: 'Sign-off', hint: 'Replace the placeholder before sending.' }
];

export type Draft = Record<BlockKey, string>;

export type DraftInput = { person: Person; service: Service; entries: Entry[]; cta: Cta };

export function draft({ service, entries, cta }: DraftInput): Draft {
	const [first, second] = entries;
	if (!first) return { subject: '', trigger: '', diagnosis: '', value: valueFor(service), cta: cta.ask(service), signoff: SIGNOFF };
	const area = AREA[service.id] ?? service.name.toLowerCase();
	const cap = second ? 10 : 16;
	const trigger = [triggerSentence(first, cap, null), second ? triggerSentence(second, cap, first) : ''].filter(Boolean).join(' ');
	return {
		subject: `${TOPIC[first.event.type](first.document.publishedAt ? monthYear(first.document.publishedAt) : null)}: an early ${area} check`,
		trigger,
		diagnosis: diagnosisFor(service, strongestMove(first)),
		value: valueFor(service),
		cta: cta.ask(service),
		signoff: SIGNOFF
	};
}

const SIGNOFF = 'Best,\n[Your name], Orange Systems';

export function greeting(person: Person): string {
	return `Hi ${person.name.split(' ')[0]},`;
}

export function body(person: Person, blocks: Draft): string {
	return [greeting(person), blocks.trigger, blocks.diagnosis, blocks.value, blocks.cta, blocks.signoff]
		.map((part) => part.trim())
		.filter(Boolean)
		.join('\n\n');
}

export function words(text: string): number {
	return text.split(/\s+/).filter(Boolean).length;
}

export function email(person: Person, blocks: Draft): string {
	return `Subject: ${blocks.subject.trim()}\n\n${body(person, blocks)}`;
}

export function mailto(person: Person, blocks: Draft): string {
	return `mailto:?subject=${encodeURIComponent(blocks.subject.trim())}&body=${encodeURIComponent(body(person, blocks))}`;
}

export type HubspotNote = { title: string; lines: { label: string; value: string }[] };

export function hubspotNote(company: Company, person: Person, blocks: Draft, entries: Entry[]): HubspotNote {
	return {
		title: `Email activity on ${company.name}, associated with ${person.name}`,
		lines: [
			{ label: 'Subject', value: blocks.subject.trim() },
			...entries.map((entry, index) => ({
				label: entries.length > 1 ? `Signal ${index + 1}` : 'Signal',
				value: `${entry.event.summary} (${DIMENSION_LABEL[strongestMove(entry).dimension]}, ${sourceMonth(entry)}). Source: ${entry.document.url}`
			}))
		]
	};
}
