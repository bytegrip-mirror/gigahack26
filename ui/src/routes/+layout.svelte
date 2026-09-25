<script lang="ts">
	import '../app.css';
	import '@fontsource-variable/space-grotesk';
	import '@fontsource-variable/archivo';

	import { untrack } from 'svelte';
	import { page } from '$app/state';
	import { fetchMeta, fetchStatus, type Meta, type Status } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { shortNumber } from '$lib/format';
	import Logo from '$lib/components/Logo.svelte';

	let { children } = $props();

	let meta = $state<Meta | null>(null);
	let status = $state<Status | null>(null);
	let loading = $state(true);
	let counts = $state<Record<string, number>>({});
	let switching = $state(false);
	let now = $state(Date.now());

	const healthy = $derived(status !== null && status.postgres === 'up' && status.mongo === 'up');

	$effect(() => {
		const timer = setInterval(() => (now = Date.now()), 1000);
		return () => clearInterval(timer);
	});

	const countdown = $derived.by(() => {
		const target = meta?.nextRefreshAt ? new Date(meta.nextRefreshAt).getTime() : null;
		if (target === null) return '—';
		const seconds = Math.max(0, Math.round((target - now) / 1000));
		if (seconds < 60) return `${seconds}s`;
		const minutes = Math.round(seconds / 60);
		return minutes < 60 ? `${minutes}m` : `${Math.round(minutes / 60)}h`;
	});
	const lines = $derived(meta?.serviceLines ?? []);

	$effect(() => {
		let cancelled = false;
		const stored = untrack(() => session.serviceLine);
		Promise.all([fetchMeta(stored || undefined), fetchStatus()])
			.then(([metaResult, statusResult]) => {
				if (cancelled) return;
				meta = metaResult;
				status = statusResult;
				loading = false;
				if (!stored) session.set(metaResult.defaultServiceLine);
				if (metaResult.degraded) {
					toasts.push(
						'The language model is unreachable, so re-scores run on the local rubric engine. They stay instant and offline.',
						'warn'
					);
				}
			})
			.catch(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		const available = lines;
		if (available.length === 0) return;
		let cancelled = false;
		Promise.all(available.map((line) => fetchMeta(line).then((m) => [line, m.bands.PRIORITY ?? 0] as const)))
			.then((pairs) => {
				if (cancelled) return;
				counts = Object.fromEntries(pairs);
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	});

	function chooseLine(line: string) {
		if (line === session.serviceLine) return;
		session.set(line);
		switching = true;
		fetchMeta(line)
			.then((result) => (meta = result))
			.catch(() => toasts.push('That service line could not be loaded.', 'error'))
			.finally(() => (switching = false));
	}
</script>

<div class="app">
	<aside class="rail">
		<a class="brand" href="/">
			<Logo size={19} />
			<span class="wordmark">
				<strong class="dsp">Signal Desk</strong>
				<span class="label">Orange Systems</span>
			</span>
		</a>

		<nav aria-label="Views">
			<a href="/" aria-current={page.url.pathname === '/' ? 'page' : undefined}>Board</a>
			<a href="/studio" aria-current={page.url.pathname.startsWith('/studio') ? 'page' : undefined}>
				Signal Studio
			</a>
			<a href="/pipeline" aria-current={page.url.pathname.startsWith('/pipeline') ? 'page' : undefined}>
				Pipeline
			</a>
			<a href="/settings" aria-current={page.url.pathname.startsWith('/settings') ? 'page' : undefined}>
				Settings
			</a>
		</nav>

		<div class="block">
			<p class="label">Service lines</p>
			<ul>
				{#each lines as line (line)}
					<li>
						<button
							class="line"
							aria-current={line === session.serviceLine ? 'true' : undefined}
							disabled={switching}
							onclick={() => chooseLine(line)}
						>
							<span class="line-name">{line}</span>
							{#if switching && line === session.serviceLine}
								<span class="spinner"></span>
							{:else if counts[line] !== undefined}
								<span class="num line-count">{counts[line]}</span>
							{/if}
						</button>
					</li>
				{/each}
			</ul>
		</div>

		{#if status}
			<div class="block">
				<p class="label">Corpus</p>
				<dl class="facts">
					<dt>Companies</dt><dd class="num">{shortNumber(status.companies)}</dd>
					<dt>Documents</dt><dd class="num">{shortNumber(status.evidence)}</dd>
					<dt>Signals</dt><dd class="num">{shortNumber(status.signals)}</dd>
				</dl>
			</div>
		{/if}

		<div class="rail-foot">
			{#if meta?.nextRefreshAt}
				<p class="refresh">
					<span class="dot" class:ok={healthy} class:bad={!healthy}></span>
					next refresh <span class="num">{countdown}</span>
				</p>
			{:else if loading}
				<p class="refresh"><span class="spinner"></span> connecting</p>
			{/if}
		</div>
	</aside>

	<main>
		{@render children()}
	</main>
</div>

<div class="toasts" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class="toast" data-tone={toast.tone}>
			<span>{toast.message}</span>
			<button class="btn btn-quiet btn-sm" onclick={() => toasts.dismiss(toast.id)}>Dismiss</button>
		</div>
	{/each}
</div>

<style>
	.app {
		display: grid;
		grid-template-columns: var(--rail) minmax(0, 1fr);
		height: 100vh;
	}

	.rail {
		border-right: 1px solid var(--rule);
		background: var(--paper);
		display: flex;
		flex-direction: column;
		overflow-y: auto;
		scrollbar-width: none;
	}

	.rail::-webkit-scrollbar {
		display: none;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: 9px;
		padding: 14px 14px 15px;
		color: var(--ink);
		border-bottom: 1px solid var(--rule);
	}

	.brand:hover {
		text-decoration: none;
	}

	.wordmark {
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
	}

	.wordmark strong {
		font-size: 17px;
		line-height: 1.1;
	}

	.wordmark .label {
		font-size: 9.5px;
	}

	nav {
		display: flex;
		flex-direction: column;
		padding: 6px 0;
		border-bottom: 1px solid var(--rule);
	}

	nav a {
		padding: 7px 14px 7px 13px;
		font-size: 13.5px;
		color: var(--ink-2);
		border-left: 2px solid transparent;
		transition: background var(--ease), color var(--ease);
	}

	nav a:hover {
		background: var(--sunken);
		text-decoration: none;
		color: var(--ink);
	}

	nav a[aria-current='page'] {
		border-left-color: var(--orange);
		color: var(--ink);
		font-weight: 500;
	}

	.block {
		padding: 13px 14px;
		border-bottom: 1px solid var(--rule);
	}

	.block .label {
		margin-bottom: 8px;
	}

	.block ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.line {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 8px;
		width: 100%;
		padding: 5px 0 5px 9px;
		border-left: 2px solid transparent;
		font-size: 13px;
		color: var(--ink-2);
		text-align: left;
		transition: color var(--ease), border-color var(--ease);
	}

	.line:hover {
		color: var(--ink);
	}

	.line[aria-current='true'] {
		border-left-color: var(--orange);
		color: var(--ink);
		font-weight: 500;
	}

	.line-count {
		font-size: 11.5px;
		color: var(--ink-3);
	}

	.facts {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 3px 10px;
		margin: 0;
		font-size: 12.5px;
	}

	.facts dt {
		color: var(--ink-3);
	}

	.facts dd {
		margin: 0;
		text-align: right;
	}

	.rail-foot {
		margin-top: auto;
		padding: 12px 14px 14px;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.refresh {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 11.5px;
		color: var(--ink-3);
	}

	.dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		flex: none;
	}

	.dot.ok {
		background: var(--accent);
	}

	.dot.bad {
		background: var(--oxblood);
	}

	main {
		min-width: 0;
		overflow: hidden;
		display: flex;
		flex-direction: column;
	}

	.toasts {
		position: fixed;
		right: 16px;
		bottom: 16px;
		display: flex;
		flex-direction: column;
		gap: 6px;
		z-index: 200;
		max-width: 400px;
	}

	.toast {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		background: var(--ink);
		color: var(--paper);
		border-radius: 2px;
		padding: 9px 10px;
		font-size: 12.5px;
		line-height: 1.4;
	}

	.toast[data-tone='warn'] {
		background: #4a3208;
	}

	.toast[data-tone='error'] {
		background: var(--oxblood);
	}

	.toast :global(.btn) {
		color: var(--paper);
		opacity: 0.75;
	}

	@media (max-width: 860px) {
		.app {
			grid-template-columns: 1fr;
			grid-template-rows: auto minmax(0, 1fr);
		}
		.rail {
			flex-direction: row;
			align-items: center;
			gap: 10px;
			overflow-x: auto;
			border-right: 0;
			border-bottom: 1px solid var(--rule);
		}
		.brand {
			border-bottom: 0;
			padding: 10px 12px;
		}
		.wordmark .label {
			display: none;
		}
		nav {
			flex-direction: row;
			border-bottom: 0;
			padding: 0;
		}
		nav a {
			border-left: 0;
			border-bottom: 2px solid transparent;
			padding: 10px 10px 8px;
		}
		nav a[aria-current='page'] {
			border-left-color: transparent;
			border-bottom-color: var(--orange);
		}
		.block {
			border-bottom: 0;
			padding: 0;
		}
		.block .label,
		.block ul,
		.rail-foot {
			display: none;
		}
	}
</style>
