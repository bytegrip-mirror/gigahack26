<script lang="ts">
	import '../app.css';

	import { page } from '$app/state';
	import { toasts } from '$lib/toast.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { companies } from '$lib/mock/companies';
	import { documents } from '$lib/mock/documents';
	import { events } from '$lib/mock/events';
	import { leadsFor } from '$lib/mock/engine';
	import Logo from '$lib/components/Logo.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Tag from '$lib/ui/Tag.svelte';

	let { children } = $props();

	const leads = $derived(leadsFor(icpStore.items, icpStore.rules));

	const nav = [
		{ href: '/', label: 'Leads', icon: 'inbox' },
		{ href: '/icps', label: 'ICP studio', icon: 'target' },
		{ href: '/watchers', label: 'Watchers', icon: 'eye' },
		{ href: '/windows', label: 'Windows', icon: 'clock' }
	] as const;

	function active(href: string): boolean {
		return href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);
	}
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

		<section class="border-b border-rule px-3 py-3 max-compact:hidden">
			<p class="type-label mb-2">The chain</p>
			<ol class="flex flex-col gap-1 text-sm text-ink-3">
				<li>1. Watch a company</li>
				<li>2. Keep each document as fetched</li>
				<li>3. Pull out dated events</li>
				<li>4. Match events to ICP triggers</li>
				<li>5. Rule on need, budget, timing</li>
				<li>6. Decide, then send to HubSpot</li>
			</ol>
		</section>

		<div class="mt-auto flex flex-col gap-2 px-3 pb-3 max-compact:ml-auto max-compact:flex-row max-compact:items-center max-compact:pb-0">
			<section class="border-b border-rule py-3 max-compact:hidden">
				<p class="type-label mb-2">Watched</p>
				<div class="grid grid-cols-2 gap-x-3 gap-y-2">
					<Metric compact value={companies.length} label="Companies" />
					<Metric compact value={documents.length} label="Documents" />
					<Metric compact value={events.length} label="Events" />
					<Metric compact value={leads.length} label="Leads" />
				</div>
			</section>
			<div class="flex items-center gap-2">
				<Tag tone="dashed" title="Every number and quote on these screens is static demo data. Nothing is fetched.">Demo data</Tag>
				<span class="type-sub max-compact:hidden">No live connection</span>
			</div>
		</div>
	</aside>

	<main class="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
		{@render children()}
	</main>
</div>

<div class="fixed right-4 bottom-4 z-toast flex max-w-menu flex-col gap-1" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class={['flex animate-arrive items-start gap-2 rounded-sm p-2 text-base leading-normal text-paper', toast.tone === 'warn' || toast.tone === 'error' ? 'bg-oxblood' : 'bg-ink']}>
			<span>{toast.message}</span>
			<Button variant="inverse" size="sm" onclick={() => toasts.dismiss(toast.id)}>Dismiss</Button>
		</div>
	{/each}
</div>
