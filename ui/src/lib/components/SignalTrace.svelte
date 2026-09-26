<script lang="ts">
	let { spark, title = '' }: { spark: number[]; title?: string } = $props();

	const id = $props.id();
	const WIDTH = 100;
	const HEIGHT = 20;
	const PAD = 1.5;

	/**
	 * Monotone cubic rather than a plain Catmull-Rom: it interpolates the points
	 * without overshooting them, so a month with three readings stays a peak instead
	 * of turning into a wave that dips below its neighbours.
	 */
	function monotone(values: number[]): string {
		const points = values.map((value, index) => ({
			x: (index / Math.max(1, values.length - 1)) * WIDTH,
			y: HEIGHT - PAD - value * (HEIGHT - PAD * 2)
		}));
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

	const drawn = $derived.by(() => {
		const series = spark.length > 0 ? spark : [0];
		const peak = Math.max(...series, 1);
		return monotone(series.map((value) => value / peak));
	});

	const area = $derived(drawn ? `${drawn} L${WIDTH} ${HEIGHT} L0 ${HEIGHT} Z` : '');
</script>

<span class="wrap" {title}>
	<svg
		class="trace"
		viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
		preserveAspectRatio="none"
		aria-hidden="true"
	>
		<defs>
			<linearGradient id={`fade-${id}`} x1="0" y1="0" x2="0" y2="1">
				<stop offset="0%" stop-color="var(--color-orange)" stop-opacity="0.42" />
				<stop offset="100%" stop-color="var(--color-orange)" stop-opacity="0.04" />
			</linearGradient>
		</defs>
		<path class="fill" d={area} fill={`url(#fade-${id})`} />
		<path class="line" d={drawn} />
	</svg>
</span>

<style>
	.wrap {
		display: block;
		width: 100%;
		line-height: 0;
	}

	.trace {
		display: block;
		width: 100%;
		height: 20px;
	}

	.line {
		fill: none;
		stroke: var(--color-orange);
		stroke-width: 1.25;
		stroke-linecap: round;
		stroke-linejoin: round;
		vector-effect: non-scaling-stroke;
	}
</style>
