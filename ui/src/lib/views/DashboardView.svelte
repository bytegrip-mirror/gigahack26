<script lang="ts">
	import NotificationBell from '$lib/components/NotificationBell.svelte';
	import {
		fetchBoard,
		fetchIcpProfiles,
		fetchPeople,
		fetchTelemetry,
		type BoardRow,
		type Telemetry
	} from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { session } from '$lib/session.svelte';
	import { fitWord } from '$lib/format';
	import Icon from '$lib/components/Icon.svelte';
	import type { IconName } from '$lib/components/icons';
	import Bars from '$lib/components/Bars.svelte';
	import CompanyMark from '$lib/components/CompanyMark.svelte';
	import Tag from '$lib/components/Tag.svelte';

	/** The desk is set up for one person; this is where it says so. */
	const USER = 'Cristian';
	const SHOWN = 6;

	let rows = $state<BoardRow[]>([]);
	let people = $state(0);
	let profiles = $state(0);
	let telemetry = $state<Telemetry | null>(null);

	const status = $derived(workspace.status);
	const meta = $derived(workspace.meta);
	const bands = $derived(meta?.bands ?? {});
	const thresholds = $derived({
		watch: meta?.scoring.watchThreshold ?? 0.28,
		nurture: meta?.scoring.nurtureThreshold ?? 0.45,
		priority: meta?.scoring.priorityThreshold ?? 0.55
	});

	const queue = $derived(rows.filter((row) => row.band !== 'DISQUALIFIED' && row.band !== 'THIN'));
	const pick = $derived(queue[0] ?? null);
	const next = $derived(queue.slice(0, SHOWN));

	const monthly = $derived.by(() => {
		const sums = new Array<number>(12).fill(0);
		for (const row of rows) row.spark.forEach((value, index) => (sums[index] += value));
		return sums;
	});
	const thisMonth = $derived(monthly[11] ?? 0);
	const lastMonth = $derived(monthly[10] ?? 0);
	const change = $derived(lastMonth === 0 ? null : Math.round(((thisMonth - lastMonth) / lastMonth) * 100));

	const months = $derived.by(() => {
		const now = Date.now();
		return monthly.map((_, index) =>
			new Date(now - (11 - index) * 30 * 86_400_000).toLocaleDateString(undefined, { month: 'short' })
		);
	});

	const fitBars = $derived([
		{ label: 'Strong', value: bands.PRIORITY ?? 0 },
		{ label: 'Good', value: bands.NURTURE ?? 0 },
		{ label: 'Fair', value: bands.WATCH ?? 0 },
		{ label: 'Unproven', value: bands.THIN ?? 0 },
		{ label: 'Out', value: bands.DISQUALIFIED ?? 0 }
	]);

	const coverage = $derived(telemetry?.coverage ?? null);
	const read = $derived(coverage ? Math.round((coverage.withDocuments / Math.max(1, coverage.companies)) * 100) : null);

	type Kpi = { icon: IconName; title: string; value: string; unit: string; foot: string; href: string };
	const kpis = $derived<Kpi[]>([
		{
			icon: 'target',
			title: 'Ready to call',
			value: exact(bands.PRIORITY ?? 0),
			unit: 'companies',
			foot: 'Strong fit',
			href: '/companies'
		},
		{
			icon: 'star',
			title: 'Warm',
			value: exact(bands.NURTURE ?? 0),
			unit: 'companies',
			foot: 'Good fit, keep close',
			href: '/companies'
		},
		{
			icon: 'building',
			title: 'Contacts',
			value: exact(people),
			unit: 'people',
			foot: 'Named and ready',
			href: '/people'
		},
		{
			icon: 'bell',
			title: 'New signals',
			value: exact(thisMonth),
			unit: 'this month',
			foot: change === null ? 'No signals last month' : `${change >= 0 ? '+' : ''}${change}% vs last month`,
			href: '/companies'
		},
		{
			icon: 'layers',
			title: 'ICP Profiles',
			value: exact(profiles),
			unit: 'profiles',
			foot: 'Books of business',
			href: '/icp'
		}
	]);

	/** A headline figure is read, not skimmed: 4,184 says more than 4.2k does. */
	function exact(value: number): string {
		return value.toLocaleString('en-US');
	}

	function reason(row: BoardRow): string {
		return row.strongest?.questionTitle ?? row.chips[0]?.questionTitle ?? 'No reason yet';
	}

	function hours(value: number | null | undefined): string {
		if (value === null || value === undefined) return '—';
		if (value < 24) return `${Math.max(1, Math.round(value))}h ago`;
		return `${Math.round(value / 24)}d ago`;
	}

	$effect(() => {
		fetchTelemetry()
			.then((result) => (telemetry = result))
			.catch(() => (telemetry = null));
	});

	$effect(() => {
		const line = session.serviceLine;
		const region = session.region;
		if (!line) return;
		let cancelled = false;
		fetchBoard({ serviceLine: line, region })
			.then((list) => {
				if (!cancelled) rows = list;
			})
			.catch(() => {
				if (!cancelled) rows = [];
			});
		fetchPeople(line)
			.then((list) => {
				if (!cancelled) people = list.length;
			})
			.catch(() => {
				if (!cancelled) people = 0;
			});
		// Every profile, not just the ones switched on: how many books exist is a
		// different question from how many the board is showing.
		fetchIcpProfiles()
			.then((list) => {
				if (!cancelled) profiles = list.length;
			})
			.catch(() => {
				if (!cancelled) profiles = 0;
			});
		return () => {
			cancelled = true;
		};
	});
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Welcome, {USER}</h1>
			<p class="sub">{session.serviceLine} · {session.region === 'world' ? 'Worldwide' : session.region}</p>
		</div>
		<div class="topbar-tools">
			<a class="btn btn-primary" href="/companies">
				<Icon name="board" size={15} />
				Open the board
			</a>
			<NotificationBell />
		</div>
	</header>

	<div class="body scroll">
		<div class="dash">
			<div class="kpis">
				{#each kpis as kpi (kpi.title)}
					<a class="card kpi" href={kpi.href}>
						<span class="card-head">
							<span class="chip"><Icon name={kpi.icon} size={15} /></span>
							<span class="card-title">{kpi.title}</span>
						</span>
						<span class="figure">
							<span class="big num">{kpi.value}</span>
							<span class="unit">{kpi.unit}</span>
						</span>
						<span class="foot">{kpi.foot}</span>
					</a>
				{/each}
			</div>

			<section class="card list">
				<div class="card-head">
					<h2 class="card-title">Call these first</h2>
					<a class="go" href="/companies" aria-label="Open the board"><Icon name="external" size={15} /></a>
				</div>
				{#if next.length === 0}
					<p class="sub">Nobody is ready yet.</p>
				{:else}
					<table class="calls">
						<thead>
							<tr>
								<th>Company</th>
								<th>Fit</th>
								<th class="why">Why</th>
								<th>Stage</th>
							</tr>
						</thead>
						<tbody>
							{#each next as row (row.id)}
								<tr>
									<td>
										<a class="who" href={`/companies?co=${row.id}`}>
											<CompanyMark name={row.name} domain={row.domain} />
											<span class="name">{row.name}</span>
										</a>
									</td>
									<td><Tag band={row.band}>{fitWord(row.probability, thresholds)}</Tag></td>
									<td class="why">{reason(row)}</td>
									<td class="stage">{row.leadStatusLabel ?? 'New'}</td>
								</tr>
							{/each}
						</tbody>
					</table>
				{/if}
			</section>

			<section class="card pick">
				<span class="card-head">
					<span class="chip chip-ink"><Icon name="star" size={15} /></span>
					<span class="card-title">Top pick today</span>
				</span>
				{#if pick}
					<span class="pick-name dsp">{pick.name}</span>
					<span class="pick-why">{pick.whyNow ?? reason(pick)}</span>
					<a class="btn btn-ink pick-go" href={`/companies?co=${pick.id}`}>
						Open company
						<Icon name="chevronRight" size={14} />
					</a>
				{:else}
					<span class="pick-why">Nobody is ready yet.</span>
				{/if}
			</section>

			<section class="card facts-card">
				<div class="card-head">
					<h2 class="card-title">What we read</h2>
				</div>
				<dl class="facts">
					<dt>Documents</dt>
					<dd class="num">{exact(status?.captures ?? 0)}</dd>
					<dt>Companies covered</dt>
					<dd class="num">{read === null ? '—' : `${read}%`}</dd>
					<dt>Newest read</dt>
					<dd class="num">{hours(telemetry?.freshness?.newestCaptureAgeHours)}</dd>
					<dt>Reader</dt>
					<dd>{status?.languageModelAvailable ? 'AI model' : 'Local rules'}</dd>
				</dl>
			</section>

			<section class="card chart">
				<div class="card-head">
					<h2 class="card-title">Signals per month</h2>
				</div>
				<span class="figure">
					<span class="big num">{exact(monthly.reduce((a, b) => a + b, 0))}</span>
					<span class="unit">in 12 months</span>
				</span>
				<div class="plot"><Bars values={monthly} labels={months} /></div>
			</section>

			<section class="card chart">
				<div class="card-head">
					<h2 class="card-title">Companies by fit</h2>
				</div>
				<span class="figure">
					<span class="big num">{exact(status?.companies ?? 0)}</span>
					<span class="unit">watched</span>
				</span>
				<div class="plot">
					<Bars values={fitBars.map((bar) => bar.value)} labels={fitBars.map((bar) => bar.label)} highlight={0} />
				</div>
			</section>

		</div>

		{#if status && (status.postgres !== 'up' || status.mongo !== 'up')}
			<p class="note warn">
				<Icon name="alert" size={14} />
				<span>A database is down. Numbers can be old.</span>
			</p>
		{/if}
	</div>
</div>

<style>
	.body {
		flex: 1;
		min-height: 0;
		padding: var(--spacing-4);
		display: flex;
		flex-direction: column;
		gap: var(--spacing-4);
	}

	.dash {
		display: grid;
		grid-template-columns: repeat(12, minmax(0, 1fr));
		gap: var(--spacing-3);
	}

	.card {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-3);
		padding: var(--spacing-4);
		min-width: 0;
		background: var(--color-surface);
		border: 1px solid var(--color-rule);
	}

	/*
	 * The figures keep one row whatever their number. They were tiles in the
	 * twelve-column grid spanning three columns each, which is four across — so a
	 * fifth stranded itself in the first column of the next row and pushed the
	 * list and the cards after it out of shape.
	 */
	.kpis {
		grid-column: 1 / -1;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: var(--spacing-3);
	}

	.kpi {
		color: var(--color-ink);
	}

	.kpi:hover {
		text-decoration: none;
		border-color: var(--color-ink);
	}

	.list {
		grid-column: span 8;
		grid-row: span 2;
	}

	.pick {
		grid-column: span 4;
		background: var(--color-orange);
		border-color: var(--color-orange);
		color: var(--color-on-fill);
	}

	.facts-card {
		grid-column: span 4;
	}

	.chart {
		grid-column: span 6;
		min-height: 200px;
	}

	@media (max-width: 1100px) {
		.list,
		.pick,
		.facts-card,
		.chart {
			grid-column: span 12;
			grid-row: auto;
		}

		.calls .why {
			display: none;
		}
	}

	.card-head {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
	}

	.card-title {
		flex: 1;
		font-size: var(--text-md);
		font-weight: 700;
		color: var(--color-ink);
	}

	.pick .card-title {
		color: var(--color-on-fill);
	}

	.chip {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: var(--spacing-ctl);
		height: var(--spacing-ctl);
		background: var(--color-orange-wash);
		color: var(--color-orange-ink);
	}

	.chip-ink {
		background: var(--color-on-fill);
		color: var(--color-orange);
	}

	.go {
		color: var(--color-ink-3);
	}

	.go:hover {
		color: var(--color-orange);
	}

	.figure {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}

	.big {
		font-family: var(--font-display);
		font-size: 30px;
		font-weight: 700;
		line-height: 1;
		color: var(--color-ink);
	}

	.kpi:first-child .big {
		color: var(--color-orange-ink);
	}

	.unit {
		font-size: var(--text-base);
		color: var(--color-ink-3);
	}

	.foot {
		font-size: var(--text-sm);
		color: var(--color-ink-3);
		border-top: 1px solid var(--color-rule);
		padding-top: var(--spacing-2);
	}

	.calls {
		width: 100%;
		border-collapse: collapse;
		table-layout: fixed;
	}

	.calls th {
		text-align: left;
		font-size: var(--text-micro);
		font-weight: 700;
		color: var(--color-ink-3);
		padding: 0 var(--spacing-2) var(--spacing-2);
		border-bottom: 1px solid var(--color-rule);
	}

	.calls th:first-child {
		width: 38%;
	}

	.calls th:nth-child(2),
	.calls th:last-child {
		width: 14%;
	}

	.calls td {
		height: var(--spacing-bar);
		padding: 0 var(--spacing-2);
		border-bottom: 1px solid var(--color-rule);
		font-size: var(--text-base);
		color: var(--color-ink-2);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.calls tr:last-child td {
		border-bottom: 0;
	}

	.who {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		color: var(--color-ink);
		font-weight: 700;
		min-width: 0;
	}

	.who:hover {
		color: var(--color-orange);
		text-decoration: none;
	}

	.name {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.why {
		color: var(--color-ink-3);
	}

	.pick-name {
		font-size: var(--text-title);
		line-height: 1.1;
	}

	.pick-why {
		font-size: var(--text-md);
		line-height: 1.45;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.pick-go {
		align-self: flex-start;
		margin-top: auto;
	}

	.plot {
		flex: 1;
		min-height: 80px;
	}

	.facts {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: var(--spacing-2) var(--spacing-3);
		margin: 0;
		font-size: var(--text-base);
	}

	.facts dt {
		color: var(--color-ink-3);
	}

	.facts dd {
		margin: 0;
		text-align: right;
		font-weight: 700;
		color: var(--color-ink);
	}

	.note {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}
</style>
