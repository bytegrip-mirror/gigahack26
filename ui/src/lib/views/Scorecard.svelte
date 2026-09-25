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
	import {
		age,
		date,
		fit,
		horizon,
		host,
		nats,
		plural,
		signed,
		sourceKind,
		theme,
		tier
	} from '$lib/format';
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

	let detail = $state<CompanyDetail | null>(null);
	let error = $state<string | null>(null);
	let loading = $state(true);
	let outreachOpen = $state(false);
	let ledgerOpen = $state(false);
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

	async function onDrafted(subject: string) {
		if (!detail) return;
		try {
			lead = await recordLead({ companyId: detail.id, serviceLine, status: 'CONTACTED', outreachSubject: subject });
		} catch {
			/* the stage control still works even if this write fails */
		}
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
		<p class="note"><span class="spinner"></span> Reading the scorecard</p>
	{:else if detail}
		<section class="score">
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
			{#if detail.whyNow.daysInBand !== null && detail.whyNow.daysInBand > 0}
				<p class="why-clock">
					<span class="num">{Math.round(detail.whyNow.daysInBand)}</span> days left in the top band without new
					evidence
				</p>
			{/if}
		</section>

		<section class="block actions">
			<div class="stage">
				<p class="label">Stage</p>
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
			</div>

			<div class="row">
				<button class="btn btn-ink" onclick={() => (outreachOpen = true)}>Generate outreach</button>
				<button class="btn" disabled={busy === 'crm'} onclick={pushCrm}>
					{#if busy === 'crm'}<span class="spinner"></span>{/if}
					{lead?.crmSyncedAt ? 'Re-push to HubSpot' : 'Push to HubSpot'}
				</button>
			</div>
			{#if !detail.crmConfigured}
				<p class="sub dim">
					HubSpot is not connected on this instance. The button records the intent and keeps the lead here.
				</p>
			{/if}
			<div class="row quiet">
				<button class="btn btn-quiet btn-sm" onclick={() => judge('ACCEPT')}>Mark qualified</button>
				<button class="btn btn-quiet btn-sm" onclick={() => judge('REJECT')}>Not a lead</button>
			</div>
		</section>

		{#if detail.whyNow.triggers.length > 0}
			<section class="block">
				<h3 class="label">Top signals</h3>
				<ul class="signals">
					{#each detail.whyNow.triggers as row (row.evidenceId + row.questionId + row.spanStart)}
						<li>
							<div class="sig-head">
								<span class="key">{row.questionKey}</span>
								<span class="num pos">{signed(row.contributionNats)}</span>
							</div>
							{#if row.spanText}<p class="quote">“{row.spanText}”</p>{/if}
							<p class="sub dim">
								{row.publisher} · {age(row.ageDays)} · {theme(row.theme)} · {tier(row.tier)}
								<button class="link" onclick={() => openRow(row)}>capture</button>
							</p>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		{#if detail.whyNow.counters.length > 0}
			<section class="block">
				<h3 class="label">Counter signals</h3>
				<ul class="signals">
					{#each detail.whyNow.counters as row (row.evidenceId + row.questionId + row.spanStart)}
						<li class="against">
							<div class="sig-head">
								<span class="key">{row.questionKey}</span>
								<span class="num neg">{signed(row.contributionNats)}</span>
							</div>
							{#if row.spanText}<p class="quote">“{row.spanText}”</p>{/if}
							<p class="sub dim">
								{row.publisher} · {age(row.ageDays)}
								<button class="link" onclick={() => openRow(row)}>capture</button>
							</p>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		<section class="block">
			<div class="block-head">
				<h3 class="label">Themes</h3>
				<span class="sub dim">cap {nats(detail.themeCap)}</span>
			</div>
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
			{#if detail.themes.length === 0}
				<p class="sub dim">No theme contributed; the company sits at the base rate.</p>
			{/if}
		</section>

		<section class="block">
			<div class="block-head">
				<h3 class="label">Decay</h3>
				<span class="sub dim">half-life sensitivity</span>
			</div>
			<dl class="facts">
				<dt>Priority for</dt><dd>{horizon(detail.priorityHorizonDays)}</dd>
				<dt>Above Nurture</dt><dd>{horizon(detail.nurtureHorizonDays)}</dd>
			</dl>
			<div class="sens">
				{#each Object.entries(detail.sensitivity) as [label, value] (label)}
					<div><span class="num">{value.toFixed(2)}</span><span class="label">H {label}</span></div>
				{/each}
			</div>
		</section>

		<section class="block">
			<button class="disclose" onclick={() => (ledgerOpen = !ledgerOpen)} aria-expanded={ledgerOpen}>
				<span class="label">Full ledger · {scored.length}</span>
				<span class="sub dim">{ledgerOpen ? 'hide' : 'show the arithmetic'}</span>
			</button>
			{#if ledgerOpen}
				<Ledger rows={scored} onopen={openRow} />
				<div class="sum num">
					<div><span>logit({detail.baseRate.toFixed(2)})</span><span>{signed(detail.baseLogit)}</span></div>
					{#each scored as row (row.evidenceId + row.questionId + row.spanStart)}
						<div class="dim">
							<span>{row.questionKey}{row.themeCapped ? ' · capped' : ''}</span>
							<span>{signed(row.contributionNats)}</span>
						</div>
					{/each}
					<div class="total">
						<span>logit → fit</span><span>{signed(detail.logit)} → {fit(detail.probability)}</span>
					</div>
				</div>
				{#if detail.weakestLink}
					<p class="sub dim">
						Weakest link — falsify here first: <span>{detail.weakestLink}</span>
					</p>
				{/if}
			{/if}
		</section>

		<section class="block">
			<div class="block-head">
				<h3 class="label">Documents read · {detail.evidence.length}</h3>
				<label class="toggle">
					<input type="checkbox" bind:checked={silentOnly} />
					<span>silent only ({silent.length})</span>
				</label>
			</div>
			<ul class="docs">
				{#each documents as item (item.id)}
					<li>
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
					</li>
				{/each}
			</ul>
		</section>
	{/if}
</div>

{#if outreachOpen && detail}
	<OutreachModal
		companyId={detail.id}
		companyName={detail.name}
		{serviceLine}
		onclose={() => (outreachOpen = false)}
		ondrafted={onDrafted}
	/>
{/if}

<style>
	.head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 10px;
		padding: 13px 15px;
		border-bottom: 1px solid var(--ink);
	}

	h2 {
		font-size: 21px;
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

	.score {
		padding: 13px 15px;
		border-bottom: 1px solid var(--rule);
	}

	.score-row {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-bottom: 8px;
	}

	.score-val {
		font-family: var(--font-display);
		font-size: 36px;
		font-weight: 400;
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

	.block {
		padding: 13px 15px;
		border-bottom: 1px solid var(--rule);
	}

	.block h3,
	.block .label {
		margin-bottom: 8px;
	}

	.block-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 10px;
		margin-bottom: 8px;
	}

	.block-head h3,
	.block-head .label {
		margin: 0;
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

	.why-head .label {
		margin: 0;
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
		font-size: 16px;
		line-height: 1.3;
		color: var(--ink);
	}

	.why-text {
		margin-top: 6px;
		font-size: 12.5px;
		line-height: 1.6;
		color: var(--ink-2);
	}

	.why-clock {
		margin-top: 8px;
		font-size: 11.5px;
		color: var(--ink-3);
	}

	.actions {
		display: flex;
		flex-direction: column;
		gap: 9px;
	}

	.stage .label {
		margin-bottom: 5px;
	}

	.stage .seg {
		width: 100%;
	}

	.stage .seg button {
		flex: 1;
		text-align: center;
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

	.signals {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.signals li {
		padding-left: 9px;
		border-left: 2px solid var(--orange-line);
	}

	.signals li.against {
		border-left-color: var(--oxblood-line);
	}

	.sig-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 10px;
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
		margin-top: 3px;
		font-family: var(--font-display);
		font-size: 13px;
		font-style: italic;
		line-height: 1.45;
		color: var(--ink-2);
	}

	.link {
		color: var(--orange-deep);
		text-decoration: underline;
		text-underline-offset: 2px;
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

	.facts {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 3px 10px;
		margin: 0;
		font-size: 12.5px;
	}

	.facts dt {
		color: var(--ink-3);
	}

	.facts dd {
		margin: 0;
		text-align: right;
	}

	.sens {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 4px;
		margin-top: 9px;
	}

	.sens > div {
		border: 1px solid var(--rule);
		padding: 5px 6px;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}

	.sens .num {
		font-size: 13.5px;
	}

	.sens .label {
		font-size: 9.5px;
	}

	.disclose {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		width: 100%;
		gap: 10px;
	}

	.disclose .label {
		margin: 0;
	}

	.sum {
		margin-top: 10px;
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

	.toggle {
		display: flex;
		align-items: center;
		gap: 5px;
		font-size: 11px;
		color: var(--ink-3);
	}

	.docs {
		list-style: none;
		margin: 0;
		padding: 0;
		border-top: 1px solid var(--rule);
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
