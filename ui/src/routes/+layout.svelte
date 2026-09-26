<script lang="ts">
	import '../app.css';
	import '@fontsource-variable/space-grotesk';
	import '@fontsource-variable/archivo';

	import { page } from '$app/state';
	import { fetchBookmarks } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { command } from '$lib/command.svelte';
	import { shortNumber } from '$lib/format';
	import { regionChoices, regionLabel } from '$lib/regions';
	import Logo from '$lib/components/Logo.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';
	import CommandPalette from '$lib/components/CommandPalette.svelte';

	let { children } = $props();

	let now = $state(Date.now());
	let savedCount = $state(0);

	const healthy = $derived(
		workspace.status !== null &&
			workspace.status.postgres === 'up' &&
			workspace.status.mongo === 'up'
	);

	const nav = [
		{ href: '/', label: 'Board', icon: 'board' },
		{ href: '/studio', label: 'Signal Studio', icon: 'studio' },
		{ href: '/pipeline', label: 'Pipeline', icon: 'pipeline' },
		{ href: '/market', label: 'Market', icon: 'board' },
		{ href: '/settings', label: 'Settings', icon: 'settings' }
	] as const;

	$effect(() => {
		void page.url.pathname;
		void workspace.savedRevision;
		fetchBookmarks()
			.then((list) => (savedCount = list.length))
			.catch(() => (savedCount = 0));
	});

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

<div class="flex h-full max-compact:flex-col">
	<aside
		class="flex w-rail flex-none flex-col overflow-y-auto border-r border-rule bg-paper max-compact:w-full max-compact:flex-row max-compact:items-center max-compact:gap-2 max-compact:overflow-x-auto max-compact:border-r-0 max-compact:border-b"
	>
		<div class="border-b border-rule pb-2 max-compact:flex max-compact:flex-none max-compact:items-center max-compact:border-b-0 max-compact:pb-0">
			<a href="/" class="flex items-center gap-2 px-3 pt-3 pb-2 text-ink hover:no-underline max-compact:py-2">
				<Logo size={19} />
				<span class="flex min-w-0 flex-col gap-px">
					<strong class="type-display text-xl leading-tight">Signal Desk</strong>
					<span class="type-label max-compact:hidden">Orange Systems</span>
				</span>
			</a>

			<div class="px-3 max-compact:flex-none max-compact:px-0 max-compact:pr-2">
				<button
					type="button"
					class="flex h-ctl w-full items-center gap-2 rounded-sm border border-rule-strong bg-surface px-2 text-base text-ink-3 transition-colors hover:border-ink-3 hover:text-ink max-compact:w-ctl max-compact:justify-center"
					onclick={() => command.show()}
				>
					<Icon name="search" size="md" />
					<span class="flex-1 text-left max-compact:hidden">Search</span>
					<kbd class="rounded-sm border border-rule px-1 font-ui text-micro text-ink-4 max-compact:hidden">⌘K</kbd>
				</button>
			</div>
		</div>

		<nav
			aria-label="Views"
			class="flex flex-col border-b border-rule py-1 max-compact:flex-none max-compact:flex-row max-compact:border-b-0 max-compact:py-0"
		>
			{#each nav as item (item.href)}
				<a
					href={item.href}
					aria-current={active(item.href) ? 'page' : undefined}
					class={[
						'flex items-center gap-2 border-l-2 py-2 pr-3 pl-3 text-md transition-colors hover:bg-sunken hover:text-ink hover:no-underline max-compact:flex-none max-compact:border-l-0 max-compact:border-b-2 max-compact:p-2',
						active(item.href) ? 'border-l-orange text-ink font-medium max-compact:border-b-orange' : 'border-transparent text-ink-2'
					]}
				>
					<span class={active(item.href) ? 'text-orange-deep' : 'text-ink-3'}><Icon name={item.icon} size="lg" /></span>
					<span class="max-compact:hidden">{item.label}</span>
				</a>
			{/each}
		</nav>

		<div class="mt-auto flex flex-col gap-2 px-3 pb-3 max-compact:hidden">
			{#if workspace.status}
				<section class="border-b border-rule py-3">
					<p class="type-label mb-2">Corpus</p>
					<div class="grid grid-cols-2 gap-x-3 gap-y-2">
						<Metric compact value={shortNumber(workspace.status.companies)} label="Companies" />
						<Metric compact value={shortNumber(workspace.status.evidence)} label="Documents" />
						<Metric compact value={shortNumber(workspace.status.signals)} label="Signals" />
						<Metric
							compact
							value={String(savedCount)}
							label="Saved"
							tone={savedCount > 0 ? 'good' : null}
							hint="Companies you have starred. The board's Saved filter shows them."
						/>
					</div>
				</section>
			{/if}

			<div class="flex flex-col gap-1">
				<p class="type-label">Board region</p>
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
				<p class="flex items-center gap-1 text-sm text-ink-3" title="The board re-scores itself on this interval">
					<span class={['size-2 flex-none rounded-full', healthy ? 'bg-accent' : 'bg-oxblood']}></span>
					<span class="type-label">Refresh</span>
					<span class="num">{countdown}</span>
				</p>
			{:else if workspace.loading}
				<p class="flex items-center gap-1 text-sm text-ink-3"><Spinner /> <span class="type-label">Connecting</span></p>
			{/if}
		</div>
	</aside>

	<main class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
		{@render children()}
	</main>
</div>

<div class="fixed right-4 bottom-4 z-toast flex max-w-menu flex-col gap-1" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class={['flex items-start gap-2 rounded-sm p-2 text-base leading-normal text-paper', toast.tone === 'warn' || toast.tone === 'error' ? 'bg-oxblood' : 'bg-ink']}>
			<span>{toast.message}</span>
			<Button variant="inverse" size="sm" onclick={() => toasts.dismiss(toast.id)}>Dismiss</Button>
		</div>
	{/each}
</div>

<CommandPalette serviceLines={workspace.lines} regions={workspace.regions} />
