<script lang="ts">
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Button from '$lib/ui/Button.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { companies } from '$lib/mock/companies';
	import { events } from '$lib/mock/events';
	import { matches } from '$lib/mock/matches';
	import { DIMENSION_LABEL, documentOf, leadsFor, signed } from '$lib/mock/engine';
	import { csv, save } from '$lib/export';
	import { date, host, month } from '$lib/format';
	import { toasts } from '$lib/toast.svelte';

	const leads = $derived(leadsFor(icpStore.items, icpStore.rules));

	const windows = $derived(
		leads
			.flatMap((lead) => lead.moves.filter((signal) => signal.term).map((signal) => ({ lead, signal, endsAt: signal.term?.endsAt ?? null })))
			.sort((a, b) => (a.endsAt ?? '9999').localeCompare(b.endsAt ?? '9999'))
	);

	const stated = $derived(windows.filter((row) => row.endsAt !== null).length);

	const contracts = $derived(
		events
			.filter((event) => event.type === 'VENDOR_CONTRACT')
			.sort((a, b) => b.date.localeCompare(a.date))
			.map((event) => ({
				event,
				company: companies.find((company) => company.id === event.companyId),
				term: matches.find((match) => match.eventId === event.id && match.term)?.term,
				uses: leads.flatMap((lead) => lead.moves.filter((signal) => signal.event.id === event.id).map((signal) => ({ lead, signal })))
			}))
	);

	function download() {
		const text = csv(
			['Company', 'Service line', 'Contract', 'Term as stated', 'Ends', 'Source'],
			windows.map((row) => [
				row.lead.company.name,
				row.lead.icp.serviceLine,
				row.signal.event.summary,
				row.signal.term?.basis ?? '',
				row.endsAt ?? 'term not public',
				row.signal.document.url
			])
		);
		save('contract-windows.csv', text);
		toasts.push('Windows saved as CSV', 'success', 3000);
	}
</script>

<View>
	<Topbar title="Windows">
		{#snippet subtitle()}Contracts that lock a company in today. An end date shows only when a source states the term.{/snippet}
		{#snippet metrics()}
			<div class="flex flex-wrap items-stretch max-compact:hidden">
				<Metric value={windows.length} label="Locks found" />
				<Metric value={stated} label="End date stated" tone="hot" />
				<Metric value={contracts.length} label="Contracts seen" />
			</div>
		{/snippet}
		{#snippet tools()}
			<Button variant="primary" icon="download" onclick={download} disabled={windows.length === 0}>Download CSV</Button>
		{/snippet}
	</Topbar>

	<section class="scroll relative min-h-0 flex-1 bg-surface max-sm:overflow-x-auto" aria-label="Contract windows">
		<table class="table-grid">
			<thead>
				<tr>
					<th scope="col" class="w-1/5">Company</th>
					<th scope="col">Contract</th>
					<th scope="col" class="w-1/4">Term, as the source states it</th>
					<th scope="col" class="w-1/8"><span class="block text-right">Ends</span></th>
				</tr>
			</thead>
			<tbody>
				{#each windows as row (row.signal.id + row.lead.id)}
					<tr>
						<td>
							<a class="block font-display text-lg leading-snug font-medium tracking-num text-ink" href={`/?lead=${row.lead.id}`}>{row.lead.company.name}</a>
							<span class="block truncate text-sm font-medium text-orange-ink">{row.lead.icp.serviceLine}</span>
						</td>
						<td>
							<span class="block text-base leading-snug">{row.signal.event.summary}</span>
							<span class="block text-sm leading-normal text-ink-3">“{row.signal.event.quote}”</span>
							<a class="text-sm" href={row.signal.document.url} target="_blank" rel="noreferrer noopener">{host(row.signal.document.url)}</a>
						</td>
						<td class="text-base leading-snug text-ink-2">{row.signal.term?.basis}</td>
						<td class="num text-right">
							{#if row.endsAt}
								<span class="text-lg font-medium">{month(row.endsAt)}</span>
							{:else}
								<span class="text-sm text-ink-3">Term not public</span>
							{/if}
						</td>
					</tr>
				{:else}
					<tr><td colspan="4" class="type-sub">No contract lock matches any ICP right now.</td></tr>
				{/each}
			</tbody>
		</table>

		<h2 class="type-label border-b border-rule px-4 pt-5 pb-2">Every vendor contract the watchers saw</h2>
		<table class="table-grid">
			<thead>
				<tr>
					<th scope="col" class="w-1/5">Company</th>
					<th scope="col">Event</th>
					<th scope="col" class="w-1/8"><span class="block text-right">Signed</span></th>
					<th scope="col" class="w-1/4">How the ICPs score it</th>
				</tr>
			</thead>
			<tbody>
				{#each contracts as row (row.event.id)}
					{@const source = documentOf(row.event)}
					<tr>
						<td class="text-base font-medium">{row.company?.name}</td>
						<td>
							<span class="block text-base leading-snug">{row.event.summary}</span>
							<span class="block text-sm text-ink-3">{row.term?.basis ?? 'Term not stated'}</span>
							<a class="text-sm" href={source.url} target="_blank" rel="noreferrer noopener">{host(source.url)}</a>
						</td>
						<td class="num text-right text-base">{date(row.event.date)}</td>
						<td>
							{#each row.uses as use (use.lead.id + use.signal.id)}
								<span class="block text-sm leading-normal">
									<span class="font-medium text-orange-ink">{use.lead.icp.serviceLine}:</span>
									{DIMENSION_LABEL[use.signal.dimension]} {signed(use.signal.base, 2)}, {use.signal.trigger.text}
								</span>
							{:else}
								<span class="text-sm text-ink-4">A fact on file. No ICP scores it.</span>
							{/each}
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</section>
</View>
