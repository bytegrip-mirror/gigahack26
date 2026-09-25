<script lang="ts">
	import type { LedgerRow } from '$lib/api';
	import { age, confidence, date, nats, odds, precise, signed, sourceKind, tier, host } from '$lib/format';

	let {
		rows,
		onopen,
		emptyMessage = 'No evidence passed the confidence floor for this policy.'
	}: { rows: LedgerRow[]; onopen: (row: LedgerRow) => void; emptyMessage?: string } = $props();

	let expanded = $state<Set<string>>(new Set());

	function key(row: LedgerRow) {
		return `${row.evidenceId}:${row.questionId}:${row.spanStart}`;
	}

	function toggle(row: LedgerRow) {
		const id = key(row);
		const next = new Set(expanded);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		expanded = next;
	}
</script>

{#if rows.length === 0}
	<p class="muted small">{emptyMessage}</p>
{:else}
	<table class="grid ledger">
		<thead>
			<tr>
				<th style="width: 34%">Evidence</th>
				<th style="width: 22%">Question</th>
				<th style="width: 26%">Weight of evidence</th>
				<th style="width: 10%; text-align: right">Contribution</th>
				<th style="width: 8%"></th>
			</tr>
		</thead>
		<tbody>
			{#each rows as row (key(row))}
				{@const open = expanded.has(key(row))}
				<tr class:blocking={row.blocking}>
					<td>
						<div class="stack gap-4">
							<span class="title">{row.title ?? host(row.sourceUrl)}</span>
							<span class="tiny muted">
								{row.publisher} · {sourceKind(row.sourceKind)} ·
								<a href={row.sourceUrl} target="_blank" rel="noreferrer noopener">source ↗</a>
							</span>
							<span class="tiny mono muted">
								{date(row.anchor)} · {age(row.ageDays)} · decay anchor: {row.anchorKind}
							</span>
						</div>
					</td>
					<td>
						<div class="stack gap-4">
							<span class="tiny mono">{row.questionKey}</span>
							<span class="tiny muted">{row.questionText}</span>
						</div>
					</td>
					<td>
						<div class="stack gap-4">
							<span class="mono formula">
								{tier(row.tier)} {signed(row.tierNats, 1)} × {row.authority.toFixed(2)} × {row.decay.toFixed(3)} × {row.shrink.toFixed(2)}
							</span>
							<span class="tiny muted">
								tier × authority × decay (H={row.halfLifeDays}d) × confidence shrink ({confidence(row.confidence)})
								{#if row.groupRank > 0}
									· rank {row.groupRank + 1} in group, ×{row.discount}
								{/if}
								{#if row.themeCapped}· theme capped{/if}
							</span>
						</div>
					</td>
					<td style="text-align: right">
						<div class="stack gap-4" style="align-items: flex-end">
							<span class="mono amount" class:negative={row.contributionNats < 0}>
								{signed(row.contributionNats)}
							</span>
							<span class="tiny muted mono">{odds(row.contributionNats)}</span>
						</div>
					</td>
					<td style="text-align: right">
						<button class="btn btn-ghost tiny" onclick={() => toggle(row)} aria-expanded={open}>
							{open ? 'Less' : 'Verbatim'}
						</button>
					</td>
				</tr>
				{#if open}
					<tr class="detail-row">
						<td colspan="5">
							<div class="detail">
								{#if row.spanText}
									<blockquote>“{row.spanText}”</blockquote>
								{/if}
								<div class="row gap-12" style="flex-wrap: wrap">
									<button class="btn tiny" onclick={() => onopen(row)}>Open the capture</button>
									<a class="btn tiny" href={row.sourceUrl} target="_blank" rel="noreferrer noopener">
										Open source ↗
									</a>
								</div>
								<dl class="arith mono tiny">
									<dt>character range</dt>
									<dd>{row.spanStart}–{row.spanEnd}</dd>
									<dt>extraction confidence</dt>
									<dd>{confidence(row.confidence)} → shrink 2c−1 = {signed(row.shrink, 2)}</dd>
									<dt>raw weight</dt>
									<dd>{precise(row.rawNats)} nats</dd>
									<dt>after rank discount</dt>
									<dd>{precise(row.contributionNats)} nats ({nats(row.contributionNats)})</dd>
									<dt>engine</dt>
									<dd>{row.engine}</dd>
									<dt>span check</dt>
									<dd>{row.spanVerified ? 'contained in the stored capture' : 'not verified — row struck'}</dd>
									{#if row.rationale}
										<dt>why it matched</dt>
										<dd class="prose">{row.rationale}</dd>
									{/if}
								</dl>
							</div>
						</td>
					</tr>
				{/if}
			{/each}
		</tbody>
	</table>
{/if}

<style>
	.ledger td {
		font-size: 13px;
	}

	.title {
		font-weight: 550;
		line-height: 1.35;
	}

	.formula {
		font-size: 11.5px;
		color: var(--ink-2);
		background: var(--surface-sunken);
		border-radius: 4px;
		padding: 2px 6px;
		display: inline-block;
	}

	.amount {
		font-size: 14px;
		font-weight: 600;
	}

	.amount.negative {
		color: var(--danger);
	}

	tr.blocking td {
		background: var(--danger-bg);
	}

	.detail-row td {
		background: var(--surface-sunken);
		padding: 0;
	}

	.detail {
		padding: 14px 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	blockquote {
		margin: 0;
		border-left: 3px solid var(--orange);
		padding: 4px 0 4px 12px;
		font-size: 13.5px;
		line-height: 1.6;
		color: var(--ink-2);
		max-height: 180px;
		overflow: auto;
	}

	.arith {
		display: grid;
		grid-template-columns: 180px 1fr;
		gap: 3px 14px;
		margin: 0;
		color: var(--ink-3);
	}

	.arith dd {
		margin: 0;
		color: var(--ink-2);
	}

	.arith dd.prose {
		font-family: var(--font-sans);
	}
</style>
