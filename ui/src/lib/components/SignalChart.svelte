<script lang="ts">
	let { spark, labels = [] }: { spark: number[]; labels?: string[] } = $props();

	const id = $props.id();
	const WIDTH = 520;
	const HEIGHT = 130;
	const LEFT = 26;
	const RIGHT = 8;
	const TOP = 10;
	const BOTTOM = 22;

	const plot = { w: WIDTH - LEFT - RIGHT, h: HEIGHT - TOP - BOTTOM };

	const series = $derived(spark.length > 0 ? spark : [0]);
	const peak = $derived(Math.max(...series, 1));
	const step = $derived(plot.w / Math.max(1, series.length - 1));

	const points = $derived(
		series.map((value, index) => ({
			x: LEFT + index * step,
			y: TOP + plot.h - (value / peak) * plot.h,
			value
		}))
	);

	function monotone(): string {
		if (points.length < 2) return '';
		const slopes: number[] = [];
		for (let i = 0; i < points.length - 1; i++) {
			slopes.push((points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x || 1));
		}
		const tangents = points.map((_, i) => {
			if (i === 0) return slopes[0];
			if (i === points.length - 1) return slopes[slopes.length - 1];
			const before = slopes[i - 1];
			const after = slopes[i];
			if (before * after <= 0) return 0;
			return (2 * before * after) / (before + after);
		});

		let path = `M${points[0].x.toFixed(2)} ${points[0].y.toFixed(2)}`;
		for (let i = 0; i < points.length - 1; i++) {
			const width = points[i + 1].x - points[i].x;
			const c1x = points[i].x + width / 3;
			const c1y = points[i].y + (tangents[i] * width) / 3;
			const c2x = points[i + 1].x - width / 3;
			const c2y = points[i + 1].y - (tangents[i + 1] * width) / 3;
			path += ` C${c1x.toFixed(2)} ${c1y.toFixed(2)} ${c2x.toFixed(2)} ${c2y.toFixed(2)} ${points[i + 1].x.toFixed(2)} ${points[i + 1].y.toFixed(2)}`;
		}
		return path;
	}

	const drawn = $derived(monotone());
	const area = $derived(drawn ? `${drawn} L${LEFT + plot.w} ${TOP + plot.h} L${LEFT} ${TOP + plot.h} Z` : '');
	const ticks = $derived(peak <= 1 ? [0, 1] : [0, Math.round(peak / 2), Math.round(peak)]);
</script>

<svg class="chart" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} width={WIDTH} height={HEIGHT} role="img">
	<defs>
		<linearGradient id={`chart-fade-${id}`} x1="0" y1="0" x2="0" y2="1">
			<stop offset="0%" stop-color="var(--color-orange)" stop-opacity="0.34" />
			<stop offset="100%" stop-color="var(--color-orange)" stop-opacity="0.03" />
		</linearGradient>
	</defs>

	{#each ticks as tick (tick)}
		<line
			class="grid"
			x1={LEFT}
			y1={TOP + plot.h - (tick / peak) * plot.h}
			x2={LEFT + plot.w}
			y2={TOP + plot.h - (tick / peak) * plot.h}
		/>
		<text class="tick" x={LEFT - 6} y={TOP + plot.h - (tick / peak) * plot.h + 3} text-anchor="end">{tick}</text>
	{/each}

	<path class="fill" d={area} fill={`url(#chart-fade-${id})`} />
	<path class="line" d={drawn} />

	{#each points as point, index (index)}
		{#if point.value > 0}
			<circle class="point" cx={point.x} cy={point.y} r="2.4" />
		{/if}
	{/each}

	<line class="axis" x1={LEFT} y1={TOP + plot.h} x2={LEFT + plot.w} y2={TOP + plot.h} />

	{#each points as point, index (index)}
		{#if labels[index]}
			<text class="tick" x={point.x} y={HEIGHT - 6} text-anchor="middle">{labels[index]}</text>
		{/if}
	{/each}
</svg>

<style>
	.chart {
		display: block;
		width: 100%;
		height: auto;
	}

	.grid {
		stroke: var(--color-rule);
		stroke-width: 1;
	}

	.axis {
		stroke: var(--color-rule-strong);
		stroke-width: 1;
	}

	.line {
		fill: none;
		stroke: var(--color-orange);
		stroke-width: 1.75;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	.point {
		fill: var(--color-orange);
		stroke: var(--color-surface);
		stroke-width: 1;
	}

	.tick {
		font-size: 9px;
		font-variant-numeric: tabular-nums;
		fill: var(--color-ink-4);
	}
</style>
