<script lang="ts">
	import { fetchBacktest, fetchMarket, type Backtest, type Market, type MarketSlice } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { shortNumber, fit, ageShort } from '$lib/format';
	import Metric from '$lib/components/Metric.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Tag from '$lib/components/Tag.svelte';

	let market = $state<Market | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let axis = $state<'countries' | 'industries' | 'sizeBands'>('countries');
	let backtest = $state<Backtest | null>(null);
	let horizon = $state(90);

	const slices = $derived(market ? market[axis] : []);
	const busiest = $derived(slices.length > 0 ? slices[0].inMarket : 0);
	const axisLabel = $derived(
		axis === 'countries' ? 'Country' : axis === 'industries' ? 'Industry' : 'Headcount'
	);

	$effect(() => {
		const line = session.serviceLine;
		const window = horizon;
		if (!line) return;
		let cancelled = false;
		loading = true;
		fetchMarket(line)
			.then((result) => {
				if (cancelled) return;
				market = result;
				error = null;
			})
			.catch(() => {});
		fetchBacktest(line, window)
			.then((result) => {
				if (!cancelled) backtest = result;
			})
			.catch((cause) => {
				if (!cancelled) error = (cause as Error).message;
			})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Where the demand is moving</h1>
			<p class="sub">
				{session.serviceLine} · every company in the corpus, grouped by what they have in common, not by what they
				scored. The lead list is the by-product of this view.
			</p>
		</div>
		<div class="topbar-tools">
			<div class="seg">
				<button class="seg-btn" class:on={axis === 'countries'} onclick={() => (axis = 'countries')}>Country</button>
				<button class="seg-btn" class:on={axis === 'industries'} onclick={() => (axis = 'industries')}>Industry</button>
				<button class="seg-btn" class:on={axis === 'sizeBands'} onclick={() => (axis = 'sizeBands')}>Size</button>
			</div>
		</div>
	</header>

	<div class="toolbar">
		<div class="metrics">
			<Metric value={shortNumber(market?.companies ?? 0)} label="Companies indexed" />
			<Metric value={shortNumber(market?.inMarket ?? 0)} label="Worth a look" tone="hot" />
			<Metric value={shortNumber(market?.signals ?? 0)} label="Signals" />
			<Metric
				value={String(market?.blindSpots ?? 0)}
				label="Blind spots"
				tone={(market?.blindSpots ?? 0) > 0 ? 'bad' : 'good'}
				hint="Companies we hold no document for. They are counted rather than scored."
			/>
		</div>
	</div>

	<div class="panes">
		<div class="scroll">
			<LoadingVeil show={loading} label="Aggregating the corpus" />

			{#if backtest && backtest.companies > 0}
				<section class="panel backtest">
					<div class="panel-head">
						<h2 class="dsp">What the board would have said, and what happened next</h2>
						<div class="seg">
							{#each [30, 90, 180] as days (days)}
								<button class="seg-btn" class:on={horizon === days} onclick={() => (horizon = days)}>
									{days}d
								</button>
							{/each}
						</div>
					</div>
					{#if !backtest.powered}
						<p class="note warn">
							<strong>Not enough depth to answer.</strong> {backtest.withEvidence} companies hold a document
							dated before {(backtest.asOf ?? '').slice(0, 10)}, and only {backtest.called}
							{backtest.called === 1 ? 'reaches' : 'reach'} a band on the evidence from before that date. The
							corpus is broad rather than deep — a few hundred companies at roughly seven documents each — so a
							cutoff this far back leaves about two documents per company and almost nobody clears a band on them.
							Any rate computed from that comparison would be a fact about the crawl's depth wearing the costume of
							an accuracy figure. Crawling more documents per company is what would power it.
						</p>
					{/if}
					<div class="metrics">
						<Metric value={String(backtest.called)} label="It would have called" tone="hot" />
						<Metric
							value={backtest.calledRate != null ? (backtest.calledRate * 100).toFixed(0) + '%' : '—'}
							label="Went on to produce the evidence"
							tone="good"
						/>
						<Metric value={String(backtest.control)} label="It would not have" />
						<Metric
							value={backtest.controlRate != null ? (backtest.controlRate * 100).toFixed(0) + '%' : '—'}
							label="Also went on to"
						/>
						<Metric
							value={backtest.lift != null ? backtest.lift.toFixed(1) + '×' : '—'}
							label="Lift over not calling"
							tone={(backtest.lift ?? 0) > 1 ? 'good' : 'bad'}
						/>
					</div>
					<p class="sub dim">
						Every document the corpus holds was hidden from the scorer, the board was rebuilt as of
						{new Date(backtest.asOf).toISOString().slice(0, 10)}, and then the documents that arrived after that date were
						read back. This tests whether the score <em>precedes</em> the evidence — the corpus confirming itself — and not
						whether it precedes a purchase, which only a sales team that has used the system could show.
					</p>
				</section>
			{/if}

			{#if error}
				<div class="empty">The market could not be built: {error}</div>
			{:else if slices.length === 0 && !loading}
				<div class="empty">Nothing in this service line has enough companies to group yet.</div>
			{:else}
				<table class="grid">
					<thead>
						<tr>
							<th>{axisLabel}</th>
							<th class="m">Companies</th>
							<th class="m" title="Companies in the Priority or Nurture band">To call</th>
							<th class="m">Signals</th>
							<th class="m" title="Companies we hold at least one document for">Evidence</th>
							<th class="m" title="Median fit across the group">Med. fit</th>
							<th class="m" title="Median age of the newest signal in this group">Newest</th>
							<th title="Worth-a-look companies in this group, against the busiest group">Share</th>
						</tr>
					</thead>
					<tbody>
						{#each slices as slice (slice.key)}
							{@const share = busiest === 0 ? 0 : slice.inMarket / busiest}
							<tr>
								<td class="c-name">
									{slice.key}
									{#if slice.inMarket === 0 && slice.companies >= 10}
										<Tag variant="bare" title="Enough companies to draw a conclusion from, and none of them worth calling">cold</Tag>
									{/if}
								</td>
								<td class="m num">{slice.companies}</td>
								<td class="m num" class:hot={slice.inMarket > 0}>{slice.inMarket}</td>
								<td class="m num">{shortNumber(slice.signals)}</td>
								<td class="m num">{slice.withEvidence}</td>
								<td class="m num">{fit(slice.medianFit)}</td>
								<td class="m num">{ageShort(slice.medianAgeDays)}</td>
								<td class="c-bar">
									<span class="bar" style={`width:${Math.round(share * 100)}%`}></span>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</div>
	</div>
</div>

<style>
	.backtest {
		margin-bottom: var(--sp-3);
	}

	.topbar {
		gap: var(--sp-4);
	}

	.c-name {
		font-weight: 500;
		display: flex;
		align-items: center;
		gap: var(--sp-2);
	}

	.hot {
		color: var(--orange-ink);
		font-weight: 600;
	}

	.c-bar {
		width: 120px;
	}

	.bar {
		display: block;
		height: 6px;
		background: var(--orange);
		border-radius: 1px;
		min-width: 1px;
	}

	.seg-btn {
		font: inherit;
		font-size: var(--fs-sm);
		padding: 2px var(--sp-3);
		border: 0;
		background: transparent;
		color: var(--ink-2);
		cursor: pointer;
	}

	.seg-btn.on {
		background: var(--surface);
		color: var(--ink);
		font-weight: 600;
	}
</style>
