<script lang="ts">
	import type { LedgerRow } from '$lib/api';
	import { age, confidence, date, host, odds, precise, signed, sourceKind, tier } from '$lib/format';
	import Icon from '$lib/ui/Icon.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Empty from '$lib/ui/Empty.svelte';

	let {
		rows,
		onopen,
		empty = 'Nothing cleared the confidence floor.'
	}: { rows: LedgerRow[]; onopen: (row: LedgerRow) => void; empty?: string } = $props();

	let open = $state<Set<string>>(new Set());

	const keyOf = (row: LedgerRow) => `${row.evidenceId}:${row.questionId}:${row.spanStart}`;

	function toggle(row: LedgerRow) {
		const next = new Set(open);
		const id = keyOf(row);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		open = next;
	}
</script>

{#if rows.length === 0}
	<Empty>{empty}</Empty>
{:else}
	<ul class="m-0 list-none p-0">
		{#each rows as row (keyOf(row))}
			{@const expanded = open.has(keyOf(row))}
			<li class={['border-b border-rule py-2', row.blocking && 'bg-oxblood-wash']}>
				<div class="flex items-start justify-between gap-2">
					<div class="min-w-0 flex-1">
						<span class="block text-base leading-snug font-medium">{row.title ?? host(row.sourceUrl)}</span>
						<div class="mt-0.5 flex items-baseline justify-between gap-2">
							<span class="type-sub">
								{row.publisher} ·
								<a href={row.sourceUrl} target="_blank" rel="noreferrer noopener">{sourceKind(row.sourceKind)}</a>
								· {date(row.anchor)} · {age(row.ageDays)}
							</span>
							<button
								class="inline-flex flex-none items-center gap-0.5 text-micro text-ink-3 hover:text-ink hover:underline"
								onclick={() => toggle(row)}
								aria-expanded={expanded}
							>
								<Icon name={expanded ? 'chevronDown' : 'chevronRight'} size="sm" />
								{expanded ? 'Hide' : 'Verbatim'}
							</button>
						</div>
					</div>

					<div class="flex-none text-right">
						<span class={['num block text-md leading-tight font-medium', row.contributionNats < 0 && 'text-oxblood']}
							>{signed(row.contributionNats)}</span
						>
						<span class="num text-sm leading-normal text-ink-4">{odds(row.contributionNats)}</span>
					</div>
				</div>

				<div class="mt-1">
					<span class="block text-base leading-snug font-medium text-ink" title={row.questionKey}>{row.questionTitle}</span>
					<span class="mt-px block text-sm leading-normal text-ink-3">{row.questionText}</span>
				</div>

				<span class="num mt-1 block border border-rule bg-paper px-2 py-0.5 text-micro leading-normal text-ink-2">
					{tier(row.tier)} {signed(row.tierNats, 1)} × {row.authority.toFixed(2)} × {row.decay.toFixed(3)} × {row.shrink.toFixed(
						2
					)}
					{#if row.groupRank > 0}<span class="ml-1 text-orange-ink">rank {row.groupRank + 1} ×{row.discount}</span>{/if}
					{#if row.blocking}<span class="ml-1 text-orange-ink">disqualifying</span>{/if}
					{#if row.themeCapped}<span class="ml-1 text-orange-ink">capped</span>{/if}
				</span>

				{#if expanded}
					<div class="mt-2 border-l-2 border-orange bg-paper px-3 py-2">
						{#if row.spanText}
							<blockquote class="mb-2 border-l-2 border-orange pl-2 font-display text-md leading-relaxed text-ink">
								“{row.spanText}”
							</blockquote>
						{/if}
						<div class="flex flex-wrap items-center gap-1">
							<Button variant="default" size="sm" onclick={() => onopen(row)}>Open capture</Button>
							<Button size="sm" href={row.sourceUrl} target="_blank" rel="noreferrer noopener">Open source</Button>
							<span class="num text-sm leading-normal text-ink-4">chars {row.spanStart}–{row.spanEnd}</span>
							<span class="num text-sm leading-normal text-ink-4"
								>{confidence(row.confidence)} conf · {precise(row.rawNats)} raw</span
							>
						</div>
						{#if row.rationale}<p class="text-sm leading-normal text-ink-3">{row.rationale}</p>{/if}
					</div>
				{/if}
			</li>
		{/each}
	</ul>
{/if}
