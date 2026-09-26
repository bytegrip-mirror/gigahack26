<script lang="ts">
	import { fetchMarket, type Market } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { shortNumber, ageShort } from '$lib/format';
	import Metric from '$lib/components/Metric.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Tag from '$lib/components/Tag.svelte';

	let market = $state<Market | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let axis = $state<'countries' | 'industries' | 'sizeBands'>('countries');

	const slices = $derived(market ? market[axis] : []);
	const busiest = $derived(slices.length > 0 ? slices[0].inMarket : 0);
	const axisLabel = $derived(
		axis === 'countries' ? 'Country' : axis === 'industries' ? 'Industry' : 'Headcount'
	);

	$effect(() => {
		const line = session.serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		fetchMarket(line)
			.then((result) => {
				if (cancelled) return;
				market = result;
				error = null;
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
			/>
		</div>
	</div>

	<div class="panes">
		<div class="scroll">
			<LoadingVeil show={loading} label="Aggregating the corpus" />

			{#if error}
				<div class="empty">The market could not be built: {error}</div>
			{:else if slices.length === 0 && !loading}
				<div class="empty">Nothing in this service line has enough companies to group yet.</div>
			{:else}
				<table class="tbl">
					<thead>
						<tr>
							<th>{axisLabel}</th>
							<th class="m">Companies</th>
							<th class="m" title="Companies in the Priority or Nurture band">To call</th>
							<th class="m">Signals</th>
							<th class="m" title="Companies we hold at least one document for">Evidence</th>
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
		.topbar {
		gap: var(--spacing-4);
	}

	.c-name {
		font-weight: 500;
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
	}

	.hot {
		color: var(--color-orange-ink);
		font-weight: 600;
	}

	.c-bar {
		width: 120px;
	}

	.bar {
		display: block;
		height: 6px;
		background: var(--color-orange);
		border-radius: 1px;
		min-width: 1px;
	}

	.seg-btn {
		font: inherit;
		font-size: var(--text-sm);
		padding: 2px var(--spacing-3);
		border: 0;
		background: transparent;
		color: var(--color-ink-2);
		cursor: pointer;
	}

	.seg-btn.on {
		background: var(--color-surface);
		color: var(--color-ink);
		font-weight: 600;
	}</style>
