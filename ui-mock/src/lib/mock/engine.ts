import { TODAY, date } from '$lib/format';
import { companies, REGION_OF } from './companies';
import { documents } from './documents';
import { events } from './events';
import { matches } from './matches';
import { people } from './roster';
import { rules as seedRules } from './rules';
import type { Company, CompanyEvent, Dimension, Document, Icp, Person, ScaleStep, SourceKind, Term, Tier, Trigger, UniversalRules } from './types';

export const DIMENSIONS: Dimension[] = ['NEED', 'BUDGET', 'TIMING', 'ACCESS', 'OPENNESS'];

export const DIMENSION_LABEL: Record<Dimension, string> = {
	NEED: 'Need',
	BUDGET: 'Budget',
	TIMING: 'Timing',
	ACCESS: 'Access',
	OPENNESS: 'Openness'
};

export const DIMENSION_HINT: Record<Dimension, string> = {
	NEED: 'They have the pain we solve',
	BUDGET: 'They can pay',
	TIMING: 'The window is open now',
	ACCESS: 'A named person owns the change',
	OPENNESS: 'They buy from outside'
};

export const TIER_LABEL: Record<Tier, string> = { 3: 'Strong', 2: 'Medium', 1: 'Weak' };

export const STRENGTH: Record<Tier, number> = { 3: 2, 2: 1.5, 1: 1 };

export const AUTHORITY: Record<SourceKind, number> = { website: 1, report: 1, jobs: 0.9, news: 0.8 };

export const AUTHORITY_LABEL: Record<SourceKind, string> = {
	website: 'company website',
	report: 'company report',
	jobs: 'job post',
	news: 'news site'
};

export const HALF_LIFE_MONTHS = 18;

export function litLabel(thresholds: Record<Dimension, number>): string {
	const values = [...new Set(DIMENSIONS.map((dimension) => thresholds[dimension]))];
	return values.length === 1 ? values[0].toFixed(1) : DIMENSIONS.map((dimension) => `${DIMENSION_LABEL[dimension]} ${thresholds[dimension].toFixed(1)}`).join(', ');
}

const DAY = 86_400_000;
const MONTH_DAYS = 365 / 12;
const NOW = new Date(TODAY).getTime();

export type Move = {
	id: string;
	dimension: Dimension;
	trigger: Trigger;
	event: CompanyEvent;
	document: Document;
	why: string;
	term?: Term;
	strength: number;
	halfLife: number;
	authority: number;
	certainty: number;
	base: number;
	ageMonths: number;
	freshness: number;
	points: number;
	before: number;
	after: number;
};

export type Entry = {
	event: CompanyEvent;
	document: Document;
	moves: Move[];
	people: Person[];
};

export type Contact = {
	person: Person;
	moves: Move[];
	weight: number;
	rank: number;
	target: string | null;
};

const BUYING = /\b(CIO|CTO|CDO|CISO|COO|CFO)\b|Chief (Information|Technology|Digital|Operating|Operations|Financial|Finance|Security|Transformation|Data|Process)|Head of (IT|Automation|Transformation|Digital|Process)|IT Transformation|Digitali[sz]ation|Automat/i;
const SENIOR = /\b(CEO|President|EVP|Executive Vice President|Chief|Board)\b/i;

export function roleRank(role: string): number {
	return BUYING.test(role) ? 2 : SENIOR.test(role) ? 1 : 0;
}

export type Lead = {
	id: string;
	company: Company;
	icp: Icp;
	moves: Move[];
	entries: Entry[];
	levels: Record<Dimension, number>;
	thresholds: Record<Dimension, number>;
	scale: ScaleStep[];
	universal: boolean;
	lit: number;
	total: number;
	sources: number;
	flags: Move[];
	contacts: Contact[];
};

const eventById = new Map(events.map((event) => [event.id, event]));
const documentById = new Map(documents.map((document) => [document.id, document]));

function round(value: number): number {
	return Math.round(value * 100) / 100;
}

function timeOf(iso: string): number {
	return new Date(`${iso.slice(0, 10)}T00:00:00Z`).getTime();
}

function months(from: number, to: number): number {
	return Math.round(((to - from) / DAY / MONTH_DAYS) * 10) / 10;
}

export function decay(ageMonths: number, halfLife = HALF_LIFE_MONTHS): number {
	return round(Math.pow(0.5, ageMonths / halfLife));
}

export function halfLifeOf(trigger: Trigger): number {
	const configured = (trigger as Trigger & { halfLifeMonths?: number }).halfLifeMonths;
	return typeof configured === 'number' && configured > 0 ? configured : HALF_LIFE_MONTHS;
}

const EXPAND: Record<string, string> = {
	COO: 'chief operating officer',
	CIO: 'chief information officer',
	CFO: 'chief financial officer',
	CISO: 'chief information security officer',
	CDO: 'chief digital officer',
	CTO: 'chief technology officer'
};

const FILLER = new Set(['head', 'of', 'the', 'and', 'or', 'for']);

function matchesRole(target: string, role: string): boolean {
	const lower = role.toLowerCase();
	return target.split(/\s+or\s+/i).some((part) => {
		const key = part.trim();
		const phrase = key.toLowerCase();
		const abbreviation = EXPAND[key.toUpperCase()] ? key.toUpperCase() : Object.keys(EXPAND).find((short) => EXPAND[short] === phrase);
		if (abbreviation) return new RegExp(`\\b${abbreviation}\\b`).test(role) || lower.includes(EXPAND[abbreviation]);
		const words = phrase.split(/\s+/).filter((word) => word.length > 2 && !FILLER.has(word));
		return words.length > 0 && words.every((word) => lower.includes(word));
	});
}

export function targetRolesOf(icp: Icp): string[] {
	const extended = icp as Icp & { targetRoles?: string[]; personas?: { role: string }[] };
	return extended.targetRoles ?? extended.personas?.map((persona) => persona.role) ?? [];
}

export function eventsOf(companyId: string): CompanyEvent[] {
	return events.filter((event) => event.companyId === companyId).sort((a, b) => b.date.localeCompare(a.date));
}

export function documentsOf(companyId: string): Document[] {
	return documents.filter((document) => document.companyId === companyId);
}

export function documentOf(event: CompanyEvent): Document {
	return documentById.get(event.documentId) as Document;
}

export function peopleOf(companyId: string): Person[] {
	return people.filter((person) => person.companyId === companyId);
}

export function owns(person: Person, event: CompanyEvent): boolean {
	const surname = person.name.split(' ').slice(-1)[0];
	return event.documentId === person.documentId || event.quote.includes(person.name) || (surname.length > 3 && event.quote.includes(surname));
}

export function inScope(icp: Icp, company: Company): boolean {
	const regionOk = icp.regions.length === 0 || icp.regions.includes(REGION_OF[company.country]);
	const industryOk = icp.industries.length === 0 || icp.industries.includes(company.industry);
	const sizeOk = icp.sizes.length === 0 || icp.sizes.includes(company.size);
	return regionOk && industryOk && sizeOk;
}

export function levelAt(moves: Move[], dimension: Dimension, at: number): number {
	return round(
		moves
			.filter((move) => move.dimension === dimension && timeOf(move.event.date) <= at)
			.reduce((sum, move) => sum + move.base * decay(months(timeOf(move.event.date), at), move.halfLife), 0)
	);
}

function movesFor(icp: Icp, company: Company, rules: UniversalRules): Move[] {
	const pool = [...icp.triggers, ...rules.positive, ...rules.negative];
	const raw = matches
		.filter((match) => (match.icpId === icp.id || match.icpId === '*') && match.companyId === company.id)
		.flatMap((match) => {
			const event = eventById.get(match.eventId);
			const trigger = pool.find((candidate) => candidate.id === match.targetId);
			if (!event || !trigger) return [];
			const document = documentOf(event);
			const strength = STRENGTH[trigger.tier];
			const authority = AUTHORITY[document.kind];
			const base = round(trigger.direction * strength * authority * match.confidence);
			const ageMonths = months(timeOf(event.date), NOW);
			const halfLife = halfLifeOf(trigger);
			const freshness = decay(ageMonths, halfLife);
			return [
				{
					id: `${event.id}:${trigger.id}`,
					dimension: trigger.dimension,
					trigger,
					event,
					document,
					why: match.why,
					term: match.term,
					strength,
					halfLife,
					authority,
					certainty: match.confidence,
					base,
					ageMonths,
					freshness,
					points: round(base * freshness),
					before: 0,
					after: 0
				}
			];
		})
		.sort((a, b) => a.event.date.localeCompare(b.event.date));
	raw.forEach((move, index) => {
		const at = timeOf(move.event.date);
		const earlier = raw.slice(0, index).filter((other) => other.dimension === move.dimension);
		move.before = round(earlier.reduce((sum, other) => sum + other.base * decay(months(timeOf(other.event.date), at), other.halfLife), 0));
		move.after = round(move.before + move.base);
	});
	return raw;
}

export function evaluate(icp: Icp, company: Company, rules: UniversalRules = seedRules): Lead {
	const moves = movesFor(icp, company, rules);
	const levels = Object.fromEntries(
		DIMENSIONS.map((dimension) => [dimension, round(moves.filter((move) => move.dimension === dimension).reduce((sum, move) => sum + move.points, 0))])
	) as Record<Dimension, number>;
	const byEvent = new Map<string, Move[]>();
	for (const move of moves) byEvent.set(move.event.id, [...(byEvent.get(move.event.id) ?? []), move]);
	const staff = peopleOf(company.id);
	const targets = targetRolesOf(icp);
	const entries = [...byEvent.values()]
		.map((list) => ({
			event: list[0].event,
			document: list[0].document,
			moves: list,
			people: staff.filter((person) => owns(person, list[0].event))
		}))
		.sort((a, b) => b.event.date.localeCompare(a.event.date));
	const contacts = staff
		.map((person) => {
			const owned = moves.filter((move) => owns(person, move.event));
			const index = targets.findIndex((role) => matchesRole(role, person.role));
			return {
				person,
				moves: owned,
				weight: round(owned.reduce((sum, move) => sum + Math.abs(move.points), 0)),
				rank: roleRank(person.role),
				target: index >= 0 ? targets[index] : null,
				order: index >= 0 ? targets.length - index : 0
			};
		})
		.sort(
			(a, b) =>
				b.order - a.order ||
				(targets.length > 0 ? Number(b.weight > 0) - Number(a.weight > 0) : 0) ||
				b.rank - a.rank ||
				b.weight - a.weight
		)
		.map(({ order, ...contact }) => contact);
	return {
		id: `${company.id}:${icp.id}`,
		company,
		icp,
		moves,
		entries,
		levels,
		thresholds: rules.lit,
		scale: rules.scale,
		universal: moves.some((move) => rules.positive.includes(move.trigger) || rules.negative.includes(move.trigger)),
		lit: DIMENSIONS.filter((dimension) => levels[dimension] >= rules.lit[dimension]).length,
		total: round(DIMENSIONS.reduce((sum, dimension) => sum + levels[dimension], 0)),
		sources: new Set(moves.map((move) => move.document.publisher)).size,
		flags: moves.filter((move) => move.trigger.flag),
		contacts
	};
}

export function leadsFor(list: Icp[], rules: UniversalRules = seedRules): Lead[] {
	return list
		.flatMap((icp) => companies.filter((company) => inScope(icp, company)).map((company) => evaluate(icp, company, rules)))
		.sort((a, b) => b.lit - a.lit || b.total - a.total);
}

export type Point = { age: number; value: number };

export function series(lead: Lead, dimension: Dimension, windowDays: number): Point[] {
	const start = NOW - windowDays * DAY;
	const stamps = new Set<number>();
	for (let t = start; t <= NOW; t += 7 * DAY) stamps.add(t);
	stamps.add(NOW);
	for (const move of lead.moves) {
		const at = timeOf(move.event.date);
		if (move.dimension === dimension && at >= start) {
			stamps.add(at - 1);
			stamps.add(at);
		}
	}
	return [...stamps]
		.sort((a, b) => a - b)
		.map((t) => ({ age: (NOW - t) / DAY, value: levelAt(lead.moves, dimension, t) }));
}

export function signed(value: number, digits = 1): string {
	const text = Math.abs(value).toFixed(digits);
	return value < 0 ? `−${text}` : value > 0 ? `+${text}` : text;
}

export function moveLine(move: Move): string {
	return `${DIMENSION_LABEL[move.dimension]} ${signed(move.base, 2)} (${move.before.toFixed(2)} → ${move.after.toFixed(2)})`;
}

export function summary(lead: Lead): string {
	const { company, icp, levels } = lead;
	const lines = [
		`${company.name}, ${icp.serviceLine}. Signaling on ${lead.lit} of 5.`,
		DIMENSIONS.map((dimension) => `${DIMENSION_LABEL[dimension]} ${levels[dimension].toFixed(2)}`).join(', ') + '.'
	];
	for (const entry of lead.entries.slice(0, 5))
		lines.push(`${date(entry.event.date)}: ${entry.event.summary} (${entry.document.publisher}). ${entry.moves.map(moveLine).join('; ')}.`);
	const top = lead.contacts[0];
	lines.push(top ? `Named contact: ${top.person.name}, ${top.person.role}.` : 'No named contact in the sources.');
	return lines.join(' ');
}
