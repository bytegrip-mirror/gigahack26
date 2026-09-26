<script lang="ts">
	/**
	 * The Fit meter: how far a company has come from the watch line to the
	 * Priority line. `value` is that fraction, 0 to 1, and it drives both the
	 * length and the colour, so the mark carries one reading.
	 *
	 * The colour is not computed here, only interpolated: the two ends are
	 * `--fit-lo` and `--fit-hi`, which the theme sets per mode. A ramp that means
	 * "darker is stronger" cannot survive an automatic flip into dark mode —
	 * the strongest company would be the one closest to the background — so the
	 * dark end is a chosen pair that runs the other way, dim amber to bright
	 * orange, and stays above 3:1 against both the surface and the track.
	 */
	let { value }: { value: number } = $props();

	const meter = $derived(`${(Math.max(0, Math.min(1, value)) * 100).toFixed(1)}%`);
</script>

<span class="fit-bar" aria-hidden="true">
	<span class="filled" style:--meter={meter}></span>
</span>

<style>
	.fit-bar {
		display: block;
		/* Shorter than the column: the fill now reaches the end of the track at
		   the Priority line, so a full-width rail would leave the words above it
		   looking like an afterthought. */
		width: 88px;
		max-width: 100%;
		height: 4px;
		background: var(--color-sunken);
		overflow: hidden;
	}

	.filled {
		display: block;
		height: 100%;
		width: var(--meter);
		/* A browser without color-mix gets the deep end rather than no bar at
		   all; the reading is wrong by a shade, never invisible. */
		background: var(--fit-hi);
		background: color-mix(in oklab, var(--fit-hi) var(--meter), var(--fit-lo));
	}
</style>
