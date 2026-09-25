<script lang="ts">
	import type { LedgerRow } from '$lib/api';
	import { age, confidence, date, host, odds, precise, signed, sourceKind, tier } from '$lib/format';

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
	<p class="empty">{empty}</p>
{:else}
	<ul class="ledger">
		{#each rows as row (keyOf(row))}
			{@const expanded = open.has(keyOf(row))}
			<li class:blocking={row.blocking}>
				<div class="row-main">
					<div class="col-src">
						<span class="src">{row.title ?? host(row.sourceUrl)}</span>
						<span class="sub">
							{row.publisher} · <a href={row.sourceUrl} target="_blank" rel="noreferrer noopener">{sourceKind(row.sourceKind)}</a>
						</span>
						<span class="sub dim num">{date(row.anchor)} · {age(row.ageDays)}</span>
					</div>

					<div class="col-q">
						<span class="key">{row.questionKey}</span>
						<span class="sub">{row.questionText}</span>
						<span class="formula num">
							{tier(row.tier)} {signed(row.tierNats, 1)} × {row.authority.toFixed(2)} × {row.decay.toFixed(3)} × {row.shrink.toFixed(2)}
							{#if row.groupRank > 0}<span class="mark">rank {row.groupRank + 1} ×{row.discount}</span>{/if}
							{#if row.themeCapped}<span class="mark">capped</span>{/if}
						</span>
					</div>

					<div class="col-amt">
						<span class="num amt" class:neg={row.contributionNats < 0}>{signed(row.contributionNats)}</span>
						<span class="sub dim num">{odds(row.contributionNats)}</span>
						<button class="btn btn-quiet btn-sm" onclick={() => toggle(row)} aria-expanded={expanded}>
							{expanded ? 'Less' : 'Verbatim'}
						</button>
					</div>
				</div>

				{#if expanded}
					<div class="detail">
						{#if row.spanText}<blockquote>“{row.spanText}”</blockquote>{/if}
						<div class="detail-actions">
							<button class="btn btn-sm" onclick={() => onopen(row)}>Open capture</button>
							<a class="btn btn-sm" href={row.sourceUrl} target="_blank" rel="noreferrer noopener">Open source</a>
							<span class="sub dim num">chars {row.spanStart}–{row.spanEnd}</span>
							<span class="sub dim num">{confidence(row.confidence)} conf · {precise(row.rawNats)} raw</span>
						</div>
						{#if row.rationale}<p class="sub dim">{row.rationale}</p>{/if}
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
		padding: 9px 0;
	}

	.ledger > li.blocking {
		background: var(--oxblood-wash);
	}

	.row-main {
		display: grid;
		grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr) 88px;
		gap: 12px;
	}

	.col-amt {
		text-align: right;
	}

	.src {
		display: block;
		font-size: 12.5px;
		font-weight: 500;
		line-height: 1.35;
	}

	.sub {
		display: block;
		font-size: 11px;
		line-height: 1.45;
		color: var(--ink-2);
		margin-top: 2px;
	}

	

	.formula {
		display: block;
		margin-top: 5px;
		font-size: 10px;
		line-height: 1.5;
		padding: 3px 6px;
		background: var(--paper);
		border: 1px solid var(--rule);
		color: var(--ink-2);
	}

	.mark {
		color: var(--orange-ink);
		margin-left: 4px;
	}

	.amt {
		display: block;
		font-size: 13px;
		font-weight: 500;
	}

	.amt.neg {
		color: var(--oxblood);
	}

	.col-amt .btn {
		margin-top: 5px;
	}

	.detail {
		margin-top: 9px;
		padding: 10px 12px;
		background: var(--paper);
		border-left: 2px solid var(--orange);
	}

	blockquote {
		margin: 0 0 8px;
		padding-left: 10px;
		border-left: 2px solid var(--orange);
		font-family: var(--font-display);
		font-size: 13.5px;
		line-height: 1.5;
		color: var(--ink);
		max-height: 150px;
		overflow: auto;
	}

	.detail-actions {
		display: flex;
		align-items: center;
		gap: 6px;
		flex-wrap: wrap;
	}

	.empty {
		font-size: 12.5px;
		color: var(--ink-3);
	}
</style>
