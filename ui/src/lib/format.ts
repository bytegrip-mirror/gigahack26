export function fit(value: number): string {
	return value.toFixed(2);
}

export type Thresholds = { watch: number; nurture: number; priority: number };

/**
 * The scored quantity in the words a rep would use for it.
 *
 * The number is a log-odds reading put through a sigmoid, so on its own it says
 * nothing a person can act on: 0.70 against a 0.25 base rate is the same fact as
 * 0.52 against 0.45, and neither is legible. The band is not a substitute for
 * this — it folds in the theme cap and the gates, so a company can carry strong
 * evidence and still sit in Nurture because one topic is all it has.
 */
export function fitWord(probability: number, thresholds: Thresholds): string {
	if (probability >= thresholds.priority) return 'Strong';
	if (probability >= thresholds.nurture) return 'Good';
	if (probability >= thresholds.watch) return 'Fair';
	return 'Weak';
}

export function fitTone(probability: number, thresholds: Thresholds): string {
	if (probability >= thresholds.priority) return 'strong';
	if (probability >= thresholds.nurture) return 'good';
	if (probability >= thresholds.watch) return 'fair';
	return 'weak';
}

export function precise(value: number): string {
	return value.toFixed(4);
}

export function signed(value: number, digits = 3): string {
	const rounded = value.toFixed(digits);
	return value > 0 ? `+${rounded}` : rounded;
}

export function age(days: number | null | undefined): string {
	if (days === null || days === undefined) return 'no dated signal';
	if (days < 1) return 'today';
	if (days < 2) return 'yesterday';
	if (days < 45) return `${Math.round(days)} days ago`;
	if (days < 400) return `${Math.round(days / 30)} months ago`;
	return `${(days / 365).toFixed(1)} years ago`;
}

export function ageShort(days: number | null | undefined): string {
	if (days === null || days === undefined) return '—';
	if (days < 1) return 'today';
	if (days < 30) return `${Math.round(days)}d`;
	if (days < 365) return `${Math.round(days / 30)}mo`;
	return `${(days / 365).toFixed(1)}y`;
}

export function date(iso: string | null | undefined): string {
	if (!iso) return '—';
	return new Date(iso).toLocaleDateString(undefined, {
		year: 'numeric',
		month: 'short',
		day: 'numeric'
	});
}

export function timestamp(iso: string | null | undefined): string {
	if (!iso) return '—';
	return new Date(iso).toISOString().replace('T', ' ').replace('.000Z', 'Z');
}

export function tier(value: string): string {
	return value.charAt(0) + value.slice(1).toLowerCase();
}

export function theme(value: string): string {
	if (value === 'data') return 'Data & AI';
	if (value === 'custom') return 'Custom';
	return value.charAt(0).toUpperCase() + value.slice(1);
}

export function sourceKind(value: string): string {
	if (value === 'JOB_POSTING') return 'job posting';
	if (value === 'NEWS') return 'news report';
	if (value === 'NEWSROOM') return 'company newsroom';
	if (value === 'STRATEGY') return 'company strategy page';
	if (value === 'REGISTRY') return 'company register';
	if (value === 'FILING') return 'regulatory filing';
	return value.replace(/_/g, ' ').toLowerCase();
}

export function plural(count: number, one: string, many?: string): string {
	return `${count} ${count === 1 ? one : (many ?? `${one}s`)}`;
}

/**
 * A link only when it is a link.
 *
 * Source URLs come from the crawler and are http(s) by construction, but they are
 * still text that arrived from the open web, and `href="javascript:…"` runs on
 * click. Anything that is not http(s) is rendered as words instead of a link.
 */
export function safeHref(url: string | null | undefined): string | null {
	if (!url) return null;
	return /^https?:\/\//i.test(url.trim()) ? url.trim() : null;
}

export function host(url: string): string {
	try {
		return new URL(url).hostname.replace(/^www\./, '');
	} catch {
		return url;
	}
}

export function shortNumber(value: number): string {
	if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
	if (value >= 10_000) return `${Math.round(value / 1000)}k`;
	if (value >= 1000) return `${(value / 1000).toFixed(1)}k`;
	return String(value);
}

export function duration(ms: number): string {
	if (ms < 1000) return `${Math.round(ms)} ms`;
	if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
	return `${Math.floor(ms / 60_000)}m ${Math.round((ms % 60_000) / 1000)}s`;
}

export function confidence(value: number): string {
	return value.toFixed(2);
}

export function horizon(days: number | null | undefined): string {
	if (days === null || days === undefined) return 'not reachable';
	if (days < 1) return 'crosses today';
	if (days < 60) return `${Math.round(days)} more days`;
	if (days < 720) return `${Math.round(days / 30)} more months`;
	return 'beyond two years';
}
