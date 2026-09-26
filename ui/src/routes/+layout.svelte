<script lang="ts">
	import '../app.css';

	import { page } from '$app/state';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { command } from '$lib/command.svelte';
	import { regionChoices, regionLabel } from '$lib/regions';
	import Logo from '$lib/components/Logo.svelte';
	import Icon from '$lib/components/Icon.svelte';
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
		{ href: '/', label: 'Companies', icon: 'board' },
		{ href: '/people', label: 'People', icon: 'building' },
		{ href: '/pipeline', label: 'Pipeline', icon: 'pipeline' },
		{ href: '/icp', label: 'ICP Studio', icon: 'studio' },
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
	<aside class="rail on-ink">
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

		<div class="rail-foot">
			<div class="region">
				<p class="label">Board region</p>
				<Menu
					text={regionLabel(workspace.regions, session.region)}
					icon="target"
					align="start"
					stretch
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
		grid-template-columns: var(--spacing-rail) minmax(0, 1fr);
		height: 100vh;
	}

	.rail {
		display: flex;
		flex-direction: column;
		overflow-y: auto;
		scrollbar-width: none;
	}

	.rail::-webkit-scrollbar {
		display: none;
	}

	.rail-top {
		border-bottom: 1px solid var(--color-rule);
		padding-bottom: var(--spacing-2);
	}

	.brand {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-3) var(--spacing-3) var(--spacing-2);
		color: var(--color-ink);
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
		font-size: var(--text-xl);
		line-height: 1.1;
	}

	.wordmark .label {
		font-size: var(--text-micro);
	}

	.searchbox {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		margin: 0 var(--spacing-3);
		padding: 0 var(--spacing-2);
		height: var(--spacing-ctl);
		width: calc(100% - var(--spacing-5));
		border: var(--border-width-ctl) solid var(--color-rule-strong);
		border-radius: var(--radius);
		background: var(--color-surface);
		color: var(--color-ink-3);
		font-size: var(--text-base);
		transition: border-color var(--ease), color var(--ease);
	}

	.searchbox:hover {
		border-color: var(--color-ink-3);
		color: var(--color-ink);
	}

	.searchbox-text {
		flex: 1;
		text-align: left;
	}

	kbd {
		border: 1px solid var(--color-rule);
		border-radius: var(--radius);
		padding: 0 3px;
		font-family: var(--font-ui);
		font-size: var(--text-micro);
		color: var(--color-ink-4);
	}

	nav {
		display: flex;
		flex-direction: column;
		padding: var(--spacing-1) 0;
		border-bottom: 1px solid var(--color-rule);
	}

	nav a {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: 6px var(--spacing-3) 6px 11px;
		font-size: var(--text-md);
		color: var(--color-ink-2);
		border-left: 2px solid transparent;
		transition: background var(--ease), color var(--ease);
	}

	nav a:hover {
		background: var(--color-sunken);
		text-decoration: none;
		color: var(--color-ink);
	}

	nav a[aria-current='page'] {
		border-left-color: var(--color-orange);
		color: var(--color-ink);
		font-weight: 700;
	}

	nav a :global(.icon) {
		color: var(--color-ink-3);
	}

	nav a[aria-current='page'] :global(.icon) {
		color: var(--color-orange-deep);
	}

	.rail-foot {
		margin-top: auto;
		display: flex;
		flex-direction: column;
		padding: 0 var(--spacing-3) var(--spacing-3);
		gap: var(--spacing-2);
	}

	.region {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
	}

	.region .label {
		display: block;
	}

	.refresh {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		font-size: var(--text-sm);
		color: var(--color-ink-3);
	}

	.dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		flex: none;
	}

	.dot.ok {
		background: var(--color-accent);
	}

	.dot.bad {
		background: var(--color-oxblood);
	}

	main {
		min-width: 0;
		overflow: hidden;
		display: flex;
		flex-direction: column;
	}

	.toasts {
		animation: rise var(--ease-enter) both;
		position: fixed;
		right: var(--spacing-4);
		bottom: var(--spacing-4);
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
		z-index: var(--z-index-toast);
		max-width: 400px;
	}

	.toast {
		display: flex;
		align-items: flex-start;
		gap: var(--spacing-2);
		background: var(--color-ink);
		color: var(--color-paper);
		border-radius: var(--radius);
		padding: var(--spacing-2) 10px;
		font-size: var(--text-base);
		line-height: 1.4;
	}

	.toast[data-tone='warn'] {
		background: var(--color-warning);
		color: var(--color-ink);
	}

	.toast[data-tone='warn'] :global(.btn) {
		color: var(--color-ink);
	}

	.toast[data-tone='error'] {
		background: var(--color-oxblood);
	}

	.toast :global(.btn) {
		color: var(--color-paper);
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
			gap: var(--spacing-2);
			overflow-x: auto;
			border-right: 0;
			border-bottom: 1px solid var(--color-rule);
		}
		.rail-top {
			display: flex;
			align-items: center;
			border-bottom: 0;
			padding: 0;
			flex: none;
		}
		.brand {
			padding: var(--spacing-2) var(--spacing-3);
		}
		.wordmark .label {
			display: none;
		}
		.searchbox {
			width: var(--spacing-ctl);
			margin: 0 var(--spacing-2) 0 0;
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
			padding: var(--spacing-2);
		}
		nav a[aria-current='page'] {
			border-left-color: transparent;
			border-bottom-color: var(--color-orange);
		}
		.nav-label {
			display: none;
		}
		.rail-foot {
			display: none;
		}
	}</style>
