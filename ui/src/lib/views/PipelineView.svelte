<script lang="ts">
	import { fetchLeads, fetchLeadTally, recordLead, type LeadTally, type LeadView } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { age, fit, host, plural } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';

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

	const grouped = $derived(
		STAGES.map((stage) => ({
			...stage,
			rows: leads.filter((lead) => lead.status === stage.id)
		}))
	);

	function daysSince(iso: string | null): number | null {
		return iso ? Math.max(0, (Date.now() - new Date(iso).getTime()) / 86_400_000) : null;
	}

	async function advance(lead: LeadView) {
		const next = NEXT[lead.status];
		if (!next) return;
		busy = lead.id;
		try {
			await recordLead({ companyId: lead.companyId, serviceLine, status: next });
			leads = await fetchLeads(serviceLine);
			tally = await fetchLeadTally(serviceLine);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = null;
		}
	}
</script>

<div class="view">
	<header class="topbar">
		<div>
			<h1 class="dsp">Pipeline</h1>
			<p class="label">
				Everything this team has touched, and where it stands. Advance a stage here; the board follows.
			</p>
		</div>
		{#if tally}
			<div class="tally">
				<span><span class="num">{tally.CONTACTED}</span> <span class="label">contacted</span></span>
				<span><span class="num">{tally.REPLIED}</span> <span class="label">replied</span></span>
				<span><span class="num">{tally.QUALIFIED}</span> <span class="label">qualified</span></span>
				<span><span class="num">{tally.REJECTED}</span> <span class="label">rejected</span></span>
			</div>
		{/if}
	</header>

	{#if loading && leads.length === 0}
		<p class="note"><span class="spinner"></span> Reading the pipeline</p>
	{:else if leads.length === 0}
		<div class="empty">
			<h2 class="dsp">Nothing has been worked yet</h2>
			<p class="sub">
				Open a company on the board and record a stage — or generate an outreach draft, which marks it contacted
				for you. Everything you touch shows up here with the evidence still attached.
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
						<ul>
							{#each stage.rows as lead (lead.id)}
								<li>
									<div class="row-top">
										<a class="name dsp" href="/?co={lead.companyId}">{lead.company}</a>
										{#if lead.probability !== null}
											<span class="fit-col">
												<span class="num">{fit(lead.probability)}</span>
												<Tag band={lead.band}>{lead.band}</Tag>
											</span>
										{/if}
									</div>
									<p class="sub dim">
										{#if lead.domain}{host(lead.domain)} · {/if}{lead.serviceLine}
									</p>
									<p class="sub">
										{#if lead.lastContactedAt}
											{age(daysSince(lead.lastContactedAt))} · {plural(lead.contactCount, 'contact')}
											{#if lead.channel}· {lead.channel}{/if}
										{:else}
											<span class="dim">not contacted yet</span>
										{/if}
									</p>
									{#if lead.outreachSubject}
										<p class="subject">“{lead.outreachSubject}”</p>
									{/if}
									{#if NEXT[lead.status]}
										<button class="btn btn-sm advance" disabled={busy === lead.id} onclick={() => advance(lead)}>
											{#if busy === lead.id}<span class="spinner"></span>{/if}
											Mark {STAGES.find((s) => s.id === NEXT[lead.status])?.label.toLowerCase()}
										</button>
									{/if}
								</li>
							{/each}
						</ul>
					</section>
				{/if}
			{/each}
		</div>
	{/if}
</div>

<style>
	.view {
		display: flex;
		flex-direction: column;
		height: 100%;
		min-height: 0;
	}

	.topbar {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: 20px;
		padding: 11px 16px 12px;
		border-bottom: 1px solid var(--ink);
		background: var(--surface);
		flex-wrap: wrap;
	}

	h1 {
		font-size: 23px;
		line-height: 1.1;
	}

	.topbar .label {
		margin-top: 3px;
		max-width: 64ch;
	}

	.tally {
		display: flex;
		gap: 16px;
	}

	.tally > span {
		display: flex;
		align-items: baseline;
		gap: 4px;
	}

	.tally .num {
		font-size: 16px;
		font-weight: 500;
	}

	.panes {
		position: relative;
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
		align-content: start;
	}

	.stage {
		border-right: 1px solid var(--rule);
		border-bottom: 1px solid var(--rule);
	}

	.stage-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
		padding: 10px 14px;
		border-bottom: 1px solid var(--rule);
		background: var(--paper);
		position: sticky;
		top: 0;
		z-index: 1;
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

	.stage ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.stage li {
		padding: 11px 14px 13px;
		border-bottom: 1px solid var(--rule);
		display: flex;
		flex-direction: column;
		gap: 2px;
		align-items: flex-start;
	}

	.row-top {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 10px;
	}

	.name {
		font-size: 15px;
		color: var(--ink);
	}

	.name:hover {
		text-decoration: underline;
	}

	.sub {
		font-size: 11.5px;
		line-height: 1.4;
	}

	.fit-col {
		display: flex;
		align-items: center;
		gap: 6px;
		flex: none;
	}

	.fit-col .num {
		font-size: 14px;
	}

	.advance {
		margin-top: 8px;
	}

	.subject {
		margin-top: 6px;
		font-family: var(--font-display);
		font-size: 12.5px;
		font-style: italic;
		color: var(--ink-3);
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 18px 16px;
		font-size: 13px;
		color: var(--ink-3);
	}

	.empty {
		margin: 40px auto;
		max-width: 520px;
		padding: 22px;
		border: 1px solid var(--rule);
		display: flex;
		flex-direction: column;
		gap: 10px;
		align-items: flex-start;
	}

	.empty h2 {
		font-size: 22px;
	}
</style>
