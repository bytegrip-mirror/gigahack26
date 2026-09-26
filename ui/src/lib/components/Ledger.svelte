<script lang="ts">
	import type { LedgerRow } from '$lib/api';
	import { age, confidence, date, host, precise, signed, sourceKind, tier } from '$lib/format';
	import Icon from './Icon.svelte';

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

	function effect(row: LedgerRow): string {
		if (row.blocking) return 'removed the company';
		if (row.tierNats === 0) return 'not evidence';
		return row.contributionNats < 0 ? 'counts against' : 'counts for';
	}

	function source(row: LedgerRow): string {
		if (row.sourceKind === 'FILING') return 'regulatory filing';
		if (row.sourceKind === 'REGISTRY') return 'company register';
		if (row.sourceKind === 'JOB_POSTING') return 'job posting';
		if (row.sourceKind === 'NEWSROOM' || row.sourceKind === 'STRATEGY') return 'the company’s own page';
		return 'news report';
	}

	function weight(row: LedgerRow): string {
		if (row.tier === 'HIGH') return 'high weight';
		if (row.tier === 'MEDIUM') return 'medium weight';
		return 'low weight';
	}

	function reader(row: LedgerRow): string {
		return row.engine && row.engine.startsWith('local') ? 'matched by rules' : 'read by the model';
	}

	function discount(value: number): string {
		if (value >= 0.99) return 'in full';
		if (value >= 0.49) return 'at half';
		if (value >= 0.24) return 'at a quarter';
		return `at ${value.toFixed(2)}`;
	}
</script>

{#if rows.length === 0}
	<p class="empty">{empty}</p>
{:else}
	<ul class="ledger">
		{#each rows as row (keyOf(row))}
			{@const expanded = open.has(keyOf(row))}
			{@const weighable = row.tierNats !== 0}
			<li class:blocking={row.blocking} class:muted={!weighable}>
				<div class="row-main">
					<div class="col-src">
						<span class="src">{row.title ?? host(row.sourceUrl)}</span>
						<div class="src-line">
							<span class="sub">
								{row.publisher} · <a href={row.sourceUrl} target="_blank" rel="noreferrer noopener">{sourceKind(row.sourceKind)}</a>
								· {date(row.anchor)} · {age(row.ageDays)}
							</span>
							<button class="verbatim" onclick={() => toggle(row)} aria-expanded={expanded}>
								<Icon name={expanded ? 'chevronDown' : 'chevronRight'} size={12} />
								{expanded ? 'Hide' : 'Verbatim'}
							</button>
						</div>
					</div>

					<div class="col-amt">
						{#if weighable}
							<span class="num amt" class:neg={row.contributionNats < 0}>{signed(row.contributionNats)}</span>
						{:else}
							<span class="amt none">—</span>
						{/if}
						<span class="effect" class:neg={row.contributionNats < 0} class:quiet={!weighable}>{effect(row)}</span>
					</div>
				</div>

				<div class="col-q">
					<span class="q-title" title={row.questionKey}>{row.questionTitle}</span>
					<span class="q-text">{row.questionText}</span>
				</div>

				<span class="strength">
					{weight(row)} · {source(row)} · {age(row.ageDays)} · {reader(row)}
					{#if row.groupRank > 0}<span class="mark">{row.groupRank + 1}th report of the same thing — counted {discount(row.discount)}</span>{/if}
					{#if row.themeCapped}<span class="mark">capped: one topic cannot carry the whole score</span>{/if}
					{#if row.blocking}<span class="mark">disqualifying</span>{/if}
				</span>

				{#if expanded}
					<div class="detail">
						{#if row.spanText}<blockquote>“{row.spanText}”</blockquote>{/if}
						<div class="detail-foot">
							<button class="btn btn-sm" onclick={() => onopen(row)}>Open capture</button>
							<a class="btn btn-sm" href={row.sourceUrl} target="_blank" rel="noreferrer noopener">Open source</a>
							<span class="sub dim num">characters {row.spanStart}–{row.spanEnd}</span>
							<span class="sub dim num">{confidence(row.confidence)} confident</span>
						</div>
						{#if row.rationale}<p class="sub dim">{row.rationale}</p>{/if}
						<details class="working">
							<summary>Every number behind this row</summary>
							<p class="sub dim num">
								{tier(row.tier)} {signed(row.tierNats, 1)} × {row.authority.toFixed(2)} source × {row.decay.toFixed(3)} decay
								× {row.shrink.toFixed(2)} confidence = {precise(row.rawNats)}
								{#if row.groupRank > 0} · repeated evidence ×{row.discount}{/if}
							</p>
						</details>
					</div>
				{/if}
			</li>
		{/each}
	</ul>
{/if}

<style>
	.ledger {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.ledger > li {
		border-bottom: 1px solid var(--rule);
		padding: var(--sp-2) 0;
	}

	.ledger > li.blocking {
		background: var(--oxblood-wash);
	}

	.ledger > li.muted .src {
		font-weight: 400;
		color: var(--ink-2);
	}

	.row-main {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		gap: var(--sp-2);
		align-items: start;
	}

	.col-amt {
		text-align: right;
		flex: none;
	}

	.src {
		display: block;
		font-size: var(--fs-base);
		font-weight: 500;
		line-height: 1.35;
	}

	.src-line {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--sp-2);
		margin-top: 2px;
	}

	.sub {
		display: block;
		font-size: var(--fs-sm);
		line-height: 1.45;
		color: var(--ink-3);
	}

	.verbatim {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		font-size: var(--fs-micro);
		color: var(--ink-3);
		flex: none;
	}

	.verbatim:hover {
		color: var(--ink);
		text-decoration: underline;
		text-underline-offset: 2px;
	}

	.col-q {
		margin-top: var(--sp-1);
	}

	.q-title {
		display: block;
		font-size: var(--fs-base);
		font-weight: 500;
		line-height: 1.35;
		color: var(--ink);
	}

	.q-text {
		display: block;
		font-size: var(--fs-sm);
		line-height: 1.45;
		color: var(--ink-3);
		margin-top: 1px;
	}

	.strength {
		display: block;
		margin-top: var(--sp-1);
		font-size: var(--fs-micro);
		line-height: 1.5;
		padding: 2px 6px;
		background: var(--paper);
		border: 1px solid var(--rule);
		color: var(--ink-2);
	}

	.mark {
		color: var(--orange-ink);
		margin-left: var(--sp-1);
	}

	.amt {
		display: block;
		font-size: var(--fs-md);
		font-weight: 500;
		line-height: 1.2;
	}

	.amt.none {
		color: var(--ink-3);
	}

	.amt.neg {
		color: var(--oxblood);
	}

	.effect {
		display: block;
		font-size: var(--fs-micro);
		color: var(--ink-3);
	}

	.effect.neg {
		color: var(--oxblood);
	}

	.effect.quiet {
		font-style: italic;
	}

	.detail {
		margin-top: var(--sp-2);
		padding: var(--sp-2) var(--sp-3);
		background: var(--paper);
		border-left: 2px solid var(--orange);
	}

	blockquote {
		margin: 0 0 var(--sp-2);
		padding-left: var(--sp-2);
		border-left: 2px solid var(--orange);
		font-family: var(--font-display);
		font-size: var(--fs-md);
		line-height: 1.5;
		color: var(--ink);
		max-height: 150px;
		overflow: auto;
	}

	.detail-foot {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		flex-wrap: wrap;
	}

	.working {
		margin-top: var(--sp-2);
	}

	.working summary {
		font-size: var(--fs-micro);
		color: var(--ink-3);
		cursor: pointer;
	}

	.working p {
		margin: var(--sp-1) 0 0;
	}

	.empty {
		font-size: var(--fs-base);
		color: var(--ink-3);
	}
</style>
