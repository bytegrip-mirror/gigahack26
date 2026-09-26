import { TODAY } from '$lib/format';

export type OutreachStatus = 'NOT_CONTACTED' | 'CONTACTED' | 'REPLIED' | 'MEETING';

export type OutreachEntry = { status: OutreachStatus; messages: number; lastAt: string | null };

export const STATUS_LABEL: Record<OutreachStatus, string> = {
	NOT_CONTACTED: 'New',
	CONTACTED: 'Contacted',
	REPLIED: 'Replied',
	MEETING: 'Meeting'
};

export const STATUSES = Object.keys(STATUS_LABEL) as OutreachStatus[];

const SEED: Record<string, OutreachEntry> = {
	'p-dhl-robertson': { status: 'REPLIED', messages: 2, lastAt: '2026-09-18' },
	'p-lufthansa-ruckert': { status: 'CONTACTED', messages: 3, lastAt: '2026-09-02' },
	'p-gsk-miels': { status: 'CONTACTED', messages: 1, lastAt: '2026-09-22' },
	'p-valmet-huuskonen': { status: 'MEETING', messages: 2, lastAt: '2026-09-10' },
	'p-bnp-camus': { status: 'CONTACTED', messages: 1, lastAt: '2026-08-28' }
};

const EMPTY: OutreachEntry = { status: 'NOT_CONTACTED', messages: 0, lastAt: null };

class OutreachLog {
	entries = $state<Record<string, OutreachEntry>>(structuredClone(SEED));

	get(personId: string): OutreachEntry {
		return this.entries[personId] ?? EMPTY;
	}

	logMessage(personId: string) {
		const current = this.get(personId);
		this.entries[personId] = {
			status: current.status === 'NOT_CONTACTED' ? 'CONTACTED' : current.status,
			messages: current.messages + 1,
			lastAt: TODAY.slice(0, 10)
		};
	}

	setStatus(personId: string, status: OutreachStatus) {
		this.entries[personId] = { ...this.get(personId), status };
	}
}

export const outreachLog = new OutreachLog();

export function logMessage(personId: string) {
	outreachLog.logMessage(personId);
}

export function setStatus(personId: string, status: OutreachStatus) {
	outreachLog.setStatus(personId, status);
}
