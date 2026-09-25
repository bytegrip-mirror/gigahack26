<script lang="ts">
	import '../app.css';
	import '@fontsource-variable/space-grotesk';
	import '@fontsource-variable/archivo';

	import { page } from '$app/state';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { command } from '$lib/command.svelte';
	import { shortNumber } from '$lib/format';
	import { regionChoices, regionLabel } from '$lib/regions';
	import Logo from '$lib/components/Logo.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import CommandPalette from '$lib/components/CommandPalette.svelte';

	let { children } = $props();

	let now = $state(Date.now());

	const healthy = $derived(
		workspace.status !== null &&
			workspace.status.postgres === 'up' &&
			workspace.status.mongo === 'up'
	);

	const nav = [
		{ href: '/', label: 'Board', icon: 'board' },
		{ href: '/studio', label: 'Signal Studio', icon: 'studio' },
		{ href: '/pipeline', label: 'Pipeline', icon: 'pipeline' },
		{ href: '/settings', label: 'Settings', icon: 'settings' }
	] as const;

	function active(href: string): boolean {
		return href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);
	}

	const selected = $derived(session.serviceLine);

	const countdown = $derived.by(() => {
		const target = workspace.meta?.nextRefreshAt ? new Date(workspace.meta.nextRefreshAt).getTime() : null;
		if (target === null) return '—';
		const seconds = Math.max(0, Math.round((target - now) / 1000));
		if (seconds < 60) return `${seconds}s`;
		const minutes = Math.round(seconds / 60);
		return minutes < 60 ? `${minutes}m` : `${Math.round(minutes / 60)}h`;
	});

	$effect(() => {
		const timer = setInterval(() => (now = Date.now()), 1000);
		return () => clearInterval(timer);
	});

	$effect(() => {
		const line = session.serviceLine;
		if (workspace.meta?.serviceLine === line) return;
		workspace.load(line || null).catch(() => (workspace.loading = false));
	});
</script>

<div class="app">
	<aside class="rail">
		<div class="rail-top">
			<a class="brand" href="/">
				<Logo size={19} />
				<span class="wordmark">
					<strong class="dsp">Signal Desk</strong>
					<span class="label">Orange Systems</span>
				</span>
			</a>

			<button class="searchbox" type="button" onclick={() => command.show()}>
				<Icon name="search" size={15} />
				<span class="searchbox-text">Search</span>
				<kbd>⌘K</kbd>
			</button>
		</div>

		<nav aria-label="Views">
			{#each nav as item (item.href)}
				<a href={item.href} aria-current={active(item.href) ? 'page' : undefined}>
					<Icon name={item.icon} size={16} />
					<span class="nav-label">{item.label}</span>
				</a>
			{/each}
		</nav>

		{#if workspace.status}
			<section class="block">
				<p class="label">Corpus</p>
				<div class="corpus">
					<Metric value={shortNumber(workspace.status.companies)} label="Companies" />
					<Metric value={shortNumber(workspace.status.evidence)} label="Documents" />
					<Metric value={shortNumber(workspace.status.signals)} label="Signals" />
				</div>
			</section>
		{/if}

		<div class="rail-foot">
			<div class="region">
				<p class="label">Board region</p>
				<Menu
					text={regionLabel(workspace.regions, session.region)}
					icon="target"
					align="start"
					block
					label="Board region"
					title="Which markets the board ranks. The board shows the choice."
					items={regionChoices(workspace.regions).map((choice) => ({
						id: choice.key,
						label: choice.label,
						checked: choice.key === session.region
					}))}
					onselect={(key) => session.setRegion(key)}
				/>
			</div>

			{#if workspace.meta?.nextRefreshAt}
				<p class="refresh" title="The board re-scores itself on this interval">
					<span class="dot" class:ok={healthy} class:bad={!healthy}></span>
					<span class="label">Refresh</span>
					<span class="num">{countdown}</span>
				</p>
			{:else if workspace.loading}
				<p class="refresh"><span class="spinner"></span> <span class="label">Connecting</span></p>
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

<CommandPalette serviceLines={workspace.lines} regions={workspace.regions} />

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

	.rail-top {
		border-bottom: 1px solid var(--rule);
		padding-bottom: var(--sp-2);
	}

	.brand {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
		padding: var(--sp-3) var(--sp-3) var(--sp-2);
		color: var(--ink);
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
		font-size: var(--fs-xl);
		line-height: 1.1;
	}

	.wordmark .label {
		font-size: var(--fs-micro);
	}

	.searchbox {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
		margin: 0 var(--sp-3);
		padding: 0 var(--sp-2);
		height: var(--ctl);
		width: calc(100% - var(--sp-5));
		border: 1px solid var(--rule-strong);
		border-radius: 2px;
		background: var(--surface);
		color: var(--ink-3);
		font-size: var(--fs-base);
		transition: border-color var(--ease), color var(--ease);
	}

	.searchbox:hover {
		border-color: var(--ink-3);
		color: var(--ink);
	}

	.searchbox-text {
		flex: 1;
		text-align: left;
	}

	kbd {
		border: 1px solid var(--rule);
		border-radius: 2px;
		padding: 0 3px;
		font-family: var(--font-ui);
		font-size: var(--fs-micro);
		color: var(--ink-4);
	}

	nav {
		display: flex;
		flex-direction: column;
		padding: var(--sp-1) 0;
		border-bottom: 1px solid var(--rule);
	}

	nav a {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
		padding: 6px var(--sp-3) 6px 11px;
		font-size: var(--fs-md);
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

	nav a :global(.icon) {
		color: var(--ink-3);
	}

	nav a[aria-current='page'] :global(.icon) {
		color: var(--orange-deep);
	}

	.block {
		padding: var(--sp-3);
		border-bottom: 1px solid var(--rule);
	}

	.block .label {
		display: block;
		margin-bottom: var(--sp-2);
	}


	.corpus {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--sp-2) var(--sp-3);
	}

	.corpus :global(.metric) {
		padding: 0;
		border-left: 0;
		min-width: 0;
	}

	.corpus :global(.metric-val) {
		font-size: var(--fs-lg);
	}

	.rail-foot {
		margin-top: auto;
		padding: var(--sp-3);
		display: flex;
		flex-direction: column;
		gap: var(--sp-2);
		border-top: 1px solid var(--rule);
	}

	.region {
		display: flex;
		flex-direction: column;
		gap: var(--sp-1);
	}

	.region .label {
		display: block;
	}

	.refresh {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		font-size: var(--fs-sm);
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
		right: var(--sp-4);
		bottom: var(--sp-4);
		display: flex;
		flex-direction: column;
		gap: var(--sp-1);
		z-index: var(--z-toast);
		max-width: 400px;
	}

	.toast {
		display: flex;
		align-items: flex-start;
		gap: var(--sp-2);
		background: var(--ink);
		color: var(--paper);
		border-radius: 2px;
		padding: var(--sp-2) 10px;
		font-size: var(--fs-base);
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
			gap: var(--sp-2);
			overflow-x: auto;
			border-right: 0;
			border-bottom: 1px solid var(--rule);
		}
		.rail-top {
			display: flex;
			align-items: center;
			border-bottom: 0;
			padding: 0;
			flex: none;
		}
		.brand {
			padding: var(--sp-2) var(--sp-3);
		}
		.wordmark .label {
			display: none;
		}
		.searchbox {
			width: var(--ctl);
			margin: 0 var(--sp-2) 0 0;
			justify-content: center;
		}
		.searchbox-text,
		kbd {
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
			padding: var(--sp-2);
		}
		nav a[aria-current='page'] {
			border-left-color: transparent;
			border-bottom-color: var(--orange);
		}
		.nav-label {
			display: none;
		}
		.block,
		.rail-foot {
			display: none;
		}
	}
</style>
