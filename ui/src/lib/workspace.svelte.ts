import { fetchMeta, fetchStatus, type Meta, type Status } from '$lib/api';
import { session } from '$lib/session.svelte';
import { toasts } from '$lib/toast.svelte';

class Workspace {
	meta = $state<Meta | null>(null);
	status = $state<Status | null>(null);
	counts = $state<Record<string, number>>({});
	loading = $state(true);
	switching = $state(false);
	savedRevision = $state(0);
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
