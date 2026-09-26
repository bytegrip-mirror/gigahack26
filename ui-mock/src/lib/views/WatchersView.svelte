<script lang="ts">
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { SOURCES, SOURCE_LABEL, companies, watchers } from '$lib/mock/companies';
	import { documentsOf, eventsOf, leadsFor } from '$lib/mock/engine';
	import { ago, timestamp } from '$lib/format';
	import { toasts } from '$lib/toast.svelte';

	const leads = $derived(leadsFor(icpStore.items, icpStore.rules));

	const rows = $derived(
		watchers.map((watcher) => {
			const company = companies.find((candidate) => candidate.id === watcher.companyId);
			return {
				watcher,
				company,
				documents: documentsOf(watcher.companyId).length,
				events: eventsOf(watcher.companyId).length,
				leads: leads.filter((lead) => lead.company.id === watcher.companyId)
			};
		})
	);

	const totalDocuments = $derived(rows.reduce((sum, row) => sum + row.documents, 0));
	const totalEvents = $derived(rows.reduce((sum, row) => sum + row.events, 0));
	const failing = $derived(watchers.filter((watcher) => watcher.status === 'failing').length);

	const STATUS_LABEL = { ok: 'Healthy', slow: 'Slow', failing: 'Failing' } as const;
</script>

<View>
	<Topbar title="Watchers">
		{#snippet subtitle()}Each watched company, the sources we read, and what came out.{/snippet}
		{#snippet metrics()}
			<div class="flex flex-wrap items-stretch max-compact:hidden">
				<Metric value={watchers.length} label="Watched" />
				<Metric value={totalDocuments} label="Documents" />
				<Metric value={totalEvents} label="Events" />
				<Metric value={failing} label="Failing" tone={failing > 0 ? 'bad' : null} />
			</div>
		{/snippet}
		{#snippet tools()}
			<Button variant="primary" icon="plus" onclick={() => toasts.push('The demo cannot add watchers. Nothing is fetched.', 'info', 3000)}>Watch a company</Button>
		{/snippet}
	</Topbar>

	<section class="scroll relative min-h-0 flex-1 bg-surface max-sm:overflow-x-auto" aria-label="Watchers">
		<table class="table-grid">
			<thead>
				<tr>
					<th scope="col" class="w-1/4">Company</th>
					<th scope="col">Sources watched</th>
					<th scope="col" class="w-1/8">Last checked</th>
					<th scope="col" class="w-1/12"><span class="block text-center">Status</span></th>
					<th scope="col" class="w-1/12"><span class="block text-right">Docs</span></th>
					<th scope="col" class="w-1/12"><span class="block text-right">Events</span></th>
					<th scope="col" class="w-1/6 max-md:hidden">Leads</th>
				</tr>
			</thead>
			<tbody>
				{#each rows as row (row.watcher.companyId)}
					<tr>
						<td>
							<span class="block font-display text-lg leading-snug font-medium tracking-num">{row.company?.name}</span>
							<span class="block truncate type-sub">
								<a href="https://{row.company?.domain}" target="_blank" rel="noreferrer noopener">{row.company?.domain}</a>
								· {row.company?.city}, {row.company?.country} · {row.company?.industry} · {row.company?.size}
							</span>
							{#if row.company?.competitor}<span class="block text-sm text-oxblood">Listed competitor</span>{/if}
						</td>
						<td>
							<div class="flex flex-wrap gap-1">
								{#each SOURCES as source (source)}
									<Tag tone={row.watcher.sources.includes(source) ? 'default' : 'dashed'}>
										{#if !row.watcher.sources.includes(source)}<span class="line-through">{SOURCE_LABEL[source]}</span>{:else}{SOURCE_LABEL[source]}{/if}
									</Tag>
								{/each}
							</div>
						</td>
						<td class="text-base" title={timestamp(row.watcher.lastChecked)}>
							<span class="num block">{ago(row.watcher.lastChecked)}</span>
							<span class="num block text-micro text-ink-4">next {timestamp(row.watcher.nextCheck).slice(5, 16)}</span>
						</td>
						<td class="text-center">
							<Tag tone={row.watcher.status === 'ok' ? 'default' : row.watcher.status === 'slow' ? 'wash' : 'oxblood'}>{STATUS_LABEL[row.watcher.status]}</Tag>
						</td>
						<td class="num text-right text-base">{row.documents}</td>
						<td class="num text-right text-base">{row.events}</td>
						<td class="max-md:hidden">
							<div class="flex flex-col gap-0.5">
								{#each row.leads as lead (lead.id)}
									<a class="truncate text-sm" href={`/?lead=${lead.id}`}>{lead.icp.serviceLine}</a>
								{:else}
									<span class="text-sm text-ink-4">No ICP fits</span>
								{/each}
							</div>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
		<p class="px-4 py-3 type-sub">
			A watcher keeps each document exactly as fetched, with its hash. Events are pulled from those documents and never edited.
		</p>
	</section>
</View>
