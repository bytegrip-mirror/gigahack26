<script lang="ts">
	let {
		probability,
		band,
		baseRate,
		thresholds,
		showScale = false
	}: {
		probability: number;
		band: string;
		baseRate: number;
		thresholds: { watch: number; nurture: number; priority: number };
		showScale?: boolean;
	} = $props();

	const pct = (value: number) => `${Math.max(0, Math.min(1, value)) * 100}%`;
</script>

<div class="w-full">
	<div class="relative h-1 border-y border-rule bg-sunken">
		<span
			class="absolute inset-y-0 bg-orange-wash"
			style:left={pct(thresholds.nurture)}
			style:right={pct(1 - thresholds.priority)}
		></span>
		<span class="absolute -top-0.5 -bottom-0.5 w-px bg-ink-4" style:left={pct(baseRate)}></span>
		<span
			class={[
				'absolute inset-y-0 left-0',
				band === 'PRIORITY'
					? 'bg-orange'
					: band === 'NURTURE'
						? 'bg-orange-line'
						: band === 'DISQUALIFIED'
							? 'bg-oxblood-line'
							: 'bg-ink-4'
			]}
			style:width={pct(probability)}
		></span>
	</div>
	{#if showScale}
		<div class="relative mt-0.5 h-3">
			<span
				class="absolute -translate-x-1/2 text-micro tracking-caps whitespace-nowrap text-ink-4"
				style:left={pct(baseRate)}
			>
				base {baseRate.toFixed(2)}
			</span>
			<span
				class="absolute -translate-x-1/2 text-micro tracking-caps whitespace-nowrap text-ink-4"
				style:left={pct(thresholds.priority)}
			>
				{thresholds.priority.toFixed(2)}
			</span>
		</div>
	{/if}
</div>
