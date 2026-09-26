<script lang="ts">
	import { fetchLeads, fetchLeadTally, recordLead, type LeadTally, type LeadView } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { age, fit, host, plural } from '$lib/format';
	import { copy, csv, save } from '$lib/export';
	import Tag from '$lib/ui/Tag.svelte';
	import LoadingVeil from '$lib/ui/LoadingVeil.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Toolbar from '$lib/ui/Toolbar.svelte';
	import ToolbarSeparator from '$lib/ui/ToolbarSeparator.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Note from '$lib/ui/Note.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Input from '$lib/ui/Input.svelte';
	import type { MenuItem } from '$lib/ui/menu';

	const STAGES = [
		{ id: 'NEW', label: 'New' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const NEXT: Record<string, string> = {
		NEW: 'CONTACTED',
		CONTACTED: 'REPLIED',
		REPLIED: 'QUALIFIED'
	};

	let leads = $state<LeadView[]>([]);
	let tally = $state<LeadTally | null>(null);
	let loading = $state(true);
	let busy = $state<string | null>(null);
	let query = $state('');

	const ALL_LINES = '__all__';
	const serviceLine = $derived(session.serviceLine);
	const allLines = $derived(session.allLines);
	const scopeItems = $derived([
		{ id: ALL_LINES, label: 'All service lines', checked: allLines },
		...workspace.lines.map((line) => ({
			id: line,
			label: line,
			checked: !allLines && line === serviceLine
		}))
	]);

	const lines = $derived(allLines ? (workspace.lines.length > 0 ? workspace.lines : [serviceLine]) : [serviceLine]);

	async function load() {
		const results = await Promise.all(
			lines.map((line) =>
				Promise.all([fetchLeads(line), fetchLeadTally(line)]).then(([list, counts]) => ({ list, counts }))
			)
		);
		leads = results.flatMap(({ list }) => list);
		tally = results.reduce<LeadTally>(
			(total, { counts }) => ({
				NEW: total.NEW + counts.NEW,
				CONTACTED: total.CONTACTED + counts.CONTACTED,
				REPLIED: total.REPLIED + counts.REPLIED,
				QUALIFIED: total.QUALIFIED + counts.QUALIFIED,
				REJECTED: total.REJECTED + counts.REJECTED,
				TOTAL: total.TOTAL + counts.TOTAL,
				crmConfigured: total.crmConfigured || counts.crmConfigured
			}),
			{ NEW: 0, CONTACTED: 0, REPLIED: 0, QUALIFIED: 0, REJECTED: 0, TOTAL: 0, crmConfigured: false }
		);
	}

	$effect(() => {
		const line = serviceLine;
		void allLines;
		if (!line) return;
		let cancelled = false;
		loading = true;
		load()
			.catch((cause) => toasts.push((cause as Error).message, 'error'))
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	const matched = $derived.by(() => {
		const needle = query.trim().toLowerCase();
		if (!needle) return leads;
		return leads.filter((lead) =>
			`${lead.company} ${lead.domain ?? ''} ${lead.outreachSubject ?? ''}`.toLowerCase().includes(needle)
		);
	});

	const grouped = $derived(
		STAGES.map((stage) => ({
			...stage,
			rows: matched.filter((lead) => lead.status === stage.id)
		}))
	);

	const visibleStages = $derived(grouped.filter((stage) => stage.rows.length > 0));

	function daysSince(iso: string | null): number | null {
		return iso ? Math.max(0, (Date.now() - new Date(iso).getTime()) / 86_400_000) : null;
	}

	function stageLabel(status: string): string {
		return STAGES.find((stage) => stage.id === status)?.label.toLowerCase() ?? status.toLowerCase();
	}

	async function move(lead: LeadView, status: string) {
		if (status === lead.status) return;
		busy = lead.id;
		try {
			await recordLead({ companyId: lead.companyId, serviceLine: lead.serviceLine, status });
			await load();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = null;
		}
	}

	function menuFor(lead: LeadView): MenuItem[] {
		return [
			{ id: 'open', label: 'Open on the board', icon: 'board' },
			{ id: 'link', label: 'Copy a link to this company', icon: 'link' },
			{ id: 'export', label: 'Export this row', icon: 'download' },
			...STAGES.map((stage, index) => ({
				id: `stage-${stage.id}`,
				label: `${lead.status === stage.id ? 'Currently ' : 'Move to '}${stage.label.toLowerCase()}`,
				icon: lead.status === stage.id ? ('check' as const) : undefined,
				danger: stage.id === 'REJECTED',
				separatorBefore: index === 0
			}))
		];
	}

	async function runRow(lead: LeadView, id: string) {
		if (id === 'open') window.location.assign(`/?co=${lead.companyId}&line=${encodeURIComponent(lead.serviceLine)}`);
		else if (id === 'link') {
			const ok = await copy(`${window.location.origin}/?co=${lead.companyId}&line=${encodeURIComponent(lead.serviceLine)}`);
			toasts.push(ok ? `Link to ${lead.company} copied.` : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
		} else if (id === 'export') {
			save(
				`${lead.company.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.csv`,
				csv(
					['company', 'domain', 'service_line', 'stage', 'fit', 'band', 'contacts', 'last_contacted', 'channel', 'subject'],
					[
						[
							lead.company,
							lead.domain,
							lead.serviceLine,
							lead.statusLabel,
							lead.probability === null ? '' : fit(lead.probability),
							lead.band,
							lead.contactCount,
							lead.lastContactedAt,
							lead.channel,
							lead.outreachSubject
						]
					]
				)
			);
		} else if (id.startsWith('stage-')) await move(lead, id.slice(6));
	}

	function exportAll() {
		save(
			`pipeline-${(allLines ? 'all' : serviceLine).toLowerCase().replace(/\s+/g, '-')}.csv`,
			csv(
				['company', 'domain', 'stage', 'fit', 'band', 'contacts', 'last_contacted', 'channel', 'subject'],
				leads.map((lead) => [
					lead.company,
					lead.domain,
					lead.statusLabel,
					lead.probability === null ? '' : fit(lead.probability),
					lead.band,
					lead.contactCount,
					lead.lastContactedAt,
					lead.channel,
					lead.outreachSubject
				])
			)
		);
		toasts.push(`Exported ${leads.length} leads.`, 'success');
	}
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Pipeline</h1>
			<p class="sub num">
				{plural(leads.length, 'company', 'companies')} in the pipeline{#if allLines}
					across every service line{:else}
					on {serviceLine}{/if}
			</p>
		</div>

		{#if tally}
			<div class="metrics">
				<Metric value={tally.NEW} label="New" />
				<Metric value={tally.CONTACTED} label="Contacted" />
				<Metric value={tally.REPLIED} label="Replied" tone={tally.REPLIED > 0 ? 'hot' : null} />
				<Metric value={tally.QUALIFIED} label="Qualified" tone={tally.QUALIFIED > 0 ? 'good' : null} />
				<Metric value={tally.REJECTED} label="Rejected" />
			{/if}
		{/snippet}
	</Topbar>

	<Toolbar>
		<Menu
			text={session.scopeLabel()}
			icon="layers"
			align="start"
			searchable
			label="Service line"
			title="Which service line's leads this shows"
			items={scopeItems}
			onselect={(line) => (line === ALL_LINES ? session.setAll() : workspace.select(line))}
		/>
		{#if workspace.switching}<Spinner />{/if}
		<ToolbarSeparator />

		<Input
			size="sm"
			icon="search"
			placeholder="Company or subject"
			aria-label="Filter leads"
			bind:value={query}
			class="w-rail min-w-0 flex-1 max-sm:w-full"
		/>
		<span class="type-sub text-ink-4 num ml-auto">{matched.length} of {leads.length} shown</span>
		<Menu
			items={[
				{ id: 'export', label: 'Export the pipeline as CSV', icon: 'download' },
				{ id: 'board', label: 'Open the board', icon: 'board' }
			]}
			onselect={(id) => (id === 'export' ? exportAll() : window.location.assign('/'))}
			label="More pipeline actions"
		/>
	</Toolbar>

	{#if loading && leads.length === 0}
		<Note><Spinner /> Reading the pipeline</Note>
	{:else if leads.length === 0}
		<div class="empty">
			<Icon name="pipeline" size={22} />
			<h2 class="dsp">Nothing is in the pipeline yet</h2>
			<p class="sub">
				Open a company on the board and push it to HubSpot, or generate an outreach draft — either one lands
				here as New, with its evidence still attached.
			</p>
			<Button variant="ink" href="/">Go to the board</Button>
		</Empty>
	{:else}
		<div class="relative block min-h-0 flex-1 scroll max-sm:overflow-x-auto">
			<LoadingVeil show={loading} label="Reading the pipeline" />
			{#each visibleStages as stage, index (stage.id)}
				<section class={['border-ink', index > 0 && 'border-t']}>
					<header
						class={[
							'sticky top-0 z-sticky flex items-center justify-between gap-3 border-b border-b-rule bg-paper py-2 pr-4 pl-3 border-l-4',
							stage.id === 'QUALIFIED' && 'border-l-accent',
							stage.id === 'REJECTED' && 'border-l-oxblood',
							(stage.id === 'CONTACTED' || stage.id === 'REPLIED') && 'border-l-orange'
						]}
					>
						<h2 class="type-label">{stage.label}</h2>
						<span class="num">{stage.rows.length}</span>
					</header>
					<table class="table-grid">
						<thead>
							<tr>
								<th scope="col" class="w-3/10">Company</th>
								<th scope="col" class="w-1/16">Fit</th>
								<th scope="col" class="w-1/12 max-compact:hidden">Band</th>
								<th scope="col" class="w-3/20 max-compact:hidden">Last contact</th>
								<th scope="col" class="max-lg:hidden">Draft subject</th>
								<th scope="col" class="w-1/8"><span class="sr-only">Actions</span></th>
							</tr>
						</thead>
						<tbody>
							{#each stage.rows as lead (lead.id)}
								<tr>
									<td>
										<a
											class="block font-display text-lg font-medium leading-tight text-ink"
											href="/?co={lead.companyId}&line={encodeURIComponent(lead.serviceLine)}">{lead.company}</a
										>
										<span class="type-sub">{lead.domain ? host(lead.domain) : '—'} · {lead.serviceLine}</span>
									</td>
									<td class="num text-lg">
										{lead.probability === null ? '—' : fit(lead.probability)}
									</td>
									<td class="text-center max-compact:hidden">
										{#if lead.band}<Tag band={lead.band}>{lead.band}</Tag>{:else}—{/if}
									</td>
									<td class="max-compact:hidden">
										{#if lead.lastContactedAt}
											<span class="num">{age(daysSince(lead.lastContactedAt))}</span>
											<span class="type-sub">{plural(lead.contactCount, 'contact')}{lead.channel ? ` · ${lead.channel}` : ''}</span>
										{:else}
											<span class="type-sub text-ink-4">not contacted yet</span>
										{/if}
									</td>
									<td class="max-lg:hidden">
										{#if lead.outreachSubject}
											<span class="line-clamp-2 font-display text-base italic text-ink-2">“{lead.outreachSubject}”</span>
										{:else}
											<span class="type-sub text-ink-4">no draft yet</span>
										{/if}
									</td>
									<td>
										<div class="flex items-center justify-end gap-1">
											{#if NEXT[lead.status]}
												<Button
													size="sm"
													disabled={busy === lead.id}
													title={`Mark ${stageLabel(NEXT[lead.status])}`}
													onclick={() => move(lead, NEXT[lead.status])}
												>
													{#if busy === lead.id}<Spinner />{:else}<Icon name="chevronRight" size="sm" />{/if}
													{stageLabel(NEXT[lead.status])}
												</Button>
											{/if}
											<Menu
												items={menuFor(lead)}
												onselect={(id) => runRow(lead, id)}
												size="sm"
												label={`Actions for ${lead.company}`}
											/>
										</div>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				</section>
			{/each}
			{#if matched.length === 0}
				<Note>Nothing in the pipeline matches “{query}”.</Note>
			{/if}
		</div>
	{/if}
</div>

<style>
	.panes {
		position: relative;
		display: block;
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}

	.stage + .stage {
		border-top: 1px solid var(--ink);
	}

	.stage-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-3);
		padding: var(--sp-2) var(--sp-4);
		border-bottom: 1px solid var(--rule);
		background: var(--paper);
		position: sticky;
		top: 0;
		z-index: var(--z-sticky);
	}

	.stage[data-stage='QUALIFIED'] .stage-head {
		box-shadow: inset 3px 0 0 var(--accent);
	}

	.stage[data-stage='REJECTED'] .stage-head {
		box-shadow: inset 3px 0 0 var(--oxblood);
	}

	.stage[data-stage='NEW'] .stage-head,
	.stage[data-stage='CONTACTED'] .stage-head,
	.stage[data-stage='REPLIED'] .stage-head {
		box-shadow: inset 3px 0 0 var(--orange);
	}

	.c-co {
		width: 30%;
	}

	.co {
		display: block;
		font-family: var(--font-display);
		font-size: var(--fs-lg);
		font-weight: 500;
		color: var(--ink);
		line-height: 1.2;
	}

	.co:hover {
		text-decoration: underline;
	}

	.sub {
		display: block;
		font-size: var(--fs-sm);
		color: var(--ink-3);
		margin-top: 1px;
	}

	.c-fit {
		width: 68px;
		font-size: var(--fs-lg);
	}

	.c-band {
		width: 96px;
	}

	.c-contact {
		width: 150px;
	}

	.c-subject {
		width: auto;
	}

	.subject {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		font-family: var(--font-display);
		font-size: var(--fs-base);
		font-style: italic;
		color: var(--ink-2);
	}

	.c-act {
		width: 156px;
	}

	.row-tools {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: var(--sp-1);
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		width: 280px;
		padding-left: var(--sp-2);
		border: 1px solid var(--rule-strong);
		border-radius: 2px;
		background: var(--surface);
		color: var(--ink-3);
	}

	.search:focus-within {
		border-color: var(--orange);
		box-shadow: 0 0 0 2px var(--orange-wash);
	}

	.search .input {
		border: 0;
		background: transparent;
		padding-left: 0;
	}

	.search .input:focus {
		box-shadow: none;
	}

	@media (max-width: 760px) {
		.metrics {
			flex: 1 1 100%;
			padding-top: var(--sp-2);
			border-top: 1px solid var(--rule);
		}
		.toolbar {
			flex-wrap: wrap;
		}
		.search {
			width: 100%;
		}
		.panes table.grid {
			min-width: 520px;
		}
		.panes {
			overflow-x: auto;
		}
	}

	@media (max-width: 1180px) {
		.c-subject {
			display: none;
		}
	}

	@media (max-width: 1000px) {
		.c-band,
		.c-contact {
			display: none;
		}
		.c-act {
			width: 130px;
		}
	}
</style>
