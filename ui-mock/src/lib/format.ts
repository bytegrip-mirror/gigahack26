export const TODAY = '2026-09-26T09:00:00Z';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function date(iso: string | null | undefined): string {
	if (!iso) return 'not stated';
	const value = new Date(iso.length === 10 ? `${iso}T00:00:00Z` : iso);
	return `${value.getUTCDate()} ${MONTHS[value.getUTCMonth()]} ${value.getUTCFullYear()}`;
}

export function month(value: string | null | undefined): string {
	if (!value) return 'none';
	return `${MONTHS[Number(value.slice(5, 7)) - 1]} ${value.slice(0, 4)}`;
}

export function timestamp(iso: string): string {
	return new Date(iso).toISOString().slice(0, 16).replace('T', ' ') + ' UTC';
}

export function ago(iso: string): string {
	const hours = (new Date(TODAY).getTime() - new Date(iso).getTime()) / 3_600_000;
	if (hours < 1) return 'just now';
	if (hours < 24) return `${Math.round(hours)} h ago`;
	const days = hours / 24;
	if (days < 45) return `${Math.round(days)} d ago`;
	return `${Math.round(days / 30)} mo ago`;
}

export function monthsUntil(value: string): number {
	const now = new Date(TODAY);
	const target = new Date(`${value.slice(0, 7)}-01T00:00:00Z`);
	return (target.getUTCFullYear() - now.getUTCFullYear()) * 12 + target.getUTCMonth() - now.getUTCMonth();
}

export function plural(count: number, one: string, many?: string): string {
	return `${count} ${count === 1 ? one : (many ?? `${one}s`)}`;
}

export function host(url: string): string {
	try {
		return new URL(url).hostname.replace(/^www\./, '');
	} catch {
		return url;
	}
}
