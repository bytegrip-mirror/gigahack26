<script lang="ts">
	import Panel from '$lib/ui/Panel.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Button from '$lib/ui/Button.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import type { Service } from '$lib/mock/types';

	let { service, hrefFor, oncreate }: { service: Service; hrefFor: (icpId: string) => string; oncreate: () => void } = $props();

	const owned = $derived(icpStore.icpsOf(service.id));
</script>

<div class="scroll min-h-0 flex-1">
	<Panel title="What we sell" heading="label">
		<Field label="Service name" for="service-name">
			<Input id="service-name" bind:value={() => service.name, (name) => icpStore.renameService(service, String(name ?? ''))} />
		</Field>
		<Field label="What we deliver" for="service-description" hint="One paragraph in plain words. Reps and the outreach writer both read it.">
			<Textarea id="service-description" rows={4} bind:value={service.description} />
		</Field>
		<Field label="Outcome for the customer" for="service-outcome">
			<Textarea id="service-outcome" rows={2} bind:value={service.outcome} />
		</Field>
	</Panel>
	<Panel title="ICPs for this service" heading="label">
		{#snippet tools()}<Button size="sm" icon="plus" onclick={oncreate}>Add ICP</Button>{/snippet}
		<ul class="flex flex-col">
			{#each owned as icp (icp.id)}
				<li class="flex items-baseline gap-2 border-b border-rule py-1 text-base last:border-b-0">
					<a class="min-w-0 flex-1 truncate" href={hrefFor(icp.id)}>{icp.name}</a>
					<span class="num flex-none text-ink-3">{icp.triggers.length} signals</span>
				</li>
			{:else}
				<li class="type-sub">No ICP yet. Add one to say who buys this service and what signals to watch.</li>
			{/each}
		</ul>
	</Panel>
</div>
