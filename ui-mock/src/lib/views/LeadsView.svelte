<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Toolbar from '$lib/ui/Toolbar.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import LeadDetail, { type Tab } from './LeadDetail.svelte';
	import DimensionBars from '$lib/components/DimensionBars.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { DIMENSIONS, DIMENSION_LABEL, leadsFor, litLabel } from '$lib/mock/engine';
	import { hubspotCsv } from '$lib/mock/hubspot';
	import { copy, save } from '$lib/export';
	import { toasts } from '$lib/toast.svelte';

	let line = $state('all');
	let hideFlagged = $state(false);
	let query = $state('');
	const TABS: Tab[] = ['state', 'people', 'documents', 'hubspot'];

	const leads = $derived(leadsFor(icpStore.items, icpStore.rules));
	const litText = $derived(litLabel(icpStore.rules.lit));
	const selectedId = $derived(page.url.searchParams.get('lead'));
	const selected = $derived(leads.find((lead) => lead.id === selectedId) ?? null);
	const tabParam = $derived(page.url.searchParams.get('tab'));
	const tab = $derived<Tab>(TABS.find((value) => value === tabParam) ?? 'state');

	const inLine = $derived(leads.filter((lead) => line === 'all' || lead.icp.id === line));
	const flagged = $derived(inLine.filter((lead) => lead.flags.length > 0).length);
	const visible = $derived(
		inLine.filter(
			(lead) =>
				(!hideFlagged || lead.flags.length === 0) &&
				(query.trim() === '' || lead.company.name.toLowerCase().includes(query.trim().toLowerCase()))
		)
	);
	const moveCount = $derived(inLine.reduce((sum, lead) => sum + lead.moves.length, 0));
	const strong = $derived(inLine.filter((lead) => lead.lit >= 3).length);
	const named = $derived(inLine.filter((lead) => lead.contacts.length > 0).length);

	const lineOptions = $derived([
		{ value: 'all', label: 'All service lines' },
		...icpStore.items.map((icp) => ({ value: icp.id, label: icp.serviceLine }))
	]);

	const secondary: MenuItem[] = [
		{ id: 'link', label: 'Copy link', icon: 'link' },
		{ id: 'clear', label: 'Clear filters', icon: 'filter' },
		{ id: 'reset', label: 'Undo ICP edits', icon: 'refresh', separatorBefore: true }
	];

	function navigate(update: (url: URL) => void, replace = false) {
		const url = new URL(page.url);
		update(url);
		goto(url, { noScroll: true, keepFocus: true, replaceState: replace });
	}

	function select(id: string | null) {
		navigate((url) => {
			url.searchParams.delete('tab');
			if (id) url.searchParams.set('lead', id);
			else url.searchParams.delete('lead');
		});
	}

	function showTab(next: Tab) {
		navigate((url) => (next === 'state' ? url.searchParams.delete('tab') : url.searchParams.set('tab', next)), true);
	}

	function exportVisible() {
		if (visible.length === 0) return;
		save('hubspot-companies-contacts.csv', hubspotCsv(visible));
		toasts.push(`${visible.length} companies saved as a HubSpot import CSV`, 'success', 3000);
	}

	async function runSecondary(id: string) {
		if (id === 'link') {
			const done = await copy(window.location.href);
			toasts.push(done ? 'Link copied' : 'Copy failed', done ? 'success' : 'warn', 3000);
		}
		if (id === 'clear') {
			line = 'all';
			hideFlagged = false;
			query = '';
		}
		if (id === 'reset') {
			icpStore.reset();
			toasts.push('ICPs are back to the demo version', 'info', 3000);
		}
	}
</script>

<div class="relative flex min-h-0 flex-1">
	<View class="min-w-0 flex-1">
		<Topbar title="Leads">
			{#snippet subtitle()}
				Sorted by how many of the five dimensions are lit (at {litText} or more), then by the sum of the five levels. The order is a suggestion. Each row shows why.
			{/snippet}
			{#snippet metrics()}
				{#if selected === null}
					<div class="flex flex-wrap items-stretch max-compact:hidden">
						<Metric value={inLine.length} label="Companies" />
						<Metric value={strong} label="Lit on 3 or more" tone="hot" />
						<Metric value={named} label="With a named person" />
						<Metric value={moveCount} label="Moves" />
						<Metric value={flagged} label="Flagged" tone={flagged > 0 ? 'bad' : null} />
					</div>
				{/if}
			{/snippet}
			{#snippet tools()}
				<Button variant="primary" icon="download" onclick={exportVisible} disabled={visible.length === 0}>Export to HubSpot</Button>
				<Menu items={secondary} onselect={runSecondary} label="More lead actions" />
			{/snippet}
		</Topbar>

		<Toolbar>
			<Select class="max-w-menu" size="sm" label="Service line" options={lineOptions} bind:value={line} />
			<Input class="max-w-menu" size="sm" icon="search" placeholder="Find a company" bind:value={query} />
			<Checkbox bind:checked={hideFlagged}>Hide flagged companies ({flagged})</Checkbox>
		</Toolbar>

		<section class="scroll relative min-h-0 flex-1 bg-surface max-sm:overflow-x-auto" aria-label="Companies ranked by signal points">
			{#if visible.length === 0}
				<Empty>
					<Icon name="inbox" size="xl" />
					<p>No company matches this filter. Clear the filters, or widen an ICP.</p>
				</Empty>
			{:else}
				<table class="table-grid">
					<thead>
						<tr>
							<th scope="col" class="w-row"><span class="block text-right">#</span></th>
							<th scope="col" class={selected !== null ? 'w-2/5' : 'w-1/4'}>Company</th>
							<th scope="col"><span class="grid grid-cols-5 gap-2">{#each DIMENSIONS as dimension (dimension)}<span class="truncate">{DIMENSION_LABEL[dimension]}</span>{/each}</span></th>
							<th scope="col" class="w-1/12"><span class="block text-right">Lit</span></th>
							<th scope="col" class={selected !== null ? 'hidden' : 'w-1/5'}>Top contact</th>
							<th scope="col" class={selected !== null ? 'hidden' : 'w-1/12 max-md:hidden'}><span class="block text-right">Total</span></th>
						</tr>
					</thead>
					<tbody>
						{#each visible as lead, index (lead.id)}
							{@const top = lead.contacts[0]}
							<tr
								class={[
									'cursor-pointer border-l-2 border-transparent transition-colors hover:bg-paper',
									selectedId === lead.id && 'border-orange bg-orange-wash'
								]}
								onclick={() => select(lead.id)}
							>
								<td class="num text-right text-base text-ink-3">{index + 1}</td>
								<td>
									<span class="block font-display text-lg leading-snug font-medium tracking-num">{lead.company.name}</span>
									<span class="block truncate type-sub">
										<span class="font-medium text-orange-ink">{lead.icp.serviceLine}</span> · {lead.company.country} · {lead.company.industry}
									</span>
									{#each lead.flags as flag (flag.id)}
										<span class="mt-1 flex items-start gap-1">
											<Tag tone="oxblood">Openness {flag.base.toFixed(1)}</Tag>
											<span class="line-clamp-2 text-sm leading-snug text-oxblood">{flag.trigger.text}: “{flag.event.quote}”</span>
										</span>
									{/each}
								</td>
								<td class="align-middle"><DimensionBars levels={lead.levels} thresholds={lead.thresholds} /></td>
								<td class="num text-right">
									<span class="text-lg font-medium">{lead.lit}</span><span class="text-sm text-ink-3"> of 5</span>
								</td>
								<td class={['text-base', selected !== null ? 'hidden' : '']}>
									{#if top}
										<span class="block truncate font-medium">{top.person.name}</span>
										<span class="line-clamp-2 text-sm text-ink-3">{top.person.role}</span>
									{:else}
										<span class="text-sm text-ink-4">No named contact</span>
									{/if}
								</td>
								<td class={['num text-right text-base', selected !== null ? 'hidden' : 'max-md:hidden']}>{lead.total.toFixed(1)}</td>
							</tr>
						{/each}
					</tbody>
				</table>
				<p class="px-4 py-3 type-sub">
					Each bar is a level. The tick marks the lit line at {litText}. Orange bars are lit, red bars are below zero.
				</p>
			{/if}
		</section>
	</View>

	{#if selected}
		<div class="fixed inset-0 z-raised animate-scrim bg-scrim md:hidden" role="presentation" onclick={() => select(null)}></div>
		<aside
			class={[
				'min-h-0 animate-enter-right border-l border-ink bg-surface',
				'max-md:fixed max-md:inset-y-0 max-md:right-0 max-md:z-overlay max-md:w-full max-md:max-w-palette',
				'md:relative md:w-1/2 md:max-w-dialog md:flex-none'
			]}
			aria-label="Company detail"
		>
			{#key selected.id}
				{@const current = selected}
				<LeadDetail lead={current} {tab} ontab={showTab} onclose={() => select(null)} />
			{/key}
		</aside>
	{/if}
</div>
