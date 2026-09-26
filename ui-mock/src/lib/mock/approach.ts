import { TODAY, date } from '$lib/format';
import { owns, type Lead, type Move } from './engine';
import type { Person } from './types';

export type Approach = { level: 'COLD' | 'AWARE' | 'WARM'; reason: string };

export const APPROACH_LABEL: Record<Approach['level'], string> = { WARM: 'Warm', AWARE: 'Aware', COLD: 'Cold' };

function namedIn(person: Person, move: Move): boolean {
	const surname = person.name.split(' ').slice(-1)[0];
	return move.event.quote.includes(person.name) || (surname.length > 3 && move.event.quote.includes(surname));
}

function monthsSince(iso: string): number {
	const from = new Date(iso);
	const now = new Date(TODAY);
	return (now.getUTCFullYear() - from.getUTCFullYear()) * 12 + now.getUTCMonth() - from.getUTCMonth();
}

export function approachOf(person: Person, lead: Lead): Approach {
	const owned = lead.moves.filter((move) => owns(person, move.event));
	const events = [...new Map(owned.map((move) => [move.event.id, move.event])).values()];
	const quoted = owned.find((move) => namedIn(person, move));
	const service = lead.icp.serviceLine;
	if (quoted)
		return {
			level: 'WARM',
			reason: `Named in the sentence behind a ${service} signal: "${quoted.event.summary}" (${date(quoted.event.date)}).`
		};
	if (events.length >= 2)
		return {
			level: 'WARM',
			reason: `Named in the sources of ${events.length} ${service} signals, the latest "${events.sort((a, b) => b.date.localeCompare(a.date))[0].summary}".`
		};
	if (person.since && monthsSince(person.since) <= 12)
		return { level: 'AWARE', reason: `New in the role since ${date(person.since)}, as the source states.` };
	if (events.length === 1)
		return { level: 'AWARE', reason: `Named in the source of one ${service} signal: "${events[0].summary}".` };
	return {
		level: 'COLD',
		reason: `Named only as ${person.role} in ${person.sourceName}. No ${service} signal comes from a source that names this person.`
	};
}
