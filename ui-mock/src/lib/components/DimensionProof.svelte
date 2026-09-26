<script lang="ts">
	import Tag from '$lib/ui/Tag.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import LevelBar from './LevelBar.svelte';
	import { date, host, timestamp } from '$lib/format';
	import { EVENT_LABEL } from '$lib/mock/companies';
	import { AUTHORITY_LABEL, DIMENSIONS, DIMENSION_LABEL, TIER_LABEL, signed, type Lead, type Move } from '$lib/mock/engine';
	import type { Dimension } from '$lib/mock/types';

	let { lead, onopen }: { lead: Lead; onopen: (documentId: string, quote: string) => void } = $props();

	const highest = DIMENSIONS.reduce((best, key) => (lead.levels[key] > lead.levels[best] ? key : best), DIMENSIONS[0]);

	let dimension = $state<Dimension>(highest);
	let scored = $state<Set<string>>(new Set());

	function movesOf(key: Dimension): Move[] {
		return lead.moves.filter((move) => move.dimension === key).sort((a, b) => b.event.date.localeCompare(a.event.date));
	}

	const proof = $derived(movesOf(dimension));
	const up = $derived(proof.filter((move) => move.base > 0).length);
	const down = $derived(proof.filter((move) => move.base < 0).length);

	function toggle(id: string) {
		const next = new Set(scored);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		scored = next;
	}
</script>

<div class="flex flex-col">
	<ul class="flex flex-col py-1">
		{#each DIMENSIONS as key (key)}
			{@const last = movesOf(key)[0]}
			{@const active = dimension === key}
			{@const value = lead.levels[key]}
			<li>
				<button
					type="button"
					aria-pressed={active}
					onclick={() => (dimension = key)}
					class={[
						'flex w-full items-center gap-3 border-l-2 px-4 py-2 text-left transition-colors hover:bg-paper',
						active ? 'border-l-orange bg-orange-wash' : 'border-l-transparent'
					]}
				>
					<span class={['w-1/5 flex-none text-md', active ? 'font-semibold' : 'font-medium']}>{DIMENSION_LABEL[key]}</span>
					<span class="min-w-0 flex-1"><LevelBar {value} was={last ? last.before : null} size="lg" /></span>
					<span class={['num w-bar flex-none text-right text-lg font-medium', value < 0 ? 'text-oxblood' : 'text-ink']}>{value.toFixed(2)}</span>
					<span class="num w-1/5 flex-none text-sm text-ink-3">
						{#if last}
							was {last.before.toFixed(2)} <span class={last.base < 0 ? 'text-oxblood' : 'text-ink-2'}>{signed(last.base, 2)}</span>
						{:else}
							no moves
						{/if}
					</span>
				</button>
			</li>
		{/each}
	</ul>

	<section class="flex flex-col border-t border-ink">
		<div class="flex flex-wrap items-baseline justify-between gap-2 px-4 pt-3 pb-2">
			<h3 class="type-display text-md">Proof for {DIMENSION_LABEL[dimension]}</h3>
			<span class="num text-sm text-ink-3">{proof.length} events · {up} up · {down} down</span>
		</div>
		{#each proof as move (move.id)}
			{@const open = scored.has(move.id)}
			<article class="flex flex-col gap-1 border-t border-rule px-4 py-3">
				<p class="flex flex-wrap items-baseline gap-x-2 text-sm">
					<span class="num text-ink-3">{date(move.event.date)}</span>
					<span class={['num font-semibold', move.base < 0 ? 'text-oxblood' : 'text-ink']}>{signed(move.base, 2)}</span>
					<a href={move.document.url} target="_blank" rel="noreferrer noopener">{move.document.publisher}</a>
					{#if move.trigger.flag}<Tag tone="oxblood">Flag</Tag>{/if}
				</p>
				<blockquote class="border-l-2 border-orange pl-3 text-base leading-relaxed">“{move.event.quote}”</blockquote>
				<div class="flex flex-wrap items-center gap-2 pt-1">
					<button type="button" class="inline-flex items-center gap-1 text-sm text-ink-2 hover:text-ink" aria-expanded={open} onclick={() => toggle(move.id)}>
						<Icon name={open ? 'chevronDown' : 'chevronRight'} size="sm" /> How this was scored
					</button>
					<Button size="sm" variant="quiet" icon="eye" onclick={() => onopen(move.document.id, move.event.quote)}>Open in document</Button>
				</div>
				{#if open}
					<div class="flex flex-col gap-1 border border-rule bg-paper px-3 py-2 text-sm">
						<p class="flex flex-wrap items-center gap-2">
							<Tag>{EVENT_LABEL[move.event.type]}</Tag>
							<span class="text-ink-2">{move.event.summary}</span>
						</p>
						<p class="text-ink-2"><span class="type-label mr-1">Trigger</span>{move.trigger.text}</p>
						<p class="num text-ink-2">
							Strength {move.strength.toFixed(1)} ({TIER_LABEL[move.trigger.tier]}) × Freshness {move.freshness.toFixed(2)} ({move.ageMonths.toFixed(1)} mo old, half-life {move.halfLife} mo)
							× Source {move.authority.toFixed(2)} ({AUTHORITY_LABEL[move.document.kind]}) × Certainty {move.certainty.toFixed(2)} (read by AI)
							= <span class={['font-medium', move.base < 0 ? 'text-oxblood' : 'text-ink']}>{signed(move.points, 2)} today</span>.
							It moved the level {move.before.toFixed(2)} → {move.after.toFixed(2)} on {date(move.event.date)}.
						</p>
						<p class="text-ink-3"><span class="type-label mr-1">Why it counts</span>{move.why}</p>
						{#if move.term}
							<p class="text-ink-2"><span class="type-label mr-1">Term</span>{move.term.basis}{#if move.term.endsAt}. Ends {move.term.endsAt}{/if}</p>
						{/if}
						<p class="num text-ink-3">Captured {timestamp(move.document.capturedAt)} · {host(move.document.url)}</p>
					</div>
				{/if}
			</article>
		{:else}
			<p class="border-t border-rule px-4 py-3 type-sub">No event has moved {DIMENSION_LABEL[dimension].toLowerCase()} for {lead.icp.serviceLine}.</p>
		{/each}
	</section>
</div>
