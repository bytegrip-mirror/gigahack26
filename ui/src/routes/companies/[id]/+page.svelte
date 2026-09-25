<script lang="ts">
	import { page } from '$app/state';
	import {
		fetchCompany,
		sendFeedback,
		type CompanyDetail,
		type LedgerRow
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import {
		age,
		confidence,
		date,
		fit,
		horizon,
		host,
		nats,
		odds,
		plural,
		precise,
		signed,
		sourceKind,
		theme,
		tier
	} from '$lib/format';
	import BandBadge from '$lib/components/BandBadge.svelte';
	import FitMeter from '$lib/components/FitMeter.svelte';
	import LedgerTable from '$lib/components/LedgerTable.svelte';
	import EvidenceDrawer from '$lib/components/EvidenceDrawer.svelte';
	import OutreachModal from '$lib/components/OutreachModal.svelte';

	let detail = $state<CompanyDetail | null>(null);
	let error = $state<string | null>(null);
	let loading = $state(true);
	let drawer = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);
	let outreachOpen = $state(false);
	let feedbackSent = $state<string | null>(null);
	let showSilent = $state(false);

	const id = $derived(page.params.id ?? '');
	const serviceLine = $derived(session.serviceLine);

	$effect(() => {
		const companyId = id;
		const line = serviceLine;
		if (!companyId || !line) return;
		loading = true;
		fetchCompany(companyId, line)
			.then((result) => {
				detail = result;
				error = null;
			})
			.catch((cause) => (error = cause.message))
			.finally(() => (loading = false));
	});

	const positive = $derived(
		detail ? detail.ledger.filter((row) => !row.blocking && row.contributionNats > 0) : []
	);
	const silentDocuments = $derived(
		detail ? detail.evidence.filter((item) => item.signalCount === 0) : []
	);

	async function judge(verdict: 'ACCEPT' | 'REJECT') {
		if (!detail) return;
		try {
			await sendFeedback({ companyId: detail.id, repVerdict: verdict });
			feedbackSent = verdict;
			toasts.push(
				verdict === 'ACCEPT'
					? 'Recorded as a qualified lead — it counts toward the queue precision metric.'
					: 'Recorded as rejected — it counts against the queue precision metric.',
				'success'
			);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	function openRow(row: LedgerRow) {
		drawer = { id: row.evidenceId, focus: { start: row.spanStart, end: row.spanEnd } };
	}
</script>

<svelte:head><title>{detail?.name ?? 'Company'} · Signal Board</title></svelte:head>

{#if loading && !detail}
	<p class="card empty"><span class="spinner"></span> Loading the scorecard…</p>
{:else if error}
	<p class="card empty" style="color: var(--danger)">{error}</p>
{:else if detail}
	<a class="back" href="/">← Back to the board</a>

	<header class="card head">
		<div class="stack gap-8" style="flex: 1; min-width: 300px">
			<div class="row gap-10" style="flex-wrap: wrap">
				<h1>{detail.name}</h1>
				<BandBadge band={detail.band} label={detail.bandLabel} />
				{#if detail.competitor}<span class="chip chip-danger">competitor</span>{/if}
			</div>
			<p class="muted small">
				{#if detail.domain}<a href="https://{detail.domain}" target="_blank" rel="noreferrer noopener">{detail.domain} ↗</a> · {/if}
				{detail.hqCity ? `${detail.hqCity}, ` : ''}{detail.country ?? 'unknown country'} ·
				{detail.industry ?? 'unclassified'} ·
				{detail.sizeBand ?? 'unknown size'}{#if detail.employees}
					({detail.employees} est.{#if detail.sizeConfidence && detail.sizeConfidence < 0.8}, low confidence{/if}){/if}
			</p>
			<p class="headline">{detail.bandSummary}</p>
		</div>

		<div class="verdict">
			<div class="stack gap-6" style="align-items: flex-end">
				<span class="mono fitvalue">{fit(detail.probability)}</span>
				<span class="tiny muted">posterior probability</span>
			</div>
			<FitMeter
				probability={detail.probability}
				band={detail.band}
				baseRate={detail.baseRate}
				thresholds={{
					watch: 0.3,
					nurture: 0.5,
					priority: 0.75
				}}
			/>
		</div>
	</header>

	{#if detail.gateReason}
		<section class="card gate">
			<div class="stack gap-4">
				<h2>Removed from the queue</h2>
				<p>{detail.gateReason}</p>
				<p class="tiny muted">
					The score above is still computed and shown, so you can see the counterfactual. A hard rule stays
					hard: no amount of positive evidence overrides it.
				</p>
			</div>
		</section>
	{/if}

	<section class="card gates">
		<h3>Gate evaluation</h3>
		<p class="mono small">{detail.gateLine}</p>
	</section>

	<div class="columns">
		<section class="card ledger-card">
			<div class="row" style="justify-content: space-between; margin-bottom: 12px">
				<h3>The ledger — {plural(positive.length, 'weighted signal')}</h3>
				<span class="tiny muted">sorted by absolute contribution</span>
			</div>

			<LedgerTable rows={detail.ledger.filter((row) => !row.blocking)} onopen={openRow} />

			<div class="sum mono">
				<div class="sum-row">
					<span>logit(base rate {detail.baseRate.toFixed(2)})</span>
					<span>{signed(detail.baseLogit)}</span>
				</div>
				{#each detail.ledger.filter((row) => !row.blocking) as row (row.evidenceId + row.questionId + row.spanStart)}
					<div class="sum-row muted-row">
						<span>{row.questionKey}{row.themeCapped ? ' (capped)' : ''}</span>
						<span>{signed(row.contributionNats)}</span>
					</div>
				{/each}
				<div class="sum-row total">
					<span>logit → posterior</span>
					<span>{signed(detail.logit)} → {fit(detail.probability)}</span>
				</div>
			</div>
			<p class="tiny muted" style="margin-top: 8px">
				Each row is tier × source authority × time decay × (2·confidence − 1). The total is a sum of log-odds,
				so it converts to a probability with one sigmoid. A calculator reproduces every number here.
			</p>
		</section>

		<aside class="stack gap-16">
			<section class="card">
				<h3>Themes and the cap</h3>
				<p class="tiny muted" style="margin-bottom: 10px">
					No single theme can push a company over the Priority line on its own. The cap is derived from the
					threshold, not tuned: {nats(detail.themeCap)}.
				</p>
				{#each detail.themes as item (item.name)}
					<div class="theme-row">
						<div class="row" style="justify-content: space-between">
							<span>{theme(item.name)}</span>
							<span class="mono">{signed(item.total)}</span>
						</div>
						<div class="theme-track">
							<div
								class="theme-fill"
								class:capped={item.capped}
								style="width: {Math.min(100, (Math.abs(item.total) / detail.themeCap) * 100)}%"
							></div>
						</div>
						{#if item.capped}
							<span class="tiny" style="color: var(--warn)">capped — this theme alone cannot reach Priority</span>
						{/if}
					</div>
				{/each}
				{#if detail.themes.length === 0}
					<p class="small muted">No theme contributed. The company sits at the base rate.</p>
				{/if}
				<p class="tiny muted" style="margin-top: 10px">
					Independent corroboration, not volume, is what reaches the top of the queue.
					{#if detail.themeCount >= 2}
						This company has {detail.themeCount}.
					{:else}
						This company has {detail.themeCount}.
					{/if}
				</p>
			</section>

			<section class="card">
				<h3>Counter-evidence</h3>
				{#if detail.counterEvidence.length === 0}
					<p class="small muted">Nothing in the corpus argues against this company.</p>
				{:else}
					<ul class="counter">
						{#each detail.counterEvidence as row (row.evidenceId + row.questionId + row.spanStart)}
							<li>
								<div class="row" style="justify-content: space-between">
									<span class="small">{row.questionKey}</span>
									<span class="mono small" style="color: var(--danger)">{signed(row.contributionNats)}</span>
								</div>
								<p class="tiny muted">{row.publisher} · {age(row.ageDays)}</p>
								{#if row.spanText}<p class="quote tiny">“{row.spanText}”</p>{/if}
							</li>
						{/each}
					</ul>
				{/if}
			</section>

			<section class="card">
				<h3>How long this stays true</h3>
				<dl class="facts">
					<dt>Priority for</dt>
					<dd>{horizon(detail.priorityHorizonDays)}</dd>
					<dt>Nurture floor for</dt>
					<dd>{horizon(detail.nurtureHorizonDays)}</dd>
				</dl>
				<p class="tiny muted" style="margin: 10px 0 6px">Sensitivity to the half-life</p>
				<div class="sens">
					{#each Object.entries(detail.sensitivity) as [label, value] (label)}
						<div class="sens-cell">
							<span class="mono">{value.toFixed(2)}</span>
							<span class="tiny muted">H = {label}</span>
						</div>
					{/each}
				</div>
				{#if detail.weakestLink}
					<p class="tiny muted" style="margin-top: 10px">
						Weakest link — check this one first if you want to falsify the score:
						<span class="mono">{detail.weakestLink}</span>
					</p>
				{/if}
			</section>

			<section class="card">
				<h3>Your call</h3>
				{#if feedbackSent}
					<p class="small">
						Recorded as <strong>{feedbackSent === 'ACCEPT' ? 'qualified' : 'rejected'}</strong>. It now counts
						toward the rolling queue precision.
					</p>
				{:else}
					<p class="small muted" style="margin-bottom: 10px">
						Would you work this account? The answer feeds the precision metric that decides whether the
						scoring stays trustworthy.
					</p>
					<div class="row gap-8">
						<button class="btn" onclick={() => judge('ACCEPT')}>Qualified lead</button>
						<button class="btn btn-danger" onclick={() => judge('REJECT')}>Not a lead</button>
					</div>
				{/if}
				<hr class="divider" style="margin: 14px 0" />
				<button class="btn btn-primary" style="width: 100%; justify-content: center" onclick={() => (outreachOpen = true)}>
					Generate an outreach draft
				</button>
			</section>
		</aside>
	</div>

	<section class="card documents">
		<div class="row" style="justify-content: space-between; margin-bottom: 12px; flex-wrap: wrap; gap: 10px">
			<h3>Documents read — {plural(detail.evidence.length, 'capture')}</h3>
			<label class="row gap-6 small">
				<input type="checkbox" bind:checked={showSilent} />
				Show only the {silentDocuments.length} that produced nothing
			</label>
		</div>

		<div class="doc-grid">
			{#each (showSilent ? silentDocuments : detail.evidence) as item (item.id)}
				<button class="doc" onclick={() => (drawer = { id: item.id, focus: null })}>
					<div class="row gap-6" style="justify-content: space-between">
						<span class="chip tiny">{sourceKind(item.sourceKind)}</span>
						<span class="tiny mono muted">{date(item.publishedAt ?? item.observedAt)}</span>
					</div>
					<span class="doc-title">{item.title ?? host(item.sourceUrl)}</span>
					<span class="tiny muted">{item.publisher} · {plural(item.charLength, 'char')}</span>
					{#if item.signalCount > 0}
						<span class="chip chip-orange tiny" style="align-self: flex-start">
							{plural(item.signalCount, 'signal')}
						</span>
					{:else}
						<span class="chip tiny" style="align-self: flex-start">read, no signal</span>
					{/if}
				</button>
			{/each}
		</div>
	</section>

	{#if drawer}
		<EvidenceDrawer evidenceId={drawer.id} focus={drawer.focus} onclose={() => (drawer = null)} />
	{/if}

	{#if outreachOpen}
		<OutreachModal
			companyId={detail.id}
			companyName={detail.name}
			{serviceLine}
			onclose={() => (outreachOpen = false)}
		/>
	{/if}
{/if}

<style>
	.back {
		display: inline-block;
		font-size: 13px;
		margin-bottom: 12px;
		text-decoration: none;
		color: var(--ink-3);
	}

	.back:hover {
		color: var(--orange-deep);
	}

	.head {
		display: flex;
		gap: 28px;
		padding: 20px;
		align-items: flex-start;
		flex-wrap: wrap;
		margin-bottom: 14px;
	}

	.row.gap-10 {
		gap: 10px;
	}

	.headline {
		font-size: 14.5px;
		color: var(--ink-2);
		max-width: 76ch;
	}

	.verdict {
		min-width: 260px;
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 10px;
		align-items: flex-end;
	}

	.verdict :global(.meter) {
		max-width: 320px;
	}

	.fitvalue {
		font-size: 38px;
		font-weight: 600;
		letter-spacing: -0.03em;
		line-height: 1;
	}

	.gate {
		padding: 16px 20px;
		border-color: var(--danger-line);
		background: var(--danger-bg);
		margin-bottom: 14px;
	}

	.gate h2 {
		color: var(--danger);
	}

	.gate p {
		font-size: 13.5px;
		color: var(--ink-2);
	}

	.gates {
		padding: 14px 20px;
		margin-bottom: 14px;
	}

	.gates p {
		margin-top: 6px;
		font-size: 12.5px;
		color: var(--ink-2);
	}

	.columns {
		display: grid;
		grid-template-columns: minmax(0, 1.75fr) minmax(300px, 1fr);
		gap: 16px;
		align-items: start;
	}

	.ledger-card {
		padding: 18px;
	}

	aside section {
		padding: 16px;
	}

	aside h3 {
		margin-bottom: 8px;
	}

	.sum {
		margin-top: 14px;
		border-top: 1px solid var(--line);
		padding-top: 10px;
		font-size: 12.5px;
	}

	.sum-row {
		display: flex;
		justify-content: space-between;
		gap: 16px;
		padding: 2px 0;
	}

	.muted-row {
		color: var(--ink-3);
	}

	.sum-row.total {
		border-top: 1px solid var(--line);
		margin-top: 6px;
		padding-top: 6px;
		font-weight: 600;
		color: var(--ink);
	}

	.theme-row {
		margin-bottom: 10px;
	}

	.theme-track {
		height: 5px;
		background: var(--surface-sunken);
		border-radius: 999px;
		overflow: hidden;
		margin: 4px 0 2px;
	}

	.theme-fill {
		height: 100%;
		background: var(--orange);
		border-radius: 999px;
	}

	.theme-fill.capped {
		background: var(--warn);
	}

	.counter {
		list-style: none;
		padding: 0;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.quote {
		margin-top: 4px;
		font-style: italic;
		color: var(--ink-2);
		border-left: 2px solid var(--line-strong);
		padding-left: 8px;
	}

	.facts {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 4px 12px;
		margin: 0;
		font-size: 13px;
	}

	.facts dt {
		color: var(--ink-3);
	}

	.facts dd {
		margin: 0;
		font-weight: 550;
	}

	.sens {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 6px;
	}

	.sens-cell {
		background: var(--surface-sunken);
		border: 1px solid var(--line);
		border-radius: var(--radius-sm);
		padding: 6px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 1px;
	}

	.sens-cell .mono {
		font-size: 14px;
		font-weight: 600;
	}

	.documents {
		margin-top: 16px;
		padding: 18px;
	}

	.doc-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
		gap: 10px;
	}

	.doc {
		display: flex;
		flex-direction: column;
		gap: 5px;
		text-align: left;
		background: var(--surface);
		border: 1px solid var(--line);
		border-radius: var(--radius);
		padding: 11px;
		font-size: 12.5px;
	}

	.doc:hover {
		border-color: var(--orange-200);
		background: var(--orange-050);
	}

	.doc-title {
		font-weight: 550;
		line-height: 1.35;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.empty {
		padding: 28px;
		display: flex;
		align-items: center;
		gap: 10px;
		color: var(--ink-3);
	}

	@media (max-width: 1080px) {
		.columns {
			grid-template-columns: 1fr;
		}
	}
</style>
