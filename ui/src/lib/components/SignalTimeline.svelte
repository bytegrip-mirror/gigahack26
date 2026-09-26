<script lang="ts">
	import type { LedgerRow } from '$lib/api';
	import { date } from '$lib/format';

	let { rows, note = '' }: { rows: LedgerRow[]; note?: string } = $props();

	const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
	const DAY = 86_400_000;

	/** Sized for the company pane it lives in; the whole drawing scales with it. */
	const W = 440;
	const H = 190;
	const PAD = { top: 16, right: 12, bottom: 28, left: 38 };

	/**
	 * Every signal in the ledger is a point: where it sits in time, and what it was
	 * worth to the score. The baseline is zero, so a company with nothing counting
	 * against it gets the whole height above the line rather than half of it wasted
	 * — a chart that reserves space for values that are not there reads as if they
	 * were.
	 */
	const plotted = $derived.by(() => {
		const dated = rows
			.filter((row) => !row.blocking && row.anchor)
			.map((row) => ({ row, at: new Date(row.anchor).getTime(), value: row.contributionNats }))
			.filter((entry) => Number.isFinite(entry.at));
		if (dated.length === 0) return null;

		const times = dated.map((entry) => entry.at);
		const values = dated.map((entry) => entry.value);
		let from = Math.min(...times);
		let to = Math.max(...times);
		const MONTH = 30 * DAY;
		if (to - from < 3 * MONTH) {
			const middle = (from + to) / 2;
			from = middle - 1.5 * MONTH;
			to = middle + 1.5 * MONTH;
		}
		if (to - from > 18 * MONTH) {
			from = to - 18 * MONTH;
		}

		const high = Math.max(0, ...values);
		const low = Math.min(0, ...values);
		const span = Math.max(0.04, high - low);
		const top = high + span * 0.14;
		const bottom = low - span * 0.14;

		const x = (at: number) => PAD.left + ((at - from) / (to - from)) * (W - PAD.left - PAD.right);
		const y = (value: number) => PAD.top + ((top - value) / (top - bottom)) * (H - PAD.top - PAD.bottom);

		const ticks: { x: number; label: string }[] = [];
		const first = new Date(from);
		const cursor = new Date(first.getFullYear(), first.getMonth(), 1);
		while (cursor.getTime() <= to) {
			if (cursor.getTime() >= from) {
				ticks.push({ x: x(cursor.getTime()), label: MONTHS[cursor.getMonth()] });
			}
			cursor.setMonth(cursor.getMonth() + 1);
		}

		const counts = dated.filter((entry) => entry.row.polarity === 'POSITIVE').length;
		return {
			ticks,
			zero: y(0),
			high: y(high),
			low: y(low),
			count: dated.length,
			counts,
			against: dated.length - counts,
			first: new Date(Math.min(...times)),
			last: new Date(Math.max(...times)),
			dots: dated.map((entry) => ({
				cx: x(entry.at),
				cy: y(entry.value),
				negative: entry.row.polarity !== 'POSITIVE',
				label: `${entry.row.questionTitle} · ${entry.value >= 0 ? '+' : ''}${entry.value.toFixed(2)} nats`
			}))
		};
	});
</script>

{#if plotted}
	<figure class="timeline">
		<svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${plotted.count} signals between ${date(plotted.first.toISOString())} and ${date(plotted.last.toISOString())}`}>
			<line class="grid" x1={PAD.left} x2={W - PAD.right} y1={plotted.high} y2={plotted.high} />
			<line class="grid" x1={PAD.left} x2={W - PAD.right} y1={plotted.low} y2={plotted.low} />

			{#each plotted.ticks as tick (tick.x)}
				<line class="grid month" x1={tick.x} x2={tick.x} y1={PAD.top} y2={H - PAD.bottom} />
				<text class="tick" x={tick.x} y={H - PAD.bottom + 13}>{tick.label}</text>
			{/each}

			<line class="zero" x1={PAD.left} x2={W - PAD.right} y1={plotted.zero} y2={plotted.zero} />
			<text class="axis" x={PAD.left - 6} y={plotted.zero + 3.5}>0</text>
			{#if plotted.high < plotted.zero - 4}
				<text class="axis" x={PAD.left - 6} y={plotted.high + 3.5}>{plotted.high.toFixed(1)}</text>
			{/if}

			{#each plotted.dots as dot (dot.cx + ':' + dot.cy + ':' + dot.negative)}
				<circle class="dot" class:against={dot.negative} cx={dot.cx} cy={dot.cy} r="4.5">
					<title>{dot.label}</title>
				</circle>
			{/each}
		</svg>

		<figcaption class="sub dim">
			<span class="key"><span class="mark for"></span> counts for · {plotted.counts}</span>
			<span class="key"><span class="mark against"></span> counts against · {plotted.against}</span>
			<span class="span">
				{plotted.count} {plotted.count === 1 ? 'signal' : 'signals'}, {date(plotted.first.toISOString())} to {date(
					plotted.last.toISOString()
				)}{#if note}
					· {note}{/if}
			</span>
		</figcaption>
	</figure>
{:else}
	<p class="sub dim">No dated evidence to plot yet.</p>
{/if}

<style>
	.timeline {
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
	}

	svg {
		display: block;
		width: 100%;
		height: auto;
	}

	/* Hairlines one shade off the surface, solid: the grid is there to be read
	   against, not looked at. */
	.grid {
		stroke: var(--color-rule);
		stroke-width: 1;
	}

	.grid.month {
		stroke: var(--color-rule);
		opacity: 0.6;
	}

	/* Zero is the one line worth seeing, because the sign of a value is the point. */
	.zero {
		stroke: var(--color-ink-3);
		stroke-width: 1;
	}

	.tick,
	.axis {
		font-family: var(--font-ui);
		font-size: 9px;
		fill: var(--color-ink-3);
		text-anchor: middle;
	}

	.axis {
		text-anchor: end;
	}

	/* A surface ring keeps two signals on the same day legible as two. */
	.dot {
		fill: var(--color-orange);
		stroke: var(--color-surface);
		stroke-width: 2;
	}

	.dot.against {
		fill: var(--color-oxblood);
	}

	figcaption {
		display: flex;
		flex-wrap: wrap;
		align-items: baseline;
		gap: var(--spacing-3);
	}

	.key {
		display: inline-flex;
		align-items: center;
		gap: 6px;
	}

	.key .mark {
		width: 9px;
		height: 9px;
		border-radius: 50%;
		background: var(--color-orange);
	}

	.key .mark.against {
		background: var(--color-oxblood);
	}

	.span {
		flex-basis: 100%;
	}
</style>
