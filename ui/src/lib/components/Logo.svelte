<script lang="ts">
	let { size = 20, title = 'Nudge' }: { size?: number; title?: string } = $props();
</script>

<!--
	A signal leaving a point: one dot, three waves.

	The mark before this was a radar — concentric ranges with a sweep and the
	single contact it had found. That described a thing that watches. This
	describes the thing that happens next: something occurred, and it reached you.
	The dot is the occurrence and wears the accent; the waves are the signal
	travelling, and stay ink so the eye lands on the dot first.
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
	<g stroke="var(--color-ink)" stroke-width="1.6">
		<path d="M4.6 9.8a6.2 6.2 0 0 1 6.2 6.2" />
		<path d="M4.6 5.8a10.2 10.2 0 0 1 10.2 10.2" />
		<path d="M4.6 1.8a14.2 14.2 0 0 1 14.2 14.2" />
	</g>
	<circle cx="4.6" cy="16" r="2.2" fill="var(--color-orange)" stroke="none" />
</svg>
