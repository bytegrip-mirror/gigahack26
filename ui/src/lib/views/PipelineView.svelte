<script lang="ts">
	import { fetchLeads, fetchLeadTally, recordLead, type LeadTally, type LeadView } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { age, fit, host, plural } from '$lib/format';
	import { copy, csv, save } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import type { MenuItem } from '$lib/components/menu';

	const STAGES = [
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

	const serviceLine = $derived(session.serviceLine);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		Promise.all([fetchLeads(line), fetchLeadTally(line)])
			.then(([list, counts]) => {
				if (cancelled) return;
				leads = list;
				tally = counts;
			})
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
			await recordLead({ companyId: lead.companyId, serviceLine, status });
			leads = await fetchLeads(serviceLine);
			tally = await fetchLeadTally(serviceLine);
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
		if (id === 'open') window.location.assign(`/?co=${lead.companyId}`);
		else if (id === 'link') {
			const ok = await copy(`${window.location.origin}/?co=${lead.companyId}`);
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
			`pipeline-${serviceLine.toLowerCase().replace(/\s+/g, '-')}.csv`,
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
				{plural(leads.length, 'company', 'companies')} worked on {serviceLine}
			</p>
		</div>

		{#if tally}
			<div class="metrics">
				<Metric value={tally.CONTACTED} label="Contacted" />
				<Metric value={tally.REPLIED} label="Replied" tone={tally.REPLIED > 0 ? 'hot' : null} />
				<Metric value={tally.QUALIFIED} label="Qualified" tone={tally.QUALIFIED > 0 ? 'good' : null} />
				<Metric value={tally.REJECTED} label="Rejected" />
			</div>
		{/if}

	</header>

	<div class="toolbar">
		<label class="search">
			<span class="sr-only">Filter leads</span>
			<Icon name="search" size={14} />
			<input class="input input-sm" placeholder="Company or subject" bind:value={query} />
		</label>
		<span class="spacer"></span>
		<span class="sub dim num">{matched.length} of {leads.length} shown</span>
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
			<h2 class="dsp">Nothing has been worked yet</h2>
			<p class="sub">
				Open a company on the board and set a stage, or generate an outreach draft — which marks the company
				contacted for you. Everything you touch lands here with its evidence still attached.
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
						<table class="grid">
							<thead>
								<tr>
									<th scope="col" class="c-co">Company</th>
									<th scope="col" class="c-fit">Fit</th>
									<th scope="col" class="c-band">Band</th>
									<th scope="col" class="c-contact">Last contact</th>
									<th scope="col" class="c-subject">Draft subject</th>
									<th scope="col" class="c-act"><span class="sr-only">Actions</span></th>
								</tr>
							</thead>
							<tbody>
								{#each stage.rows as lead (lead.id)}
									<tr>
										<td class="c-co">
											<a class="co" href="/?co={lead.companyId}">{lead.company}</a>
											<span class="sub">{lead.domain ? host(lead.domain) : '—'} · {lead.serviceLine}</span>
										</td>
										<td class="c-fit num">
											{lead.probability === null ? '—' : fit(lead.probability)}
										</td>
										<td class="c-band m">
											{#if lead.band}<Tag band={lead.band}>{lead.band}</Tag>{:else}—{/if}
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
			{#if matched.length === 0}
				<p class="note">Nothing in the pipeline matches “{query}”.</p>
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
