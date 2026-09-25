export const apiBase = __API_URL__.replace(/\/$/, '');

export type Submission = {
	id: string;
	author: string;
	title: string;
	body: string | null;
	tags: string[];
	createdAt: string;
};

export type NewSubmission = {
	author: string;
	title: string;
	body: string;
	tags: string[];
};

export type ApiStatus = {
	service: string;
	postgres: string;
	mongo: string;
	checkedAt: string;
};

export class ApiError extends Error {
	readonly status: number | undefined;

	constructor(message: string, status?: number) {
		super(message);
		this.name = 'ApiError';
		this.status = status;
	}
}

async function describeFailure(response: Response): Promise<string> {
	const fallback = `The API answered ${response.status}.`;
	try {
		const body = await response.json();
		return body.detail ?? body.message ?? body.title ?? fallback;
	} catch {
		return fallback;
	}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
	let response: Response;
	try {
		response = await fetch(`${apiBase}${path}`, {
			...init,
			headers: { 'content-type': 'application/json', accept: 'application/json' }
		});
	} catch {
		throw new ApiError(`Couldn't reach the API at ${apiBase}.`);
	}

	if (!response.ok) {
		throw new ApiError(await describeFailure(response), response.status);
	}

	return (await response.json()) as T;
}

export function listSubmissions(): Promise<Submission[]> {
	return request<Submission[]>('/api/submissions');
}

export function createSubmission(input: NewSubmission): Promise<Submission> {
	return request<Submission>('/api/submissions', {
		method: 'POST',
		body: JSON.stringify(input)
	});
}

export function fetchStatus(): Promise<ApiStatus> {
	return request<ApiStatus>('/api/status');
}

export function parseTags(raw: string): string[] {
	return raw
		.split(',')
		.map((tag) => tag.trim().replace(/^#/, ''))
		.filter((tag) => tag.length > 0)
		.slice(0, 8);
}

export function relativeTime(iso: string, now: number): string {
	const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
	if (seconds < 45) return 'just now';
	const minutes = Math.round(seconds / 60);
	if (minutes < 60) return `${minutes}m ago`;
	const hours = Math.round(minutes / 60);
	if (hours < 24) return `${hours}h ago`;
	return `${Math.round(hours / 24)}d ago`;
}

export const freshWindowMs = 5 * 60 * 1000;

export function isFresh(iso: string, now: number): boolean {
	return now - new Date(iso).getTime() < freshWindowMs;
}
