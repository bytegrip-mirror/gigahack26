<script lang="ts">
	import '../app.css';
	import { untrack } from 'svelte';
	import { page } from '$app/state';
	import { fetchMeta, fetchStatus, type Meta, type Status } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { plural, shortNumber } from '$lib/format';

	let { children } = $props();

	let meta = $state<Meta | null>(null);
	let status = $state<Status | null>(null);
	let loading = $state(true);

	$effect(() => {
		let cancelled = false;
		const stored = untrack(() => session.serviceLine);
		Promise.all([fetchMeta(stored || undefined), fetchStatus()])
			.then(([metaResult, statusResult]) => {
				if (cancelled) return;
				meta = metaResult;
				status = statusResult;
				loading = false;
				if (!stored) {
					session.set(metaResult.defaultServiceLine);
				}
				if (metaResult.degraded) {
					toasts.push(
						'The language model is not reachable, so the board is running on the local rubric engine. Re-scores stay instant and offline.',
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

	const healthy = $derived(status !== null && status.postgres === 'up' && status.mongo === 'up');

	function switchServiceLine(value: string) {
		session.set(value);
		meta = null;
		fetchMeta(value).then((result) => (meta = result));
	}
</script>

<div class="shell">
	<header>
		<div class="bar">
			<a class="brand" href="/">
				<span class="mark" aria-hidden="true"></span>
				<span class="brand-text">
					<strong>Signal Desk</strong>
					<span class="tiny muted">Orange Systems · B2B buying signals</span>
				</span>
			</a>

			<nav>
				<a href="/" class:active={page.url.pathname === '/'}>Board</a>
				<a href="/studio" class:active={page.url.pathname.startsWith('/studio')}>Signal Studio</a>
			</nav>

			<div class="right">
				{#if meta && meta.serviceLines.length > 0}
					<label class="line-picker">
						<span class="sr-only">Service line</span>
						<select
							class="select"
							value={session.serviceLine}
							onchange={(event) => switchServiceLine(event.currentTarget.value)}
						>
							{#each meta.serviceLines as line (line)}
								<option value={line}>{line}</option>
							{/each}
						</select>
					</label>
				{/if}

				{#if status}
					<div class="status" title="PostgreSQL, MongoDB and the classifier engine">
						<span class="dot" class:ok={healthy} class:bad={!healthy}></span>
						<span class="tiny mono">
							{status.postgres}/{status.mongo}
						</span>
						<span class="sep"></span>
						<span class="tiny mono muted" title="Corpus size">
							{shortNumber(status.companies)} co · {shortNumber(status.evidence)} docs
						</span>
					</div>
				{:else if loading}
					<div class="status"><span class="spinner"></span></div>
				{/if}
			</div>
		</div>
	</header>

	<main>
		{@render children()}
	</main>

	<footer>
		<span class="tiny muted">
			Every claim on this board resolves to a byte range in a stored capture.
			{#if status && status.signals > 0}
				{plural(status.signals, 'signal')} derived from {plural(status.evidence, 'document')}.
			{/if}
		</span>
		{#if meta}
			<span class="tiny mono muted">engine {meta.engine}</span>
		{/if}
	</footer>
</div>

<div class="toasts" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class="toast toast-{toast.tone}">
			<span>{toast.message}</span>
			<button class="btn btn-ghost tiny" onclick={() => toasts.dismiss(toast.id)}>Dismiss</button>
		</div>
	{/each}
</div>

<style>
	.shell {
		min-height: 100%;
		display: flex;
		flex-direction: column;
	}

	header {
		position: sticky;
		top: 0;
		z-index: 30;
		background: var(--surface);
		border-bottom: 1px solid var(--line);
	}

	.bar {
		max-width: 1560px;
		margin: 0 auto;
		padding: 0 24px;
		height: var(--header-height);
		display: flex;
		align-items: center;
		gap: 28px;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: 10px;
		text-decoration: none;
		color: var(--ink);
	}

	.mark {
		width: 26px;
		height: 26px;
		border-radius: 7px;
		background: var(--orange);
		box-shadow: inset 0 -9px 0 rgb(0 0 0 / 0.14);
		flex: none;
	}

	.brand-text {
		display: flex;
		flex-direction: column;
		line-height: 1.15;
	}

	.brand-text strong {
		font-size: 15px;
		letter-spacing: -0.01em;
	}

	nav {
		display: flex;
		gap: 2px;
		margin-right: auto;
	}

	nav a {
		text-decoration: none;
		color: var(--ink-2);
		font-size: 14px;
		font-weight: 550;
		padding: 6px 12px;
		border-radius: var(--radius-sm);
	}

	nav a:hover {
		background: var(--surface-sunken);
		color: var(--ink);
	}

	nav a.active {
		background: var(--orange-050);
		color: var(--orange-ink);
	}

	.right {
		display: flex;
		align-items: center;
		gap: 14px;
	}

	.line-picker select {
		width: auto;
		min-width: 168px;
		font-size: 13px;
		padding: 6px 8px;
	}

	.status {
		display: flex;
		align-items: center;
		gap: 7px;
		padding: 4px 10px;
		border: 1px solid var(--line);
		border-radius: 999px;
		background: var(--surface-sunken);
	}

	.dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
	}

	.dot.ok {
		background: var(--success);
	}

	.dot.bad {
		background: var(--danger);
	}

	.sep {
		width: 1px;
		height: 11px;
		background: var(--line-strong);
	}

	main {
		flex: 1;
		max-width: 1560px;
		width: 100%;
		margin: 0 auto;
		padding: 22px 24px 40px;
	}

	footer {
		border-top: 1px solid var(--line);
		padding: 14px 24px;
		display: flex;
		justify-content: space-between;
		gap: 16px;
		max-width: 1560px;
		margin: 0 auto;
		width: 100%;
	}

	.toasts {
		position: fixed;
		right: 20px;
		bottom: 20px;
		display: flex;
		flex-direction: column;
		gap: 8px;
		z-index: 100;
		max-width: 420px;
	}

	.toast {
		display: flex;
		align-items: flex-start;
		gap: 10px;
		background: var(--surface);
		border: 1px solid var(--line-strong);
		border-left: 3px solid var(--orange);
		border-radius: var(--radius);
		box-shadow: var(--shadow-md);
		padding: 11px 12px;
		font-size: 13px;
	}

	.toast-warn {
		border-left-color: var(--warn);
	}

	.toast-error {
		border-left-color: var(--danger);
	}

	.toast-success {
		border-left-color: var(--success);
	}

	@media (max-width: 900px) {
		.bar {
			gap: 14px;
			padding: 0 14px;
		}
		.brand-text .tiny {
			display: none;
		}
		.status .sep,
		.status .muted {
			display: none;
		}
		main {
			padding: 16px 14px 32px;
		}
	}
</style>
