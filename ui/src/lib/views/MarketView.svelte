<script lang="ts">
	import { fetchBacktest, fetchMarket, type Backtest, type Market, type MarketSlice } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { shortNumber, fit, ageShort } from '$lib/format';
	import Metric from '$lib/ui/Metric.svelte';
	import Metrics from '$lib/ui/Metrics.svelte';
	import LoadingVeil from '$lib/ui/LoadingVeil.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Toolbar from '$lib/ui/Toolbar.svelte';
	import Panes from '$lib/ui/Panes.svelte';
	import Panel from '$lib/ui/Panel.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Note from '$lib/ui/Note.svelte';

	let market = $state<Market | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let axis = $state<'countries' | 'industries' | 'sizeBands'>('countries');
	let backtest = $state<Backtest | null>(null);
	let horizon = $state<'30' | '90' | '180'>('90');

	const slices = $derived(market ? market[axis] : []);
	const busiest = $derived(slices.length > 0 ? slices[0].inMarket : 0);
	const axisLabel = $derived(
		axis === 'countries' ? 'Country' : axis === 'industries' ? 'Industry' : 'Headcount'
	);

	$effect(() => {
		const line = session.serviceLine;
		const window = Number(horizon);
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

<View>
	<Topbar title="Where the demand is moving">
		{#snippet subtitle()}
			{session.serviceLine} · every company in the corpus, grouped by what they have in common, not by what they
			scored. The lead list is the by-product of this view.
		{/snippet}
		{#snippet tools()}
			<Segmented
				bind:value={axis}
				label="Group companies by"
				options={[
					{ value: 'countries', label: 'Country' },
					{ value: 'industries', label: 'Industry' },
					{ value: 'sizeBands', label: 'Size' }
				]}
			/>
		{/snippet}
	</Topbar>

	<Toolbar>
		<Metrics>
			<Metric value={shortNumber(market?.companies ?? 0)} label="Companies indexed" />
			<Metric value={shortNumber(market?.inMarket ?? 0)} label="Worth a look" tone="hot" />
			<Metric value={shortNumber(market?.signals ?? 0)} label="Signals" />
			<Metric
				value={String(market?.blindSpots ?? 0)}
				label="Blind spots"
				tone={(market?.blindSpots ?? 0) > 0 ? 'bad' : 'good'}
			/>
		</Metrics>
	</Toolbar>

	<Panes scroll class="relative">
		<LoadingVeil show={loading} label="Aggregating the corpus" />

		{#if backtest && backtest.companies > 0}
			<Panel title="What the board would have said, and what happened next">
				{#snippet tools()}
					<Segmented
						bind:value={horizon}
						label="Backtest horizon"
						options={[
							{ value: '30', label: '30d' },
							{ value: '90', label: '90d' },
							{ value: '180', label: '180d' }
						]}
					/>
				{/snippet}
				{#if !backtest.powered}
					<Note tone="warn">
						<span>
							<strong>Not enough depth to answer.</strong> {backtest.withEvidence} companies hold a document dated before
							{(backtest.asOf ?? '').slice(0, 10)}, and only {backtest.called}
							{backtest.called === 1 ? 'reaches' : 'reach'} a band on the evidence from before that date. The corpus is
							broad rather than deep — a few hundred companies at roughly seven documents each — so a cutoff this far back
							leaves about two documents per company and almost nobody clears a band on them. Any rate computed from that
							comparison would be a fact about the crawl's depth wearing the costume of an accuracy figure. Crawling more
							documents per company is what would power it.
						</span>
					</Note>
				{/if}
				<Metrics>
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
				</Metrics>
				<p class="type-sub text-ink-4">
					Every document was hidden from the scorer, the board rebuilt as of {new Date(backtest.asOf).toISOString().slice(0, 10)}, then the later documents read back. This tests whether the score precedes the evidence, not whether it precedes a purchase.
				</p>
			</Panel>
		{/if}

		{#if error}
			<Empty>The market could not be built: {error}</Empty>
		{:else if slices.length === 0 && !loading}
			<Empty>Nothing in this service line has enough companies to group yet.</Empty>
		{:else}
			<table class="table-grid">
				<thead>
					<tr>
						<th>{axisLabel}</th>
						<th class="text-center">Companies</th>
						<th class="text-center" title="Companies in the Priority or Nurture band">To call</th>
						<th class="text-center">Signals</th>
						<th class="text-center" title="Companies we hold at least one document for">Evidence</th>
						<th class="text-center" title="Median fit across the group">Med. fit</th>
						<th class="text-center" title="Median age of the newest signal in this group">Newest</th>
						<th class="w-1/8" title="Worth-a-look companies in this group, against the busiest group">Share</th>
					</tr>
				</thead>
				<tbody>
					{#each slices as slice (slice.key)}
						{@const share = busiest === 0 ? 0 : slice.inMarket / busiest}
						<tr>
							<td class="flex items-center gap-2 font-medium">
								{slice.key}
								{#if slice.inMarket === 0 && slice.companies >= 10}
									<Tag tone="bare" title="Enough companies to draw a conclusion from, and none of them worth calling">cold</Tag>
								{/if}
							</td>
							<td class="text-center num">{slice.companies}</td>
							<td class={['text-center num', slice.inMarket > 0 && 'text-orange-ink font-semibold']}>{slice.inMarket}</td>
							<td class="text-center num">{shortNumber(slice.signals)}</td>
							<td class="text-center num">{slice.withEvidence}</td>
							<td class="text-center num">{fit(slice.medianFit)}</td>
							<td class="text-center num">{ageShort(slice.medianAgeDays)}</td>
							<td>
								<span class="block h-2 min-w-px rounded-sm bg-orange" style:width={`${Math.round(share * 100)}%`}></span>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{/if}
	</Panes>
</View>
