<script lang="ts">
	import { untrack } from 'svelte';
	import { pushState } from '$app/navigation';
	import {
		fetchBoard,
		fetchMeta,
		rescore,
		type BoardRow,
		type Engine,
		type Meta,
		type Move,
		type Polarity,
		type Rescore,
		type WeightTier
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { ageShort, duration, fit, host, shortNumber, theme } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';
	import ScoreMeter from '$lib/components/ScoreMeter.svelte';
	import CaptureSheet from '$lib/components/CaptureSheet.svelte';
	import Scorecard from '$lib/views/Scorecard.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';

	let { initialSelected = null }: { initialSelected?: string | null } = $props();

	const FILTERS = [
		{ id: 'all', label: 'All' },
		{ id: 'hot', label: 'Hot' },
		{ id: 'priority', label: 'Priority' },
		{ id: 'rejected', label: 'Rejected' },
		{ id: 'thin', label: 'Thin' }
	];

	const STAGES = [
		{ id: '', label: 'Any stage' },
		{ id: 'worked', label: 'Worked' },
		{ id: 'untouched', label: 'Untouched' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const SORTS = [
		{ id: 'score', label: 'Fit' },
		{ id: 'themes', label: 'Themes' },
		{ id: 'recent', label: 'Newest' },
		{ id: 'evidence', label: 'Documents' },
		{ id: 'name', label: 'Name' }
	];

	const ASKS = [
		'Is the company building automation capability in-house rather than buying it?',
		'Has the company announced a cost-reduction programme with a quantified headcount target?',
		'Is the company hiring process-mining or process-excellence roles?',
		'Has the company appointed a new security leader in the last two quarters?'
	];

	let meta = $state<Meta | null>(null);
	let rows = $state<BoardRow[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let filter = $state('all');
	let sort = $state('score');
	let stage = $state('');
	let query = $state('');
	let selected = $state<string | null>(untrack(() => initialSelected));

	let askOpen = $state(false);
	let questionText = $state('');
	let weightTier = $state<WeightTier>('HIGH');
	let polarity = $state<Polarity>('POSITIVE');
	let engine = $state<Engine>('local');
	let rescoring = $state(false);
	let elapsed = $state(0);
	let outcome = $state<Rescore | null>(null);

	let capture = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);

	const serviceLine = $derived(session.serviceLine);
	const movements = $derived.by(() => {
		const map = new Map<string, Move>();
		outcome?.moves.forEach((move) => map.set(move.companyId, move));
		return map;
	});
	const bands = $derived(meta?.bands ?? {});
	const regionLabel = $derived.by(() => {
		for (const continent of meta?.regions ?? []) {
			if (continent.key === session.region) return continent.name;
			for (const child of continent.children) {
				if (child.key === session.region) return `${continent.name} › ${child.name}`;
			}
		}
		return 'Worldwide';
	});

	function countFor(id: string) {
		if (id === 'all') return bands.ALL ?? rows.length;
		if (id === 'hot') return bands.HOT ?? 0;
		if (id === 'priority') return bands.PRIORITY ?? 0;
		if (id === 'rejected') return bands.DISQUALIFIED ?? 0;
		if (id === 'thin') return bands.THIN ?? 0;
		return 0;
	}

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
		const activeStage = stage;
		const activeRegion = session.region;
		if (!line) return;

		let cancelled = false;
		loading = true;
		const timer = setTimeout(
			() => {
				fetchBoard({
					serviceLine: line,
					filter: activeFilter,
					sort: activeSort,
					q: search,
					stage: activeStage,
					region: activeRegion
				})
					.then((result) => {
						if (cancelled) return;
						rows = result;
						loading = false;
						error = null;
					})
					.catch((cause) => {
						if (cancelled) return;
						loading = false;
						error = cause.message;
					});
			},
			search ? 200 : 0
		);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	$effect(() => {
		if (!rescoring) return;
		const started = performance.now();
		const timer = setInterval(() => (elapsed = performance.now() - started), 90);
		return () => clearInterval(timer);
	});

	function select(id: string | null) {
		selected = id;
		pushState(id ? `/?co=${id}` : '/', {});
	}

	async function runAsk() {
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
			if (filter !== 'all') filter = 'all';
			toasts.push(
				`${result.movedRows} rows moved in ${duration(result.tookMs)}. The question is now policy.`,
				'success'
			);
			fetchMeta(serviceLine).then((result) => (meta = result));
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			rescoring = false;
		}
	}

	function clearOutcome() {
		outcome = null;
		questionText = '';
	}
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">{serviceLine || 'Board'}</h1>
			<p class="label">
				{shortNumber(bands.ALL ?? rows.length)} ranked · {shortNumber(meta?.captures ?? 0)} captures{#if session.region !== 'world'}
					· {regionLabel}{/if}
			</p>
		</div>

		<div class="tools">
			<div class="seg" role="tablist" aria-label="Filter">
				{#each FILTERS as option (option.id)}
					<button
						role="tab"
						aria-selected={filter === option.id}
						onclick={() => (filter = option.id)}
					>
						{option.label} <span class="num">{countFor(option.id)}</span>
					</button>
				{/each}
			</div>

			<input class="input search" placeholder="Filter by name, country, industry" bind:value={query} />

			<label class="sr-only" for="region">Region</label>
			<select id="region" class="select region" value={session.region} onchange={(event) => {
				session.setRegion(event.currentTarget.value);
			}}>
				<option value="world">Worldwide</option>
				{#each meta?.regions ?? [] as continent (continent.key)}
					<option value={continent.key}>{continent.name} — all</option>
					{#each continent.children as child (child.key)}
						<option value={child.key}>　{continent.name} › {child.name}</option>
					{/each}
				{/each}
			</select>

			<label class="sr-only" for="stage">Lead stage</label>
			<select id="stage" class="select" bind:value={stage}>
				{#each STAGES as option (option.id)}
					<option value={option.id}>
						{option.label}{option.id === 'worked' && meta ? ` (${meta.stages.TOTAL ?? 0})` : ''}
					</option>
				{/each}
			</select>

			<label class="sr-only" for="sort">Sort</label>
			<select id="sort" class="select" bind:value={sort}>
				{#each SORTS as option (option.id)}
					<option value={option.id}>{option.label}</option>
				{/each}
			</select>

			<button class="btn btn-primary" onclick={() => (askOpen = !askOpen)}>
				{askOpen ? 'Close' : 'Ask a question'}
			</button>
		</div>
	</header>

	{#if askOpen || outcome}
		<div class="ask">
			{#if !outcome}
				<div class="ask-input">
					<label class="label" for="ask">Question the corpus has not been scored against</label>
					<textarea
						id="ask"
						class="textarea"
						rows="2"
						bind:value={questionText}
						placeholder="Is the company consolidating shared service centres into a captive operation?"
					></textarea>
					<div class="chips">
						{#each ASKS as suggestion (suggestion)}
							<button class="tag" onclick={() => (questionText = suggestion)}>{suggestion}</button>
						{/each}
					</div>
				</div>

				<div class="ask-opts">
					<label class="field">
						<span class="label">Weight</span>
						<select class="select" bind:value={weightTier}>
							<option value="HIGH">High · 1.5 nats</option>
							<option value="MEDIUM">Medium · 0.7</option>
							<option value="LOW">Low · 0.3</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Polarity</span>
						<select class="select" bind:value={polarity}>
							<option value="POSITIVE">Positive</option>
							<option value="NEGATIVE">Counter</option>
							<option value="DISQUALIFYING">Disqualifying</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Engine</span>
						<select class="select" bind:value={engine}>
							<option value="local">Local rubric</option>
							<option value="deepseek" disabled={!meta?.languageModelAvailable}>
								Language model{meta?.languageModelAvailable ? '' : ' — not configured'}
							</option>
						</select>
					</label>
					<button
						class="btn btn-primary"
						onclick={runAsk}
						disabled={rescoring || questionText.trim().length < 12}
					>
						{#if rescoring}<span class="spinner"></span>{/if}
						{rescoring ? duration(elapsed) : 'Score'}
					</button>
				</div>

				{#if rescoring}
					<p class="label pulsing">
						Classifying {shortNumber(meta?.captures ?? 0)} documents. The counter is wall clock.
					</p>
				{/if}
			{:else}
				<div class="result">
					<div class="result-head">
						<span class="num big">{outcome.movedRows}</span>
						<span class="label">rows moved{outcome.changedTop === 1 ? ' · leader changed' : ''}</span>
					</div>
					<div class="result-stats">
						<span><span class="num">{duration(outcome.tookMs)}</span> <span class="label">elapsed</span></span>
						<span><span class="num">{shortNumber(outcome.documentsScanned)}</span> <span class="label">documents</span></span>
						<span><span class="num">{shortNumber(outcome.signalsProduced)}</span> <span class="label">signals</span></span>
					</div>
					<p class="result-q dsp">“{outcome.question}”</p>
					<button class="btn btn-quiet btn-sm" onclick={clearOutcome}>Reset</button>
				</div>
			{/if}
		</div>
	{/if}

	<div class="panes" class:split={selected !== null}>
		<section class="list scroll" aria-label="Ranked companies">
			<LoadingVeil show={loading} label="Scoring the board" />
			{#if error}
				<p class="note warn">{error}</p>
			{:else if loading && rows.length === 0}
				<p class="note"><span class="spinner"></span> Reading the board</p>
			{:else if rows.length === 0}
				<p class="note">Nothing matches this filter.</p>
			{:else}
				<table class="grid">
					<thead>
						<tr>
							<th class="c-rank">{outcome ? 'Was' : '#'}</th>
							{#if outcome}<th class="c-rank">Now</th>{/if}
							<th class="c-co">Company</th>
							<th class="c-fit">Fit</th>
							<th class="c-sig">Strongest signal</th>
							<th class="c-th">Themes</th>
							<th class="c-doc">Docs</th>
							<th class="c-age">Newest</th>
						</tr>
					</thead>
					<tbody>
						{#each rows as row (row.id)}
							{@const move = movements.get(row.id)}
							<tr
								class:active={selected === row.id}
								class:rejected={row.band === 'DISQUALIFIED'}
								onclick={() => select(row.id)}
							>
								{#if outcome}
									<td class="num c-rank dim">{move ? move.beforeRank : '—'}</td>
								{/if}
								<td class="num c-rank">
									{row.rank}
									{#if move && move.delta !== 0}
										<span class="delta" data-dir={move.delta > 0 ? 'up' : 'down'}>
											{move.delta > 0 ? '▲' : '▼'}{Math.abs(move.delta)}
										</span>
									{/if}
								</td>
								<td class="c-co">
									<span class="co">{row.name}</span>
									<span class="sub">
										{#if row.domain}<span class="domain">{row.domain}</span> · {/if}{row.country ?? '—'}{#if row.hqCity} · {row.hqCity}{/if}
									</span>
									<span class="sub dim">{row.industry ?? 'unclassified'}{#if row.sizeBand} · {row.sizeBand}{/if}</span>
									{#if row.leadStatus && row.leadStatus !== 'NEW'}
										<span class="stage-tag" data-stage={row.leadStatus}>
											{row.leadStatusLabel}{#if row.contactCount > 1} ×{row.contactCount}{/if}
										</span>
									{/if}
								</td>
								<td class="c-fit">
									<div class="fitrow">
										<span class="num fitval">{fit(row.probability)}</span>
										<Tag band={row.band} title={row.gateReason ?? row.bandLabel}>{row.bandShort}</Tag>
									</div>
									{#if meta}
										<ScoreMeter
											probability={row.probability}
											band={row.band}
											baseRate={meta.scoring.baseRate}
											thresholds={{
												watch: meta.scoring.watchThreshold,
												nurture: meta.scoring.nurtureThreshold,
												priority: meta.scoring.priorityThreshold
											}}
										/>
									{/if}
									{#if row.gateReason}
										<span class="gate">{row.gateReason}</span>
									{/if}
								</td>
								<td class="c-sig">
									{#if row.strongest}
										<span class="q" class:hot={row.urgency === 'ACT_NOW'}>{row.whyNow}</span>
										<span class="quote">“{row.strongest.quote}”</span>
										<span class="sub dim">
											{row.strongest.publisher} · {ageShort(row.newestSignalAgeDays)}
										</span>
									{:else}
										<span class="sub dim">no signal cleared the floor</span>
									{/if}
								</td>
								<td class="c-th">
									<div class="tags">
										{#each row.themes as name (name)}
											<Tag>{theme(name)}</Tag>
										{/each}
										{#if row.themeCount === 0}
											<span class="sub dim">—</span>
										{/if}
									</div>
									{#if row.themeCount >= 2}
										<span class="corrob">corroborated</span>
									{/if}
								</td>
								<td class="num c-doc">
									{row.evidenceCount}
									{#if row.silentDocuments > 0}
										<span class="sub dim">{row.silentDocuments} silent</span>
									{/if}
								</td>
								<td class="c-age sub">{ageShort(row.newestSignalAgeDays)}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</section>

		{#if selected}
			<aside class="detail scroll" aria-label="Company detail">
				<Scorecard
					companyId={selected}
					{serviceLine}
					onclose={() => select(null)}
					oncapture={(id, focus) => (capture = { id, focus })}
				/>
			</aside>
		{/if}
	</div>
</div>

{#if capture}
	<CaptureSheet
		evidenceId={capture.id}
		focus={capture.focus}
		onclose={() => (capture = null)}
	/>
{/if}

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

	.ident .label {
		margin-top: 3px;
	}

	.tools {
		display: flex;
		align-items: center;
		gap: 8px;
		flex-wrap: wrap;
	}

	.tools :global(.seg button .num) {
		color: var(--ink-3);
		font-size: 10.5px;
	}

	.tools :global(.seg button[aria-selected='true'] .num) {
		color: var(--orange);
	}

	.search {
		width: 210px;
	}

	.tools .select {
		width: auto;
		min-width: 104px;
	}

	.tools #stage {
		min-width: 118px;
	}

	.tools #region {
		min-width: 190px;
	}

	.ask {
		display: flex;
		align-items: flex-end;
		gap: 14px;
		padding: 12px 16px;
		background: var(--orange-wash);
		border-bottom: 1px solid var(--orange-line);
		flex-wrap: wrap;
	}

	.ask-input {
		flex: 1;
		min-width: 300px;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.ask-input .textarea {
		background: var(--surface);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 5px;
	}

	.chips .tag {
		cursor: pointer;
		background: transparent;
		border-color: var(--orange-line);
		color: var(--orange-ink);
	}

	.chips .tag:hover {
		background: var(--surface);
	}

	.ask-opts {
		display: flex;
		align-items: flex-end;
		gap: 8px;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.field .select {
		min-width: 118px;
	}

	.result {
		display: flex;
		align-items: center;
		gap: 20px;
		flex: 1;
		flex-wrap: wrap;
	}

	.result-head {
		display: flex;
		align-items: baseline;
		gap: 7px;
	}

	.big {
		font-size: 26px;
		font-weight: 500;
		line-height: 1;
	}

	.result-stats {
		display: flex;
		gap: 14px;
	}

	.result-stats > span {
		display: flex;
		align-items: baseline;
		gap: 4px;
	}

	.result-stats .num {
		font-size: 13px;
	}

	.result-q {
		font-size: 16px;
		flex: 1;
		min-width: 200px;
		color: var(--ink-2);
	}

	.panes {
		flex: 1;
		min-height: 0;
		display: grid;
		grid-template-columns: minmax(0, 1fr);
	}

	.panes.split {
		grid-template-columns: minmax(0, 1fr) clamp(460px, 40%, 700px);
	}

	.panes.split .c-doc {
		display: none;
	}

	.panes.split .q {
		-webkit-line-clamp: 2;
		line-clamp: 2;
	}

	.panes.split .quote {
		-webkit-line-clamp: 1;
		line-clamp: 1;
	}

	.list {
		position: relative;
		min-height: 0;
		background: var(--surface);
	}

	.detail {
		min-height: 0;
		border-left: 1px solid var(--ink);
		background: var(--surface);
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 18px 16px;
		font-size: 13px;
		color: var(--ink-3);
	}

	.note.warn {
		color: var(--oxblood);
	}

	table.grid {
		table-layout: fixed;
	}

	tbody tr {
		cursor: pointer;
		transition: background var(--ease);
	}

	tbody tr:hover {
		background: var(--paper);
	}

	tbody tr.active {
		background: var(--orange-wash);
		box-shadow: inset 3px 0 0 var(--orange);
	}

	tbody tr.rejected .co {
		color: var(--ink-3);
	}

	tbody tr.rejected .fitval {
		text-decoration: line-through;
		text-decoration-color: var(--oxblood);
		color: var(--ink-3);
	}

	.c-rank {
		width: 52px;
		text-align: right;
		font-size: 12.5px;
		color: var(--ink-3);
	}

	.c-co {
		width: 22%;
	}

	.c-fit {
		width: 144px;
	}

	.c-sig {
		width: auto;
	}

	.c-th {
		width: 152px;
	}

	.c-doc {
		width: 58px;
		text-align: right;
	}

	.c-age {
		width: 62px;
	}

	.co {
		display: block;
		font-family: var(--font-display);
		font-size: 15.5px;
		font-weight: 500;
		line-height: 1.2;
		letter-spacing: -0.01em;
	}

	.sub {
		display: block;
		font-size: 11.5px;
		color: var(--ink-3);
		line-height: 1.4;
		margin-top: 1px;
	}

	.delta {
		display: inline-block;
		margin-left: 5px;
		font-size: 10px;
		font-family: var(--font-ui);
	}

	.delta[data-dir='up'] {
		color: var(--accent);
	}

	.delta[data-dir='down'] {
		color: var(--oxblood);
	}

	.fitrow {
		display: flex;
		align-items: center;
		gap: 7px;
		margin-bottom: 5px;
	}

	.fitval {
		font-size: 17px;
		font-weight: 500;
		line-height: 1;
	}

	.gate {
		display: block;
		margin-top: 5px;
		font-size: 11px;
		line-height: 1.35;
		color: var(--oxblood);
	}

	.q {
		display: block;
		font-size: 12.5px;
		line-height: 1.35;
		color: var(--ink-2);
	}

	.quote {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		margin-top: 3px;
		font-family: var(--font-display);
		font-size: 13px;
		font-style: italic;
		line-height: 1.4;
		color: var(--ink);
	}

	.tags {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.stage-tag {
		display: inline-block;
		margin-top: 4px;
		font-size: 10px;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		padding: 1px 5px;
		border: 1px solid var(--rule-strong);
		color: var(--ink-3);
	}

	.stage-tag[data-stage='QUALIFIED'] {
		border-color: var(--accent);
		color: var(--accent);
	}

	.stage-tag[data-stage='REJECTED'] {
		border-color: var(--oxblood-line);
		color: var(--oxblood);
	}

	.stage-tag[data-stage='CONTACTED'],
	.stage-tag[data-stage='REPLIED'] {
		border-color: var(--orange-line);
		color: var(--orange-ink);
	}

	.q.hot {
		color: var(--orange-ink);
		font-weight: 500;
	}

	.tags :global(.tag) {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.corrob {
		display: block;
		margin-top: 5px;
		font-size: 10px;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--accent);
	}

	.corrob::before {
		content: '✓ ';
	}

	@media (max-width: 1240px) {
		.panes.split {
			grid-template-columns: minmax(0, 1fr) 430px;
		}
		.c-th,
		.c-age {
			display: none;
		}
	}

	@media (max-width: 1000px) {
		.panes.split {
			grid-template-columns: minmax(0, 1fr);
		}
		.detail {
			display: none;
		}
		.c-sig {
			display: none;
		}
	}
</style>
