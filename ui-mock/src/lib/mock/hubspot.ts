import { csv } from '$lib/export';
import { STATUS_LABEL, outreachLog } from '$lib/outreach-log.svelte';
import { date } from '$lib/format';
import { DIMENSIONS, DIMENSION_LABEL, moveLine, type Lead } from './engine';

export type Property = { label: string; name: string; value: string };

export type ContactRow = { name: string; role: string; url: string; status: string; messages: number; lastAt: string };

export type Note = { title: string; moves: string; body: string; url: string };

export type HubspotRecord = {
	company: Property[];
	contacts: ContactRow[];
	notes: Note[];
};

const COUNTRY: Record<string, string> = {
	DE: 'Germany',
	GB: 'United Kingdom',
	NL: 'Netherlands',
	FR: 'France',
	FI: 'Finland',
	BE: 'Belgium'
};

export function recordFor(lead: Lead): HubspotRecord {
	const { company, icp } = lead;
	return {
		company: [
			{ label: 'Company name', name: 'name', value: company.name },
			{ label: 'Company domain name', name: 'domain', value: company.domain },
			{ label: 'City', name: 'city', value: company.city },
			{ label: 'Country/Region', name: 'country', value: COUNTRY[company.country] ?? company.country },
			{ label: 'Industry', name: 'industry', value: company.industry },
			{ label: 'Number of employees', name: 'numberofemployees', value: company.size },
			{ label: 'Service line', name: 'orange_service_line', value: icp.serviceLine },
			...DIMENSIONS.map((dimension) => ({
				label: `${DIMENSION_LABEL[dimension]} level`,
				name: `orange_${dimension.toLowerCase()}_level`,
				value: lead.levels[dimension].toFixed(2)
			})),
			{ label: 'Signaling on (of 5)', name: 'orange_signaling_on', value: String(lead.lit) },
			{ label: 'Flags', name: 'orange_flags', value: lead.flags.map((flag) => flag.trigger.text).join(' | ') }
		],
		contacts: lead.contacts.map((contact) => {
			const log = outreachLog.get(contact.person.id);
			return {
				name: contact.person.name,
				role: contact.person.role,
				url: contact.person.sourceUrl,
				status: STATUS_LABEL[log.status],
				messages: log.messages,
				lastAt: log.lastAt ?? ''
			};
		}),
		notes: lead.entries.map((entry) => ({
			title: `${date(entry.event.date)}: ${entry.event.summary}`,
			moves: entry.moves.map(moveLine).join('; '),
			body: `"${entry.event.quote}" Source: ${entry.document.publisher}.`,
			url: entry.document.url
		}))
	};
}

function split(name: string): [string, string] {
	const parts = name.split(' ');
	return [parts[0], parts.slice(1).join(' ')];
}

export function hubspotCsv(leads: Lead[]): string {
	const records = leads.map(recordFor);
	const first = records[0];
	if (!first) return '';
	const header = [
		...first.company.map((property) => property.label),
		'Company notes',
		'First name',
		'Last name',
		'Job title',
		'Contact source URL',
		'Outreach status',
		'Messages sent',
		'Last contacted'
	];
	const rows = records.flatMap((record) => {
		const company = record.company.map((property) => property.value);
		const notes = record.notes.map((note) => `${note.title} ${note.moves}. ${note.body} ${note.url}`).join('\n');
		if (record.contacts.length === 0) return [[...company, notes, '', '', '', '', '', '', '']];
		return record.contacts.map((contact) => {
			const [given, family] = split(contact.name);
			return [...company, notes, given, family, contact.role, contact.url, contact.status, contact.messages, contact.lastAt];
		});
	});
	return csv(header, rows);
}
