import {
	fetchIcpProfiles,
	fetchMeta,
	fetchStatus,
	type IcpProfile,
	type Meta,
	type Status
} from '$lib/api';
import { session } from '$lib/session.svelte';
import { toasts } from '$lib/toast.svelte';

class Workspace {
	meta = $state<Meta | null>(null);
	status = $state<Status | null>(null);
	counts = $state<Record<string, number>>({});
	loading = $state(true);
	switching = $state(false);
	savedRevision = $state(0);
	/**
	 * The profiles that are switched on, whole rather than by name.
	 *
	 * The board labels every row with the profile it falls into and that label
	 * carries the profile's colour, so the name alone is not enough to draw one.
	 * Holding the record here also means a profile edited anywhere is one reload
	 * away from every screen that shows it.
	 */
	profiles = $state<IcpProfile[]>([]);
	#token = 0;

	/**
	 * Bumped whenever the set of saved companies changes.
	 *
	 * The rail's count and the board's Saved filter read the same server state
	 * and were refreshed by different code paths, so starring a row moved one
	 * and not the other. One counter both watch is cheaper than making every
	 * caller remember to refresh both.
	 */
	savedChanged() {
		this.savedRevision += 1;
	}

	get profileNames(): string[] {
		return this.profiles.map((profile) => profile.name);
	}

	async loadProfiles() {
		try {
			this.profiles = (await fetchIcpProfiles()).filter((profile) => profile.active);
		} catch {
			this.profiles = [];
		}
	}

	get lines(): string[] {
		return this.meta?.serviceLines ?? [];
	}

	get regions() {
		return this.meta?.regions ?? [];
	}

	async load(line: string | null = session.serviceLine) {
		const token = ++this.#token;
		const [meta, status] = await Promise.all([
			fetchMeta(line || undefined),
			this.status ? Promise.resolve(this.status) : fetchStatus()
		]);
		if (token !== this.#token) return;
		this.meta = meta;
		this.status = status;
		this.loading = false;
		void this.loadProfiles();
		if (!session.serviceLine) session.set(meta.defaultServiceLine);
		if (meta.degraded) {
			toasts.push(
				'The language model is unreachable, so re-scores run on the local rubric engine. They stay instant and offline.',
				'warn'
			);
		}
		void this.loadCounts();
	}

	async loadCounts() {
		const lines = this.meta?.serviceLines ?? [];
		const pairs = await Promise.all(
			lines.map((line) =>
				fetchMeta(line)
					.then((meta) => [line, meta.bands.PRIORITY ?? 0] as const)
					.catch(() => [line, 0] as const)
			)
		);
		this.counts = Object.fromEntries(pairs);
	}

	async select(line: string) {
		if (line === session.serviceLine) return;
		session.set(line);
		this.switching = true;
		try {
			await this.load(line);
		} catch {
			toasts.push('That service line could not be loaded.', 'error');
		} finally {
			this.switching = false;
		}
	}

	async reload() {
		await this.load(session.serviceLine);
	}
}

export const workspace = new Workspace();
