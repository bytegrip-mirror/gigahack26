<script lang="ts">
	let { spark, title = '' }: { spark: number[]; title?: string } = $props();

	const WIDTH = 84;
	const HEIGHT = 20;
	const PAD = 2;

	/**
	 * A reading is a moment, not a month, so the series is blurred before it is
	 * drawn: two readings a week apart and two a month apart are the same amount of
	 * activity and end up the same shape, instead of one being a spike and the other
	 * a pair of half-height steps.
	 */
	function blurred(values: number[]): number[] {
		if (values.length < 3) return values;
		const kernel = [1, 4, 6, 4, 1];
		return values.map((_, index) => {
			let total = 0;
			let weight = 0;
			for (let tap = 0; tap < kernel.length; tap++) {
				const at = index + tap - 2;
				if (at < 0 || at >= values.length) continue;
				total += values[at] * kernel[tap];
				weight += kernel[tap];
			}
			return weight === 0 ? 0 : total / weight;
		});
	}

	const points = $derived.by(() => {
		const series = blurred(spark.length > 0 ? spark : [0]);
		const peak = Math.max(...series, 1);
		const span = Math.max(1, series.length - 1);
		return series.map((value, index) => ({
			x: (index / span) * WIDTH,
			y: HEIGHT - PAD - (value / peak) * (HEIGHT - PAD * 2)
		}));
	});

	/** Catmull-Rom through the points, which is what makes it read as one curve. */
	const line = $derived.by(() => {
		if (points.length < 2) return '';
		let path = `M${points[0].x.toFixed(2)} ${points[0].y.toFixed(2)}`;
		for (let i = 0; i < points.length - 1; i++) {
			const before = points[i - 1] ?? points[i];
			const from = points[i];
			const to = points[i + 1];
			const after = points[i + 2] ?? to;
			const c1x = from.x + (to.x - before.x) / 6;
			const c1y = from.y + (to.y - before.y) / 6;
			const c2x = to.x - (after.x - from.x) / 6;
			const c2y = to.y - (after.y - from.y) / 6;
			path += ` C${c1x.toFixed(2)} ${c1y.toFixed(2)} ${c2x.toFixed(2)} ${c2y.toFixed(2)} ${to.x.toFixed(2)} ${to.y.toFixed(2)}`;
		}
		return path;
	});

	const area = $derived(line ? `${line} L${WIDTH} ${HEIGHT} L0 ${HEIGHT} Z` : '');
</script>

<span class="wrap" {title}>
	<svg class="trace" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} width={WIDTH} height={HEIGHT} aria-hidden="true">
		<path class="fill" d={area} />
		<path class="line" d={line} />
	</svg>
</span>

<style>
	.wrap {
		display: block;
		line-height: 0;
	}

	.trace {
		display: block;
		overflow: visible;
	}

	.fill {
		fill: var(--color-ink-4);
		opacity: 0.28;
	}

	.line {
		fill: none;
		stroke: var(--color-ink-3);
		stroke-width: 1.25;
		stroke-linecap: round;
		stroke-linejoin: round;
	}
</style>
