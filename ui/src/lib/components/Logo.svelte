<script lang="ts">
	let { size = 20, title = 'Lead Radar' }: { size?: number; title?: string } = $props();
</script>

<!--
	A radar: three ranges on a common origin, the sweep, and the one contact the
	sweep has found. The mark before this was three rising bars, which said
	"chart" — this says what the product does, which is watch a market and pick
	one thing out of it.
-->
<svg
	width={size}
	height={size}
	viewBox="0 0 20 20"
	role="img"
	aria-label={title}
	focusable="false"
	fill="none"
	stroke-linecap="round"
>
	<g stroke="var(--color-ink)" stroke-width="1.5">
		<path d="M4.5 2.5a13 13 0 0 1 13 13" />
		<path d="M4.5 7a8.5 8.5 0 0 1 8.5 8.5" />
		<path d="M4.5 11.5a4 4 0 0 1 4 4" />
		<path d="M4.5 15.5 13.7 6.3" />
	</g>
	<circle cx="11.2" cy="8.8" r="1.7" fill="var(--color-orange)" stroke="none" />
</svg>
