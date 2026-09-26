<script lang="ts">
	import Panel from '$lib/ui/Panel.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Button from '$lib/ui/Button.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';
	import SignalList from './SignalList.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { DIMENSION_HINT, DIMENSION_LABEL } from '$lib/mock/engine';
	import { DIMENSION_ORDER, FADES, FADE_HINT, FADE_LABEL } from '$lib/mock/rules';

	type Tab = 'signals' | 'evidence' | 'scale';

	let tab = $state<Tab>('signals');

	const rules = $derived(icpStore.rules);

	const tabs = $derived([
		{ value: 'signals' as Tab, label: 'Signals', count: rules.positive.length + rules.negative.length },
		{ value: 'evidence' as Tab, label: 'Evidence rules', count: rules.evidence.length },
		{ value: 'scale' as Tab, label: 'Scale and fading' }
	]);

	function removeRule(id: string) {
		rules.evidence = rules.evidence.filter((rule) => rule.id !== id);
	}

	function removeStep(id: string) {
		rules.scale = rules.scale.filter((step) => step.id !== id);
	}
</script>

<Tabs bind:value={tab} {tabs} label="Universal rule sections" class="min-h-0 flex-1">
	{#snippet children(current)}
		<div class="scroll h-full">
			{#if current === 'signals'}
				<Panel>
					<p class="type-sub">These signals apply to every service and every ICP. Edit them once here.</p>
				</Panel>
				<Panel>
					<SignalList
						list={rules.positive}
						direction={1}
						newDimension="TIMING"
						lockDirection
						title="Universal positive"
						empty="No universal positive signal."
						dimensionLabel={DIMENSION_LABEL}
					/>
				</Panel>
				<Panel>
					<SignalList
						list={rules.negative}
						direction={-1}
						newDimension="BUDGET"
						lockDirection
						title="Universal negative"
						empty="No universal negative signal."
						dimensionLabel={DIMENSION_LABEL}
					/>
				</Panel>
			{:else if current === 'evidence'}
				<Panel title="What makes a sentence a signal" heading="label">
					{#snippet tools()}<Button size="sm" icon="plus" onclick={() => icpStore.addEvidenceRule()}>Add rule</Button>{/snippet}
					<p class="type-sub">A sentence must pass every rule before any signal can use it. If it fails one, we drop it.</p>
				</Panel>
				{#each rules.evidence as rule, index (rule.id)}
					<Panel>
						<div class="flex items-center gap-2">
							<span class="num flex-none type-label">{index + 1}</span>
							<Input class="flex-1" size="sm" aria-label={`Rule ${index + 1} name`} placeholder="Rule name" bind:value={rule.name} />
							<IconButton icon="trash" size="sm" tone="danger" label="Delete rule" onclick={() => removeRule(rule.id)} />
						</div>
						<Textarea rows={2} aria-label={`Rule ${index + 1} text`} bind:value={rule.rule} />
						<div class="grid grid-cols-2 gap-3 max-lg:grid-cols-1">
							<Field label="Passes" for={`${rule.id}-pass`}>
								<Textarea id={`${rule.id}-pass`} rows={2} bind:value={rule.pass} />
							</Field>
							<Field label="Fails" for={`${rule.id}-fail`}>
								<Textarea id={`${rule.id}-fail`} rows={2} bind:value={rule.fail} />
							</Field>
						</div>
					</Panel>
				{/each}
			{:else}
				<Panel title="What a level means" heading="label">
					<p class="type-sub">Each signal moves a dimension up or down. The level is the sum after fading.</p>
					{#each rules.scale as step (step.id)}
						<div class="grid grid-cols-4 items-center gap-2 border-t border-rule pt-2">
							<Input size="sm" aria-label="Level" bind:value={step.level} />
							<div class="col-span-3 flex items-center gap-2">
								<Input class="flex-1" size="sm" aria-label={`Meaning of level ${step.level}`} bind:value={step.meaning} />
								<IconButton icon="trash" size="sm" tone="danger" label="Delete level" onclick={() => removeStep(step.id)} />
							</div>
						</div>
					{/each}
				</Panel>
				<Panel title="Lit threshold per dimension" heading="label">
					<p class="type-sub">A dimension is lit when its level reaches this number.</p>
					<div class="grid grid-cols-5 gap-3 max-lg:grid-cols-2">
						{#each DIMENSION_ORDER as dimension (dimension)}
							<Field label={DIMENSION_LABEL[dimension]} for={`lit-${dimension}`} hint={DIMENSION_HINT[dimension]}>
								<Input id={`lit-${dimension}`} size="sm" type="number" step="0.25" min="0" bind:value={rules.lit[dimension]} />
							</Field>
						{/each}
					</div>
				</Panel>
				<Panel title="Default half-lives" heading="label">
					<p class="type-sub">After one half-life, a signal counts for half. Each signal picks one of these classes.</p>
					<div class="grid grid-cols-3 gap-3 max-lg:grid-cols-1">
						{#each FADES as fade (fade)}
							<Field label={`${FADE_LABEL[fade]}, months`} for={`fade-${fade}`} hint={FADE_HINT[fade]}>
								<Input id={`fade-${fade}`} size="sm" type="number" min="1" bind:value={() => rules.halfLife[fade], (months) => icpStore.setHalfLife(fade, Number(months) || 1)} />
							</Field>
						{/each}
					</div>
				</Panel>
			{/if}
		</div>
	{/snippet}
</Tabs>
