<script lang="ts">
	import IconButton from '$lib/ui/IconButton.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import { icpStore, toggle } from '$lib/icps.svelte';
	import { EVENT_LABEL, EVENT_TYPES } from '$lib/mock/companies';
	import { DIMENSION_ORDER, FADES, FADE_HINT, FADE_LABEL, WEIGHT_LABEL } from '$lib/mock/rules';
	import type { Dimension, Direction, EventType, Fade, Rubric, Tier, Trigger } from '$lib/mock/types';

	let {
		signal,
		open = false,
		hits,
		lockDirection = false,
		showDimension = false,
		dimensionLabel,
		ontoggle,
		onremove
	}: {
		signal: Trigger & { rubric: Rubric; fade: Fade };
		open?: boolean;
		hits?: number;
		lockDirection?: boolean;
		showDimension?: boolean;
		dimensionLabel: Record<Dimension, string>;
		ontoggle: () => void;
		onremove: () => void;
	} = $props();

	const uid = $derived(`sig-${signal.id}`);

	const weightOptions = ([3, 2, 1] as Tier[]).map((tier) => ({ value: tier, label: WEIGHT_LABEL[tier] }));
	const fadeOptions = $derived(FADES.map((fade) => ({ value: fade, label: `${FADE_LABEL[fade]}, ${icpStore.rules.halfLife[fade]} mo` })));
	const directionOptions: { value: Direction; label: string }[] = [
		{ value: 1, label: 'Positive' },
		{ value: -1, label: 'Negative' }
	];
	const dimensionOptions = $derived(DIMENSION_ORDER.map((dimension) => ({ value: dimension, label: dimensionLabel[dimension] })));

	function typeItems(selected: EventType[]): MenuItem[] {
		return EVENT_TYPES.map((type) => ({ id: type, label: EVENT_LABEL[type], checked: selected.includes(type) }));
	}

	function direct(next: Direction) {
		if (next === 1) signal.flag = false;
	}
</script>

<div class={['flex flex-col gap-2 border-t border-rule py-2', signal.flag && 'border-l-2 border-l-oxblood pl-2']}>
	<div class="flex items-center gap-2">
		<IconButton icon={open ? 'chevronDown' : 'chevronRight'} size="sm" label={open ? 'Close signal' : 'Edit signal'} onclick={ontoggle} aria-expanded={open} />
		<button type="button" class="min-w-0 flex-1 truncate text-left text-base text-ink" onclick={ontoggle}>
			{signal.text || 'Unnamed signal'}
		</button>
		{#if showDimension}<Tag tone="bare">{dimensionLabel[signal.dimension]}</Tag>{/if}
		{#if signal.flag}<Tag tone="oxblood" flag title="A match takes the company out of the queue">Blocker</Tag>{/if}
		<Tag tone={signal.tier === 3 ? 'wash' : 'default'}>{WEIGHT_LABEL[signal.tier]}</Tag>
		<Tag tone="bare" title={FADE_HINT[signal.fade]}>{FADE_LABEL[signal.fade]}, {icpStore.rules.halfLife[signal.fade]} mo</Tag>
		{#if hits !== undefined}
			<Tag tone={hits > 0 ? 'default' : 'bare'} title="Events in the demo data matched to this signal">{hits} {hits === 1 ? 'event' : 'events'}</Tag>
		{/if}
		<IconButton icon="trash" size="sm" tone="danger" label="Delete signal" onclick={onremove} />
	</div>

	{#if open}
		<div class="flex flex-col gap-3 pb-2 pl-5">
			<Field label="Signal name" for={`${uid}-name`}>
				<Input id={`${uid}-name`} size="sm" placeholder="Say it in plain words" bind:value={signal.text} />
			</Field>
			<div class="grid grid-cols-2 gap-3 max-lg:grid-cols-1">
				<Field label="Counts" for={`${uid}-counts`}>
					<Textarea id={`${uid}-counts`} rows={2} bind:value={signal.rubric.counts} />
				</Field>
				<Field label="Does not count" for={`${uid}-not`}>
					<Textarea id={`${uid}-not`} rows={2} bind:value={signal.rubric.doesNotCount} />
				</Field>
				<Field label="Example that counts" for={`${uid}-pass`}>
					<Textarea id={`${uid}-pass`} rows={2} bind:value={signal.rubric.countsExample} />
				</Field>
				<Field label="Example that does not count" for={`${uid}-fail`}>
					<Textarea id={`${uid}-fail`} rows={2} bind:value={signal.rubric.failsExample} />
				</Field>
			</div>
			<div class="grid grid-cols-4 gap-3 max-lg:grid-cols-2">
				<Field label="Weight">
					<Select size="sm" label="Weight" options={weightOptions} bind:value={signal.tier} />
				</Field>
				<Field label="Fades as">
					<Select size="sm" label="Fades as" options={fadeOptions} value={signal.fade} onchange={(fade) => icpStore.setFade(signal, fade)} />
				</Field>
				<Field label="Dimension">
					<Select size="sm" label="Dimension" options={dimensionOptions} bind:value={signal.dimension} />
				</Field>
				<Field label="Direction">
					<Select size="sm" label="Direction" options={directionOptions} disabled={lockDirection} bind:value={signal.direction} onchange={direct} />
				</Field>
			</div>
			<div class="flex flex-wrap items-center gap-2">
				<Menu
					text="Event types"
					size="sm"
					searchable
					label="Event types this signal listens for"
					items={typeItems(signal.eventTypes)}
					onselect={(id) => (signal.eventTypes = toggle(signal.eventTypes, id as EventType))}
				/>
				{#each signal.eventTypes as type (type)}<Tag>{EVENT_LABEL[type]}</Tag>{/each}
			</div>
			{#if signal.direction === -1}
				<Checkbox bind:checked={signal.flag}>Blocker: a match takes the company out of the queue, whatever its score</Checkbox>
			{/if}
		</div>
	{/if}
</div>
