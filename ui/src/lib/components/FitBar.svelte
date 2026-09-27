<script lang="ts">
	/**
	 * The Fit bar, in two channels.
	 *
	 * `value` is the reading itself, 0 to 1, and it is the length: a company at
	 * 0.71 fills seventy-one percent of the track. It used to be the distance from
	 * the watch line to the Priority line, which meant anything at Priority filled
	 * the bar and 0.71 and 0.55 looked identical.
	 *
	 * `shade` is where that reading sits between those two lines, and it is the
	 * colour — yellow on the companies that have barely cleared the floor, deep
	 * green on the ones at the top. Colour saturates at the Priority line on
	 * purpose: past it the question stops being "how strong" and becomes "how far
	 * past", which is what the length is for.
	 *
	 * The colour is interpolated rather than computed here. Its two ends are
	 * `--fit-lo` and `--fit-hi`, which the theme sets per mode: a ramp that means
	 * "darker is stronger" cannot survive an automatic flip into dark mode, where
	 * the strongest company would be the one nearest the background.
	 */
	let { value, shade }: { value: number; shade: number } = $props();

	const clamp = (n: number) => Math.max(0, Math.min(1, n));
	const width = $derived(`${(clamp(value) * 100).toFixed(1)}%`);
	const tone = $derived(`${(clamp(shade) * 100).toFixed(1)}%`);
</script>

<span class="fit-bar" aria-hidden="true">
	<span class="filled" style:--width={width} style:--tone={tone}></span>
</span>

<style>
	.fit-bar {
		display: block;
		width: 88px;
		max-width: 100%;
		height: 4px;
		background: var(--color-sunken);
		overflow: hidden;
	}

	.filled {
		display: block;
		height: 100%;
		width: var(--width);
		/* A browser without color-mix gets the deep end rather than no bar at
		   all; the reading is wrong by a shade, never invisible. */
		background: var(--fit-hi);
		background: color-mix(in oklab, var(--fit-hi) var(--tone), var(--fit-lo));
	}
</style>
