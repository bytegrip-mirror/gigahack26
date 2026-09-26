<script lang="ts">
	import { fetchLeads, fetchLeadTally, recordLead, type LeadTally, type LeadView } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { age, fit, fitTone, fitWord, host, plural } from '$lib/format';
	import { copy, csv, save } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import FitBar from '$lib/components/FitBar.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import type { MenuItem } from '$lib/components/menu';

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
	let boardId = $state<'work' | 'backlog' | 'all'>('work');
	let sort = $state('contact');

	const ALL_LINES = '__all__';
	const serviceLine = $derived(session.serviceLine);
	const thresholds = $derived({
		watch: workspace.meta?.scoring.watchThreshold ?? 0.28,
		nurture: workspace.meta?.scoring.nurtureThreshold ?? 0.45,
		priority: workspace.meta?.scoring.priorityThreshold ?? 0.55
	});
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

	const BOARDS = [
		{ id: 'work', label: 'In work', title: 'Leads somebody has already worked', stages: ['CONTACTED', 'REPLIED', 'QUALIFIED'] },
		{ id: 'backlog', label: 'Backlog', title: 'Worked and put down, and never picked up', stages: ['NEW', 'REJECTED'] },
		{ id: 'all', label: 'Everything', title: 'Every lead, whatever stage', stages: STAGES.map((s) => s.id) }
	] as const;

	const SORTS = [
		{ id: 'contact', label: 'Most recently worked' },
		{ id: 'name', label: 'Company, A to Z' },
		{ id: 'fit', label: 'Best fit' },
		{ id: 'stage', label: 'Furthest along' }
	];

	const board = $derived(BOARDS.find((entry) => entry.id === boardId) ?? BOARDS[0]);
	const STAGE_ORDER: Record<string, number> = { NEW: 0, CONTACTED: 1, REPLIED: 2, QUALIFIED: 3, REJECTED: 4 };

	function ordered(rows: LeadView[]): LeadView[] {
		const list = [...rows];
		const sign = sort === 'name' ? 1 : -1;
		list.sort((a, b) => {
			if (sort === 'name') return a.company.localeCompare(b.company);
			if (sort === 'fit') return sign * ((a.probability ?? 0) - (b.probability ?? 0));
			if (sort === 'stage')
				return sign * ((STAGE_ORDER[a.status] ?? 9) - (STAGE_ORDER[b.status] ?? 9)) || a.company.localeCompare(b.company);
			return (
				sign *
					((daysSince(a.lastContactedAt) ?? Number.POSITIVE_INFINITY) -
						(daysSince(b.lastContactedAt) ?? Number.POSITIVE_INFINITY)) || a.company.localeCompare(b.company)
			);
		});
		return list;
	}

	const grouped = $derived(
		STAGES.filter((stage) => (board.stages as readonly string[]).includes(stage.id)).map((stage) => ({
			...stage,
			rows: ordered(matched.filter((lead) => lead.status === stage.id))
		}))
	);

	const onBoard = $derived(BOARDS.map((entry) => ({
		id: entry.id,
		label: entry.label,
		count: matched.filter((lead) => (entry.stages as readonly string[]).includes(lead.status)).length
	})));

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
				{plural(leads.length, 'company', 'companies')} in the pipeline{#if allLines}{' '}
					across every service line{:else}{' '}
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
			</div>
		{/if}

	</header>

	<div class="toolbar">
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
		{#if workspace.switching}<span class="spinner"></span>{/if}
		<span class="sep"></span>

		<div class="seg" role="tablist" aria-label="Which leads">
			{#each onBoard as entry (entry.id)}
				<button
					role="tab"
					aria-selected={boardId === entry.id}
					title={BOARDS.find((b) => b.id === entry.id)?.title}
					onclick={() => (boardId = entry.id as 'work' | 'backlog' | 'all')}
				>
					{entry.label}
					<span class="num">{entry.count}</span>
				</button>
			{/each}
		</div>

		<span class="sep"></span>

		<label class="search">
			<span class="sr-only">Filter leads</span>
			<Icon name="search" size={14} />
			<input class="input input-sm" placeholder="Company or subject" bind:value={query} />
		</label>
		<span class="spacer"></span>
		<Menu
			text={SORTS.find((entry) => entry.id === sort)?.label ?? 'Sort'}
			icon="sort"
			size="sm"
			align="end"
			label="Sort the leads"
			items={SORTS.map((entry) => ({ id: entry.id, label: entry.label, checked: sort === entry.id }))}
			onselect={(id) => (sort = id)}
		/>
		<Menu
			items={[
				{ id: 'export', label: 'Export the pipeline as CSV', icon: 'download' },
				{ id: 'board', label: 'Open the board', icon: 'board' }
			]}
			onselect={(id) => (id === 'export' ? exportAll() : window.location.assign('/'))}
			label="More pipeline actions"
		/>
	</div>

	{#if loading && leads.length === 0}
		<p class="note"><span class="spinner"></span> Reading the pipeline</p>
	{:else if leads.length === 0}
		<div class="empty">
			<Icon name="pipeline" size={22} />
			<h2 class="dsp">Nothing is in the pipeline yet</h2>
			<p class="sub">
				Open a company on the board and push it to HubSpot, or generate an outreach draft — either one lands
				here as New, with its evidence still attached.
			</p>
			<a class="btn btn-ink" href="/">Go to the board</a>
		</div>
	{:else}
		<div class="panes scroll">
			<LoadingVeil show={loading} label="Reading the pipeline" />
			{#each grouped as stage (stage.id)}
				{#if stage.rows.length > 0}
					<section class="stage" data-stage={stage.id}>
						<header class="stage-head">
							<h2 class="label">{stage.label}</h2>
							<span class="num">{stage.rows.length}</span>
						</header>
						<table class="tbl">
							<thead>
								<tr>
									<th scope="col" class="c-co">Company</th>
									<th scope="col" class="c-fit">Fit</th>
									<th scope="col" class="c-contact">Last contact</th>
									<th scope="col" class="c-subject">Draft subject</th>
									<th scope="col" class="c-act"><span class="sr-only">Actions</span></th>
								</tr>
							</thead>
							<tbody>
								{#each stage.rows as lead (lead.id)}
									<tr>
										<td class="c-co">
											<a class="co" href="/?co={lead.companyId}&line={encodeURIComponent(lead.serviceLine)}">{lead.company}</a>
											<span class="sub">{lead.domain ? host(lead.domain) : '—'} · {lead.serviceLine}</span>
										</td>
										<td class="c-fit">
											{#if lead.probability === null}
												<span class="sub dim">—</span>
											{:else if lead.band === 'DISQUALIFIED'}
												<Tag band={lead.band}>Out of the queue</Tag>
											{:else if lead.band === 'THIN'}
												<span class="fitval dsp" data-tone="weak">Unproven</span>
											{:else}
												<span class="fitval dsp" data-tone={fitTone(lead.probability, thresholds)}
													>{fitWord(lead.probability, thresholds)}</span
												>
												<FitBar probability={lead.probability} tone={fitTone(lead.probability, thresholds)} />
											{/if}
										</td>
										<td class="c-contact">
											{#if lead.lastContactedAt}
												<span class="num">{age(daysSince(lead.lastContactedAt))}</span>
												<span class="sub">{plural(lead.contactCount, 'contact')}{lead.channel ? ` · ${lead.channel}` : ''}</span>
											{:else}
												<span class="sub dim">not contacted yet</span>
											{/if}
										</td>
										<td class="c-subject">
											{#if lead.outreachSubject}
												<span class="subject">“{lead.outreachSubject}”</span>
											{:else}
												<span class="sub dim">no draft yet</span>
											{/if}
										</td>
										<td class="c-act">
											<div class="row-tools">
												{#if NEXT[lead.status]}
													<button
														class="btn btn-sm"
														disabled={busy === lead.id}
														title={`Mark ${stageLabel(NEXT[lead.status])}`}
														onclick={() => move(lead, NEXT[lead.status])}
													>
														{#if busy === lead.id}<span class="spinner"></span>{:else}<Icon name="chevronRight" size={13} />{/if}
														{stageLabel(NEXT[lead.status])}
													</button>
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
				{/if}
			{/each}
			{#if grouped.every((stage) => stage.rows.length === 0)}
				<p class="note">
					{#if query}
						Nothing matches “{query}”.
					{:else if boardId === 'work'}
						Nobody is in work on this service line. Leads land here once you have contacted or replied to one.
					{:else if boardId === 'backlog'}
						Nothing has been put down.
					{:else}
						No lead has been worked on this service line yet.
					{/if}
				</p>
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
		border-top: 1px solid var(--color-ink);
	}

	.stage-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--spacing-3);
		padding: var(--spacing-2) var(--spacing-4);
		border-bottom: 1px solid var(--color-rule);
		background: var(--color-paper);
		position: sticky;
		top: 0;
		z-index: var(--z-index-sticky);
	}

	.stage[data-stage='QUALIFIED'] .stage-head {
		box-shadow: inset 3px 0 0 var(--color-accent);
	}

	.stage[data-stage='REJECTED'] .stage-head {
		box-shadow: inset 3px 0 0 var(--color-oxblood);
	}

	.stage[data-stage='NEW'] .stage-head,
	.stage[data-stage='CONTACTED'] .stage-head,
	.stage[data-stage='REPLIED'] .stage-head {
		box-shadow: inset 3px 0 0 var(--color-orange);
	}

	.c-co {
		width: 30%;
	}

	.co {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-lg);
		font-weight: 500;
		color: var(--color-ink);
		line-height: 1.2;
	}

	.co:hover {
		text-decoration: underline;
	}

	.sub {
		display: block;
		font-size: var(--text-sm);
		color: var(--color-ink-3);
		margin-top: 1px;
	}

	.fitval {
		display: block;
		font-size: var(--text-base);
		line-height: 1.2;
		color: var(--color-ink-2);
	}

	.fitval[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.fitval[data-tone='weak'] {
		color: var(--color-ink-3);
	}

	.c-fit {
		width: 118px;
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
		font-size: var(--text-base);
		font-style: italic;
		color: var(--color-ink-2);
	}

	.c-act {
		width: 156px;
	}

	.row-tools {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: var(--spacing-1);
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		width: 280px;
		padding-left: var(--spacing-2);
		border: 1px solid var(--color-rule-strong);
		border-radius: 2px;
		background: var(--color-surface);
		color: var(--color-ink-3);
	}

	.search:focus-within {
		border-color: var(--color-orange);
		box-shadow: 0 0 0 2px var(--color-orange-wash);
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
			padding-top: var(--spacing-2);
			border-top: 1px solid var(--color-rule);
		}
		.toolbar {
			flex-wrap: wrap;
		}
		.search {
			width: 100%;
		}
		.panes table.tbl {
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
		.c-contact {
			display: none;
		}
		.c-act {
			width: 130px;
		}
	}</style>
