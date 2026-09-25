<script lang="ts">
	import {
		fetchBoard,
		fetchMeta,
		rescore,
		type BoardRow,
		type Chip,
		type Engine,
		type Meta,
		type Move,
		type Rescore
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { age, duration, fit, host, plural, shortNumber, tier, theme } from '$lib/format';
	import BandBadge from '$lib/components/BandBadge.svelte';
	import FitMeter from '$lib/components/FitMeter.svelte';
	import SignalChip from '$lib/components/SignalChip.svelte';
	import EvidenceDrawer from '$lib/components/EvidenceDrawer.svelte';

	const FILTERS = [
		{ id: 'all', label: 'All' },
		{ id: 'hot', label: 'Hot' },
		{ id: 'priority', label: 'Priority' },
		{ id: 'rejected', label: 'Rejected' },
		{ id: 'thin', label: 'Insufficient evidence' }
	];

	const SORTS = [
		{ id: 'score', label: 'Best fit' },
		{ id: 'themes', label: 'Most independent themes' },
		{ id: 'recent', label: 'Newest signal' },
		{ id: 'evidence', label: 'Most documents read' },
		{ id: 'name', label: 'Company name' }
	];

	const SUGGESTIONS = [
		'Is the company building automation capability in-house rather than buying it?',
		'Has the company announced a cost-reduction programme with a quantified headcount target?',
		'Is the company hiring process-mining or process-excellence roles?',
		'Has the company appointed a new security leader in the last two quarters?'
	];

	let meta = $state<Meta | null>(null);
	let rows = $state<BoardRow[]>([]);
	let loading = $state(true);
	let loadError = $state<string | null>(null);

	let filter = $state('all');
	let sort = $state('score');
	let query = $state('');

	let panelOpen = $state(false);
	let questionText = $state('');
	let weightTier = $state<'HIGH' | 'MEDIUM' | 'LOW'>('HIGH');
	let polarity = $state<'POSITIVE' | 'NEGATIVE' | 'DISQUALIFYING'>('POSITIVE');
	let engine = $state<Engine>('local');

	let rescoring = $state(false);
	let elapsed = $state(0);
	let outcome = $state<Rescore | null>(null);
	let drawer = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);

	const serviceLine = $derived(session.serviceLine);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		fetchMeta(line)
			.then((result) => {
				if (!cancelled) meta = result;
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		const line = serviceLine;
		const activeFilter = filter;
		const activeSort = sort;
		const search = query;
		if (!line) return;

		let cancelled = false;
		loading = true;
		const timer = setTimeout(
			() => {
				fetchBoard({ serviceLine: line, filter: activeFilter, sort: activeSort, q: search })
					.then((rowResult) => {
						if (cancelled) return;
						rows = rowResult;
						loading = false;
						loadError = null;
					})
					.catch((cause) => {
						if (cancelled) return;
						loading = false;
						loadError = cause.message;
					});
			},
			search ? 220 : 0
		);

		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	$effect(() => {
		if (!rescoring) return;
		const started = performance.now();
		const timer = setInterval(() => {
			elapsed = performance.now() - started;
		}, 90);
		return () => clearInterval(timer);
	});

	const moves = $derived.by(() => {
		const map = new Map<string, Move>();
		outcome?.moves.forEach((move) => map.set(move.companyId, move));
		return map;
	});

	const bands = $derived(meta?.bands ?? {});

	function countFor(id: string): number {
		if (id === 'all') return bands.ALL ?? rows.length;
		if (id === 'hot') return bands.HOT ?? 0;
		if (id === 'rejected') return bands.DISQUALIFIED ?? 0;
		if (id === 'priority') return bands.PRIORITY ?? 0;
		if (id === 'thin') return bands.THIN ?? 0;
		return 0;
	}

	async function runRescore() {
		const text = questionText.trim();
		if (text.length < 12 || rescoring) return;
		rescoring = true;
		elapsed = 0;
		outcome = null;
		try {
			const result = await rescore({
				serviceLine,
				question: text,
				questionId: null,
				engine,
				tier: weightTier,
				polarity
			});
			outcome = result;
			rows = result.board;
			elapsed = result.tookMs;
			if (filter !== 'all') {
				filter = 'all';
			}
			toasts.push(
				`Re-scored ${shortNumber(result.documentsScanned)} documents in ${duration(result.tookMs)} — ${result.movedRows} rows moved. The question is now part of the policy set.`,
				'success'
			);
			fetchMeta(serviceLine).then((result) => (meta = result));
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			rescoring = false;
		}
	}

	function clearDiff() {
		outcome = null;
		questionText = '';
	}

	function openChip(chip: Chip) {
		drawer = { id: chip.evidenceId, focus: { start: chip.spanStart, end: chip.spanEnd } };
	}
</script>

<svelte:head><title>Signal Board · Orange Systems</title></svelte:head>

<div class="head">
	<div class="stack gap-4">
		<h1>Signal Board</h1>
		<p class="muted small">
			{serviceLine} · {plural(rows.length, 'company', 'companies')} in view
			{#if meta}
				· {shortNumber(meta.captures)} stored captures · base rate {meta.scoring.baseRate.toFixed(2)}
			{/if}
		</p>
	</div>
	<button class="btn btn-primary" onclick={() => (panelOpen = !panelOpen)}>
		{panelOpen ? 'Close' : 'Re-score against a new question'}
	</button>
</div>

{#if panelOpen}
	<section class="card rescore">
		<div class="stack gap-4">
			<h2>Ask a question the board has never been scored against</h2>
			<p class="small muted">
				Every stored document is re-classified against this question and the whole board re-ranks from the
				result. Nothing is re-crawled — the corpus is fixed, only the policy changes.
			</p>
		</div>

		<textarea
			class="textarea"
			rows="3"
			placeholder="e.g. Is the company consolidating shared service centres or moving transactional work into a captive operation?"
			bind:value={questionText}
		></textarea>

		<div class="suggestions">
			{#each SUGGESTIONS as suggestion (suggestion)}
				<button class="chip" onclick={() => (questionText = suggestion)}>{suggestion}</button>
			{/each}
		</div>

		<div class="controls">
			<label class="field">
				<span class="tiny muted">Importance</span>
				<select class="select" bind:value={weightTier}>
					<option value="HIGH">High — odds ×4.5 (1.5 nats)</option>
					<option value="MEDIUM">Medium — odds ×2.0 (0.7 nats)</option>
					<option value="LOW">Low — odds ×1.35 (0.3 nats)</option>
				</select>
			</label>
			<label class="field">
				<span class="tiny muted">Direction</span>
				<select class="select" bind:value={polarity}>
					<option value="POSITIVE">Positive — evidence for buying</option>
					<option value="NEGATIVE">Counter-evidence — argues against</option>
					<option value="DISQUALIFYING">Disqualifying — removes the company from the queue</option>
				</select>
			</label>
			<label class="field">
				<span class="tiny muted">Engine</span>
				<select class="select" bind:value={engine}>
					<option value="local">Local rubric engine — instant, offline, deterministic</option>
					<option value="deepseek" disabled={!meta?.languageModelAvailable}>
						Language model {meta?.languageModelAvailable ? '' : '(not configured here)'}
					</option>
				</select>
			</label>
			<button class="btn btn-primary" onclick={runRescore} disabled={rescoring || questionText.trim().length < 12}>
				{#if rescoring}<span class="spinner"></span>{/if}
				{rescoring ? `Re-scoring… ${duration(elapsed)}` : 'Re-score the corpus'}
			</button>
		</div>

		{#if rescoring}
			<p class="tiny muted pulsing">
				Classifying {shortNumber(meta?.captures ?? 0)} documents against the new question. The counter is real —
				it is the elapsed wall clock, not an animation.
			</p>
		{:else}
			<p class="tiny muted">
				The local engine scores every stored document in a second or two and never touches the network, so the
				demo cannot be broken by the venue's Wi-Fi. The language model reads each document properly and is
				better on questions that need a business inference, but it is slower and needs a connection.
			</p>
		{/if}
	</section>
{/if}

{#if outcome}
	<section class="card diff">
		<div class="row gap-12" style="flex-wrap: wrap; align-items: flex-start">
			<div class="stack gap-4" style="flex: 1; min-width: 260px">
				<h2>
					{outcome.movedRows} {outcome.movedRows === 1 ? 'row' : 'rows'} moved
					{#if outcome.changedTop === 1}· the #1 company changed{/if}
				</h2>
				<p class="small muted">“{outcome.question}”</p>
			</div>
			<div class="stats">
				<div><span class="mono big">{duration(outcome.tookMs)}</span><span class="tiny muted">re-score</span></div>
				<div><span class="mono big">{shortNumber(outcome.documentsScanned)}</span><span class="tiny muted">documents</span></div>
				<div><span class="mono big">{shortNumber(outcome.signalsProduced)}</span><span class="tiny muted">signals kept</span></div>
			</div>
			<button class="btn" onclick={clearDiff}>Back to the scored board</button>
		</div>
	</section>
{/if}

<div class="toolbar">
	<div class="tabs" role="tablist">
		{#each FILTERS as option (option.id)}
			<button
				class="tab"
				class:active={filter === option.id}
				role="tab"
				aria-selected={filter === option.id}
				onclick={() => (filter = option.id)}
			>
				{option.label}
				<span class="count mono">{countFor(option.id)}</span>
			</button>
		{/each}
	</div>

	<div class="row gap-8">
		<label class="search">
			<span class="sr-only">Search companies</span>
			<input class="input" placeholder="Search companies…" bind:value={query} />
		</label>
		<label>
			<span class="sr-only">Sort</span>
			<select class="select" bind:value={sort}>
				{#each SORTS as option (option.id)}
					<option value={option.id}>{option.label}</option>
				{/each}
			</select>
		</label>
	</div>
</div>

{#if loadError}
	<p class="card empty" style="border-color: var(--danger-line); color: var(--danger)">{loadError}</p>
{:else if loading}
	<p class="card empty"><span class="spinner"></span> Loading the board…</p>
{:else if rows.length === 0}
	<p class="card empty">
		No company matches this view. Try another filter, or clear the search.
	</p>
{:else}
	<div class="card table-wrap">
		<table class="grid">
			<thead>
				<tr>
					{#if outcome}
						<th style="width: 44px; text-align: right">Was</th>
					{/if}
					<th style="width: 44px; text-align: right">{outcome ? 'Now' : '#'}</th>
					<th style="width: 22%">Company</th>
					<th style="width: 15%">Fit</th>
					<th style="width: 29%">Strongest evidence</th>
					<th style="width: 12%">Themes</th>
					<th style="width: 8%; text-align: right">Read</th>
					<th style="width: 8%">Newest</th>
				</tr>
			</thead>
			<tbody>
				{#each rows as row (row.id)}
					{@const move = moves.get(row.id)}
					<tr class:rejected={row.band === 'DISQUALIFIED'}>
						{#if outcome}
							<td class="mono rank">
								<span class="before">{move ? move.beforeRank : '—'}</span>
							</td>
						{/if}
						<td style="text-align: right">
							<div class="stack gap-4" style="align-items: flex-end">
								<span class="mono rank">{row.rank}</span>
								{#if move && move.delta !== 0}
									<span class="delta" class:up={move.delta > 0} class:down={move.delta < 0}>
										{move.delta > 0 ? '▲' : '▼'} {Math.abs(move.delta)}
									</span>
								{/if}
							</div>
						</td>
						<td>
							<div class="stack gap-4">
								<a class="name" href="/companies/{row.id}">{row.name}</a>
								<span class="tiny muted">
									{#if row.domain}<span class="mono">{row.domain}</span> · {/if}
									{row.country ?? '—'}{#if row.hqCity} · {row.hqCity}{/if}
								</span>
								<span class="tiny muted">{row.industry ?? 'unclassified'}{#if row.sizeBand} · {row.sizeBand}{/if}</span>
								{#if row.competitor}
									<span class="chip chip-danger tiny" style="align-self: flex-start">competitor</span>
								{/if}
							</div>
						</td>
						<td>
							<div class="stack gap-6">
								<div class="row gap-8">
									<span class="mono fitvalue">{fit(row.probability)}</span>
									<BandBadge band={row.band} label={row.bandLabel} title={row.gateReason ?? ''} />
								</div>
								{#if meta}
									<FitMeter
										probability={row.probability}
										band={row.band}
										baseRate={meta.scoring.baseRate}
										thresholds={{
											watch: meta.scoring.watchThreshold,
											nurture: meta.scoring.nurtureThreshold,
											priority: meta.scoring.priorityThreshold
										}}
										compact
									/>
								{/if}
								{#if row.gateReason}
									<span class="tiny gate">{row.gateReason}</span>
								{/if}
							</div>
						</td>
						<td>
							{#if row.strongest}
								<div class="stack gap-6">
									<span class="tiny muted">{row.headline}</span>
									<SignalChip chip={row.strongest} dense onopen={openChip} />
								</div>
							{:else}
								<span class="tiny muted">{row.headline}</span>
							{/if}
						</td>
						<td>
							<div class="row gap-4" style="flex-wrap: wrap">
								{#if row.themes.length === 0}
									<span class="tiny muted">none</span>
								{/if}
								{#each row.themes as name (name)}
									<span class="chip tiny">{theme(name)}</span>
								{/each}
								{#if row.themeCount >= 2}
									<span class="chip chip-success tiny" title="Independent corroboration from two themes — the condition for reaching Priority">
										2+
									</span>
								{/if}
							</div>
						</td>
						<td style="text-align: right">
							<div class="stack gap-4" style="align-items: flex-end">
								<span class="mono">{row.evidenceCount}</span>
								{#if row.silentDocuments > 0}
									<span class="tiny muted" title="Documents read that produced no signal at this confidence">
										{row.silentDocuments} silent
									</span>
								{/if}
							</div>
						</td>
						<td>
							<span class="tiny muted">{age(row.newestSignalAgeDays)}</span>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
{/if}

{#if drawer}
	<EvidenceDrawer evidenceId={drawer.id} focus={drawer.focus} onclose={() => (drawer = null)} />
{/if}

<style>
	.head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 20px;
		margin-bottom: 16px;
	}

	.rescore {
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		margin-bottom: 16px;
		border-color: var(--orange-200);
		background: linear-gradient(180deg, var(--orange-050), var(--surface) 60%);
	}

	.suggestions {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.suggestions .chip {
		cursor: pointer;
		text-align: left;
	}

	.suggestions .chip:hover {
		background: var(--orange-100);
		border-color: var(--orange-200);
	}

	.controls {
		display: flex;
		gap: 14px;
		align-items: flex-end;
		flex-wrap: wrap;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 230px;
	}

	.controls .btn-primary {
		margin-left: auto;
	}

	.diff {
		padding: 16px 18px;
		margin-bottom: 16px;
		border-color: var(--orange);
		box-shadow: var(--shadow-sm);
		display: flex;
		gap: 18px;
		align-items: flex-start;
	}

	.stats {
		display: flex;
		gap: 22px;
	}

	.stats div {
		display: flex;
		flex-direction: column;
	}

	.big {
		font-size: 18px;
		font-weight: 600;
	}

	.toolbar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		margin-bottom: 12px;
		flex-wrap: wrap;
	}

	.tabs {
		display: flex;
		gap: 2px;
		background: var(--surface-sunken);
		border: 1px solid var(--line);
		border-radius: var(--radius);
		padding: 3px;
	}

	.tab {
		display: inline-flex;
		align-items: center;
		gap: 7px;
		border: 0;
		background: transparent;
		border-radius: var(--radius-sm);
		padding: 6px 11px;
		font-size: 13px;
		font-weight: 550;
		color: var(--ink-2);
	}

	.tab:hover {
		background: var(--surface);
	}

	.tab.active {
		background: var(--surface);
		color: var(--ink);
		box-shadow: var(--shadow-xs);
	}

	.tab .count {
		font-size: 11px;
		color: var(--ink-3);
		background: var(--surface-sunken);
		border-radius: 999px;
		padding: 0 6px;
	}

	.tab.active .count {
		background: var(--orange-050);
		color: var(--orange-ink);
	}

	.search {
		flex: 1;
		min-width: 220px;
	}

	.toolbar .select {
		width: auto;
		min-width: 210px;
	}

	.table-wrap {
		overflow: auto;
		max-height: calc(100vh - 260px);
	}

	.name {
		font-weight: 600;
		font-size: 14.5px;
		text-decoration: none;
		color: var(--ink);
	}

	.name:hover {
		color: var(--orange-deep);
		text-decoration: underline;
	}

	.fitvalue {
		font-size: 19px;
		font-weight: 600;
		letter-spacing: -0.02em;
	}

	.rank {
		color: var(--ink-3);
		font-size: 13px;
	}

	.before {
		color: var(--ink-4);
	}

	.delta {
		font-size: 11px;
		font-weight: 600;
	}

	.delta.up {
		color: var(--success);
	}

	.delta.down {
		color: var(--danger);
	}

	.gate {
		color: var(--danger);
	}

	tr.rejected td {
		background: rgb(191 42 25 / 0.025);
	}

	.empty {
		padding: 28px;
		display: flex;
		align-items: center;
		gap: 10px;
		color: var(--ink-3);
		font-size: 14px;
	}

	@media (max-width: 1100px) {
		.table-wrap {
			max-height: none;
		}
	}
</style>
