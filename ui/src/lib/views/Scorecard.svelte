<script lang="ts">
	import {
		fetchCompany,
		pushToHubspot,
		recordLead,
		sendFeedback,
		type CompanyDetail,
		type LedgerRow,
		type LeadView
	} from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { age, date, fit, host, nats, plural, signed, sourceKind, theme, tier } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';
	import ScoreMeter from '$lib/components/ScoreMeter.svelte';
	import Ledger from '$lib/components/Ledger.svelte';
	import OutreachModal from '$lib/components/OutreachModal.svelte';

	let {
		companyId,
		serviceLine,
		onclose,
		oncapture
	}: {
		companyId: string;
		serviceLine: string;
		onclose: () => void;
		oncapture: (evidenceId: string, focus: { start: number; end: number } | null) => void;
	} = $props();

	const STAGES = [
		{ id: 'NEW', label: 'New' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const TABS = [
		{ id: 'signals', label: 'Top signals' },
		{ id: 'ledger', label: 'Ledger' },
		{ id: 'documents', label: 'Documents' }
	];

	let detail = $state<CompanyDetail | null>(null);
	let error = $state<string | null>(null);
	let loading = $state(true);
	let tab = $state('signals');
	let outreachOpen = $state(false);
	let silentOnly = $state(false);
	let lead = $state<LeadView | null>(null);
	let busy = $state<string | null>(null);

	$effect(() => {
		const id = companyId;
		const line = serviceLine;
		if (!id || !line) return;
		let cancelled = false;
		loading = true;
		fetchCompany(id, line)
			.then((result) => {
				if (cancelled) return;
				detail = result;
				lead = result.lead;
				error = null;
			})
			.catch((cause) => {
				if (!cancelled) error = cause.message;
			})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	const scored = $derived(detail ? detail.ledger.filter((row) => !row.blocking) : []);
	const silent = $derived(detail ? detail.evidence.filter((item) => item.signalCount === 0) : []);
	const documents = $derived(detail ? (silentOnly ? silent : detail.evidence) : []);
	const stage = $derived(lead?.status ?? 'NEW');
	const counters = $derived(detail?.whyNow?.counters ?? []);

	function daysSince(iso: string): number {
		return Math.max(0, (Date.now() - new Date(iso).getTime()) / 86_400_000);
	}

	async function setStage(status: string) {
		if (!detail) return;
		busy = 'stage';
		try {
			lead = await recordLead({ companyId: detail.id, serviceLine, status });
			toasts.push(`Marked ${lead.statusLabel.toLowerCase()}.`, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = null;
		}
	}

	async function pushCrm() {
		if (!detail) return;
		busy = 'crm';
		try {
			const result = await pushToHubspot(detail.id, serviceLine);
			if (result.pushed) {
				toasts.push('Pushed to HubSpot.', 'success');
			} else {
				toasts.push(`HubSpot is not connected — ${result.reason}`, 'warn');
			}
			lead = await recordLead({ companyId: detail.id, serviceLine });
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = null;
		}
	}

	async function judge(value: 'ACCEPT' | 'REJECT') {
		if (!detail) return;
		try {
			await sendFeedback({ companyId: detail.id, repVerdict: value });
			toasts.push(value === 'ACCEPT' ? 'Counted as a qualified lead.' : 'Counted as rejected.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	function openRow(row: LedgerRow) {
		oncapture(row.evidenceId, { start: row.spanStart, end: row.spanEnd });
	}
</script>

<div class="card">
	<header class="head">
		<div class="head-main">
			<h2 class="dsp">{detail?.name ?? 'Loading'}</h2>
			{#if detail}
				<p class="sub">
					{#if detail.domain}<a href="https://{detail.domain}" target="_blank" rel="noreferrer noopener">{detail.domain}</a> · {/if}{detail.hqCity
						? `${detail.hqCity}, `
						: ''}{detail.country ?? 'unknown'} · {detail.industry ?? 'unclassified'} ·
					{detail.sizeBand ?? 'unknown size'}
				</p>
			{/if}
		</div>
		<button class="btn btn-quiet btn-sm" onclick={onclose} aria-label="Close detail">✕</button>
	</header>

	{#if error}
		<p class="note warn">{error}</p>
	{:else if loading && !detail}
		<div class="veil"><span class="spinner"></span><span>Reading the scorecard</span></div>
	{:else if detail}
		<section class="block score">
			<div class="score-row">
				<span class="num score-val">{fit(detail.probability)}</span>
				<div class="score-meta">
					<Tag band={detail.band}>{detail.bandLabel}</Tag>
					<span class="label">{detail.signalCount} weighted · {detail.themeCount} themes</span>
				</div>
			</div>
			<ScoreMeter
				probability={detail.probability}
				band={detail.band}
				baseRate={detail.baseRate}
				thresholds={{
					watch: detail.watchThreshold,
					nurture: detail.nurtureThreshold,
					priority: detail.priorityThreshold
				}}
				showScale
			/>
			{#if detail.gateReason}
				<p class="gate"><span class="label">Removed</span> {detail.gateReason}</p>
			{/if}
		</section>

		<section class="block why" data-urgency={detail.whyNow.urgency}>
			<div class="why-head">
				<p class="label">Why now</p>
				<span class="urgency" data-urgency={detail.whyNow.urgency}>{detail.whyNow.urgencyLabel}</span>
			</div>
			<p class="why-headline dsp">{detail.whyNow.headline}</p>
			<p class="why-text">{detail.whyNow.narrative}</p>
		</section>

		<section class="block actions">
			<div class="seg" role="tablist" aria-label="Lead stage">
				{#each STAGES as option (option.id)}
					<button
						role="tab"
						aria-selected={stage === option.id}
						disabled={busy === 'stage'}
						onclick={() => setStage(option.id)}
					>
						{option.label}
					</button>
				{/each}
			</div>
			{#if lead && lead.contactCount > 0}
				<p class="sub dim">
					{plural(lead.contactCount, 'contact')}
					{#if lead.lastContactedAt}· last {age(daysSince(lead.lastContactedAt))}{/if}
					{#if lead.channel}· via {lead.channel}{/if}
				</p>
			{/if}
			<div class="row">
				<button class="btn btn-ink" onclick={() => (outreachOpen = true)}>Generate outreach</button>
				<button class="btn" disabled={busy === 'crm'} onclick={pushCrm}>
					{#if busy === 'crm'}<span class="spinner"></span>{/if}
					{lead?.crmSyncedAt ? 'Re-push' : 'Push to HubSpot'}
				</button>
			</div>
			<div class="row quiet">
				<button class="btn btn-quiet btn-sm" onclick={() => judge('ACCEPT')}>Mark qualified</button>
				<button class="btn btn-quiet btn-sm" onclick={() => judge('REJECT')}>Not a lead</button>
			</div>
		</section>

		<section class="block summary">
			<div class="pair">
				<div class="col">
					<p class="label">Top signals · {detail.whyNow.triggers.length}</p>
					<ul>
						{#each detail.whyNow.triggers as row (row.evidenceId + row.spanStart)}
							<li>
								<button class="line" onclick={() => openRow(row)}>
									<span class="dot pos"></span>
									<span class="line-text">{row.questionTitle}</span>
									<span class="num">{signed(row.contributionNats, 2)}</span>
								</button>
							</li>
						{:else}
							<li><span class="sub dim">none above the floor</span></li>
						{/each}
					</ul>
				</div>
				<div class="col against">
					<p class="label">Counter signals · {counters.length}</p>
					<ul>
						{#each counters as row (row.evidenceId + row.spanStart)}
							<li>
								<button class="line" onclick={() => openRow(row)}>
									<span class="dot neg"></span>
									<span class="line-text">{row.questionTitle}</span>
									<span class="num">{signed(row.contributionNats, 2)}</span>
								</button>
							</li>
						{:else}
							<li><span class="sub dim">nothing argues against</span></li>
						{/each}
					</ul>
				</div>
			</div>
		</section>

		<div class="tabs" role="tablist" aria-label="Company detail">
			{#each TABS as option (option.id)}
				<button role="tab" aria-selected={tab === option.id} onclick={() => (tab = option.id)}>
					{option.label}
					{#if option.id === 'ledger'}<span class="num">{scored.length}</span>{/if}
					{#if option.id === 'documents'}<span class="num">{detail.evidence.length}</span>{/if}
				</button>
			{/each}
		</div>

		<div class="tab-body">
			{#if tab === 'signals'}
				{#each [...detail.whyNow.triggers, ...counters] as row (row.evidenceId + row.spanStart)}
					<article class="sig" class:against={row.contributionNats < 0}>
						<div class="sig-head">
							<span class="sig-title">{row.questionTitle}</span>
							<span class="num" class:pos={row.contributionNats > 0} class:neg={row.contributionNats < 0}>
								{signed(row.contributionNats)}
							</span>
						</div>
						{#if row.spanText}<p class="quote">“{row.spanText}”</p>{/if}
						<p class="sub dim">
							{row.questionText}
						</p>
						<p class="sub dim">
							{row.publisher} · {age(row.ageDays)} · {theme(row.theme)} · {tier(row.tier)}
							<button class="link" onclick={() => openRow(row)}>open capture</button>
						</p>
					</article>
				{/each}
				{#if detail.whyNow.triggers.length === 0 && counters.length === 0}
					<p class="sub dim">No signal cleared the confidence floor for this policy.</p>
				{/if}
			{/if}

			{#if tab === 'ledger'}
				<Ledger rows={scored} onopen={openRow} />
				<div class="sum num">
					<div><span>logit({detail.baseRate.toFixed(2)})</span><span>{signed(detail.baseLogit)}</span></div>
					{#each scored as row (row.evidenceId + row.questionId + row.spanStart)}
						<div class="dim">
							<span>{row.questionTitle}{row.themeCapped ? ' · capped' : ''}</span>
							<span>{signed(row.contributionNats)}</span>
						</div>
					{/each}
					<div class="total">
						<span>logit → fit</span><span>{signed(detail.logit)} → {fit(detail.probability)}</span>
					</div>
				</div>
				<div class="themes">
					{#each detail.themes as item (item.name)}
						<div class="theme">
							<div class="theme-row">
								<span>{theme(item.name)}</span>
								<span class="num">{signed(item.total)}</span>
							</div>
							<div class="bar">
								<span
									data-capped={item.capped}
									style="width:{Math.min(100, (Math.abs(item.total) / detail.themeCap) * 100)}%"
								></span>
							</div>
						</div>
					{/each}
					<p class="sub dim">Cap {nats(detail.themeCap)}, derived from the Priority line.</p>
				</div>
			{/if}

			{#if tab === 'documents'}
				<label class="toggle">
					<input type="checkbox" bind:checked={silentOnly} />
					<span>Only the {silent.length} that produced nothing</span>
				</label>
				{#each documents as item (item.id)}
					<button class="doc" onclick={() => oncapture(item.id, null)}>
						<span class="doc-top">
							<span class="label">{sourceKind(item.sourceKind)}</span>
							<span class="num dim">{date(item.publishedAt ?? item.observedAt)}</span>
						</span>
						<span class="doc-title">{item.title ?? host(item.sourceUrl)}</span>
						<span class="sub dim">
							{item.publisher} · {plural(item.charLength, 'char')}
							{#if item.signalCount > 0}· {plural(item.signalCount, 'signal')}{:else}· no signal{/if}
						</span>
					</button>
				{/each}
			{/if}
		</div>
	{/if}
</div>

{#if outreachOpen && detail}
	<OutreachModal
		companyId={detail.id}
		companyName={detail.name}
		{serviceLine}
		onclose={() => (outreachOpen = false)}
		ondrafted={(subject) => {
			const id = detail?.id;
			if (!id) return;
			recordLead({ companyId: id, serviceLine, status: 'CONTACTED', outreachSubject: subject })
				.then((saved) => (lead = saved))
				.catch(() => {});
		}}
	/>
{/if}

<style>
	.card {
		display: flex;
		flex-direction: column;
		height: 100%;
		min-height: 0;
	}

	.head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 10px;
		padding: 13px 15px;
		border-bottom: 1px solid var(--ink);
	}

	h2 {
		font-size: 20px;
		line-height: 1.15;
	}

	.sub {
		font-size: 11.5px;
		line-height: 1.45;
		color: var(--ink-3);
		margin-top: 3px;
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 16px 15px;
		font-size: 13px;
		color: var(--ink-3);
	}

	.note.warn {
		color: var(--oxblood);
	}

	.veil {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 9px;
		background: var(--paper);
		color: var(--ink-3);
		font-size: 13px;
	}

	.block {
		padding: 13px 15px;
		border-bottom: 1px solid var(--rule);
		flex: none;
	}

	.score-row {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-bottom: 8px;
	}

	.score-val {
		font-family: var(--font-display);
		font-size: 34px;
		font-weight: 600;
		line-height: 1;
		letter-spacing: -0.03em;
	}

	.score-meta {
		display: flex;
		flex-direction: column;
		gap: 4px;
		align-items: flex-start;
	}

	.gate {
		margin-top: 9px;
		font-size: 12px;
		line-height: 1.45;
		color: var(--oxblood);
	}

	.why {
		background: var(--paper);
		border-left: 2px solid var(--rule-strong);
	}

	.why[data-urgency='ACT_NOW'] {
		background: var(--orange-wash);
		border-left-color: var(--orange);
	}

	.why[data-urgency='THIS_WEEK'] {
		border-left-color: var(--orange-line);
	}

	.why-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
	}

	.urgency {
		font-size: 10px;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.09em;
		color: var(--ink-3);
	}

	.urgency[data-urgency='ACT_NOW'] {
		color: var(--orange-ink);
	}

	.urgency[data-urgency='ACT_NOW']::before,
	.urgency[data-urgency='THIS_WEEK']::before {
		content: '● ';
		color: var(--orange);
	}

	.why-headline {
		margin-top: 6px;
		font-size: 15px;
		line-height: 1.3;
		color: var(--ink);
	}

	.why-text {
		margin-top: 6px;
		font-size: 12.5px;
		line-height: 1.6;
		color: var(--ink-2);
	}

	.actions {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.actions .seg {
		width: 100%;
	}

	.actions .seg button {
		flex: 1;
		text-align: center;
		padding: 4px 6px;
		font-size: 11.5px;
	}

	.row {
		display: flex;
		gap: 6px;
	}

	.row .btn {
		flex: 1;
	}

	.row.quiet .btn {
		border-color: transparent;
		color: var(--ink-3);
	}

	.summary {
		padding: 12px 15px;
	}

	.pair {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0;
	}

	.col {
		min-width: 0;
	}

	.col + .col {
		border-left: 1px solid var(--rule);
		padding-left: 12px;
		margin-left: 12px;
	}

	.col ul {
		list-style: none;
		margin: 6px 0 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.line {
		display: flex;
		align-items: baseline;
		gap: 6px;
		width: 100%;
		text-align: left;
		padding: 2px 0;
		font-size: 12px;
		color: var(--ink-2);
		line-height: 1.35;
	}

	.line:hover .line-text {
		color: var(--orange-deep);
		text-decoration: underline;
		text-underline-offset: 2px;
	}

	.dot {
		width: 5px;
		height: 5px;
		flex: none;
		transform: translateY(-1px);
	}

	.dot.pos {
		background: var(--accent);
	}

	.dot.neg {
		background: var(--oxblood);
	}

	.line-text {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.line .num {
		flex: none;
		font-size: 11px;
		color: var(--ink-4);
	}

	.tabs {
		display: flex;
		border-bottom: 1px solid var(--ink);
		background: var(--surface);
		flex: none;
	}

	.tabs button {
		display: flex;
		align-items: center;
		gap: 5px;
		padding: 8px 13px;
		font-size: 12.5px;
		color: var(--ink-3);
		border-bottom: 2px solid transparent;
		margin-bottom: -1px;
		transition: color var(--ease), border-color var(--ease);
	}

	.tabs button:hover {
		color: var(--ink);
	}

	.tabs button[aria-selected='true'] {
		color: var(--ink);
		font-weight: 500;
		border-bottom-color: var(--orange);
	}

	.tabs .num {
		font-size: 10.5px;
		color: var(--ink-4);
	}

	.tab-body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: 13px 15px 20px;
	}

	.sig {
		border-left: 2px solid var(--accent-line);
		padding: 0 0 12px 10px;
		margin-bottom: 12px;
	}

	.sig.against {
		border-left-color: var(--oxblood-line);
	}

	.sig-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 10px;
	}

	.sig-title {
		font-family: var(--font-display);
		font-size: 14px;
		font-weight: 600;
	}

	.pos {
		color: var(--accent);
		font-size: 12px;
	}

	.neg {
		color: var(--oxblood);
		font-size: 12px;
	}

	.quote {
		margin-top: 5px;
		font-size: 12.5px;
		font-style: italic;
		line-height: 1.5;
		color: var(--ink-2);
	}

	.link {
		color: var(--orange-deep);
		text-decoration: underline;
		text-underline-offset: 2px;
	}

	.sum {
		margin-top: 12px;
		border-top: 1px solid var(--rule);
		padding-top: 8px;
		font-size: 11.5px;
	}

	.sum > div {
		display: flex;
		justify-content: space-between;
		gap: 12px;
		padding: 1px 0;
	}

	.sum .total {
		border-top: 1px solid var(--rule);
		margin-top: 5px;
		padding-top: 5px;
		font-weight: 500;
	}

	.themes {
		margin-top: 14px;
		border-top: 1px solid var(--rule);
		padding-top: 10px;
	}

	.theme {
		margin-bottom: 8px;
	}

	.theme-row {
		display: flex;
		justify-content: space-between;
		font-size: 12.5px;
	}

	.bar {
		height: 4px;
		background: var(--sunken);
		margin-top: 3px;
	}

	.bar span {
		display: block;
		height: 100%;
		background: var(--orange);
	}

	.bar span[data-capped='true'] {
		background: var(--ink);
	}

	.toggle {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		color: var(--ink-3);
		margin-bottom: 10px;
	}

	.doc {
		display: flex;
		flex-direction: column;
		gap: 3px;
		width: 100%;
		text-align: left;
		padding: 8px 0;
		border-bottom: 1px solid var(--rule);
		transition: background var(--ease);
	}

	.doc:hover {
		background: var(--paper);
	}

	.doc-top {
		display: flex;
		justify-content: space-between;
		gap: 10px;
	}

	.doc-top .num {
		font-size: 10.5px;
	}

	.doc-title {
		font-size: 12.5px;
		line-height: 1.35;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
</style>
