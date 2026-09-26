<script lang="ts">
	import { fetchBoard, type BoardRow } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { plural, shortNumber, theme } from '$lib/format';
	import CompanyMark from '$lib/components/CompanyMark.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';

	const SHOWN = 24;

	type Found = {
		id: string;
		name: string;
		domain: string | null;
		band: string;
		evidence: number;
	};

	type Question = {
		key: string;
		title: string;
		text: string;
		theme: string;
		polarity: string;
		found: Found[];
		total: number;
	};

	let rows = $state<BoardRow[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let hovered = $state<string | null>(null);
	let onlyShared = $state(false);

	const serviceLine = $derived(session.serviceLine);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		fetchBoard({ serviceLine: line })
			.then((result) => {
				if (cancelled) return;
				rows = result;
				error = null;
			})
			.catch((cause) => {
				if (!cancelled) error = (cause as Error).message;
			})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	const questions = $derived.by(() => {
		const map = new Map<string, Question>();
		for (const row of rows) {
			const perQuestion = new Map<string, number>();
			for (const chip of row.chips) {
				perQuestion.set(chip.questionKey, (perQuestion.get(chip.questionKey) ?? 0) + 1);
			}
			for (const [key, evidence] of perQuestion) {
				const chip = row.chips.find((entry) => entry.questionKey === key)!;
				const held = map.get(key) ?? {
					key,
					title: chip.questionTitle,
					text: chip.question,
					theme: chip.theme,
					polarity: chip.polarity,
					found: [],
					total: 0
				};
				held.found.push({
					id: row.id,
					name: row.name,
					domain: row.domain,
					band: row.band,
					evidence
				});
				held.total += 1;
				map.set(key, held);
			}
		}
		return [...map.values()]
			.map((question) => ({
				...question,
				found: question.found.sort((a, b) => b.evidence - a.evidence || a.name.localeCompare(b.name))
			}))
			.sort((a, b) => a.theme.localeCompare(b.theme) || b.total - a.total);
	});

	const groups = $derived.by(() => {
		const seen = new Map<string, Question[]>();
		for (const question of questions) {
			seen.set(question.theme, [...(seen.get(question.theme) ?? []), question]);
		}
		return [...seen.entries()].map(([name, items]) => ({ name, items }));
	});

	const appearances = $derived.by(() => {
		const count = new Map<string, number>();
		for (const question of questions) {
			for (const company of question.found) {
				count.set(company.id, (count.get(company.id) ?? 0) + 1);
			}
		}
		return count;
	});

	const repeated = $derived([...appearances.values()].filter((n) => n > 1).length);

	function shows(company: Found): boolean {
		return !onlyShared || (appearances.get(company.id) ?? 0) > 1;
	}

	function lit(company: Found): boolean {
		return hovered === null || hovered === company.id;
	}

	function bandWord(band: string): string {
		if (band === 'PRIORITY') return 'Priority';
		if (band === 'NURTURE') return 'Nurture';
		if (band === 'WATCH') return 'Watch';
		if (band === 'THIN') return 'Unproven';
		return 'Out of the queue';
	}

	const signals = $derived(rows.reduce((sum, row) => sum + row.signalCount, 0));
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Signal map</h1>
			<p class="sub">
				{serviceLine} · every question the board asks and the companies it found. A mark that appears under
				more than one question is a company with more than one reason to call.
			</p>
		</div>
	</header>

	<div class="toolbar">
		<label class="check">
			<input type="checkbox" bind:checked={onlyShared} />
			<span>Only the {repeated} found by more than one question</span>
		</label>
		<span class="spacer"></span>
		<span class="sub dim num">
			{questions.length} questions · {shortNumber(signals)} signals · {rows.length} companies
		</span>
	</div>

	<section class="pane scroll" aria-label="Every question and what it found">
		<LoadingVeil show={loading} label="Building the map" />

		{#if error}
			<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
		{:else if questions.length === 0 && !loading}
			<div class="empty">
				<Icon name="inbox" size={22} />
				<p>No question on this service line has found anything yet.</p>
			</div>
		{:else}
			<div class="map">
				{#each groups as group (group.name)}
					<div class="group">
						<p class="group-name">
							{theme(group.name)}
							<span class="num group-count">{group.items.length}</span>
						</p>
						{#each group.items as question (question.key)}
							<div class="row" class:quiet={question.polarity !== 'POSITIVE'}>
								<span class="q" title={question.text}>
									<span class="q-title">{question.title}</span>
									<span class="q-count num">{question.total}</span>
								</span>
								<div class="found">
									{#each question.found.filter(shows).slice(0, SHOWN) as company (company.id)}
										<a
											class="mark"
											class:lit={lit(company)}
											class:dim={!lit(company)}
											href={`/companies/${company.id}`}
											data-band={company.band}
											title={`${company.name} — ${bandWord(company.band)}, ${plural(company.evidence, 'document')}`}
											onmouseenter={() => (hovered = company.id)}
											onmouseleave={() => (hovered = null)}
										>
											<CompanyMark name={company.name} domain={company.domain} />
										</a>
									{/each}
									{#if question.found.filter(shows).length === 0}
										<span class="sub dim">nobody here is found by another question</span>
									{:else if !onlyShared && question.total > SHOWN}
										<span class="more sub dim">+{question.total - SHOWN}</span>
									{/if}
								</div>
							</div>
						{/each}
					</div>
				{/each}
			</div>
		{/if}
	</section>
</div>

<style>
	.toolbar .spacer {
		margin-left: auto;
	}

	.check {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		font-size: var(--text-sm);
		color: var(--color-ink-2);
		cursor: pointer;
	}

	.pane {
		position: relative;
		flex: 1;
		min-height: 0;
		background: var(--color-surface);
	}

	.map {
		padding: var(--spacing-2) 0 var(--spacing-5);
	}

	.group + .group {
		border-top: 1px solid var(--color-rule);
		margin-top: var(--spacing-3);
		padding-top: var(--spacing-3);
	}

	.group-name {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
		padding: 0 var(--spacing-4) var(--spacing-1);
		font-family: var(--font-display);
		font-size: var(--text-md);
		font-weight: 600;
		color: var(--color-ink);
	}

	.group-count {
		font-size: var(--text-micro);
		color: var(--color-ink-4);
	}

	.row {
		display: grid;
		grid-template-columns: 300px minmax(0, 1fr);
		align-items: center;
		gap: var(--spacing-3);
		padding: var(--spacing-1) var(--spacing-4);
	}

	.row:hover {
		background: var(--color-paper);
	}

	.q {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--spacing-2);
		min-width: 0;
		padding: 3px var(--spacing-2);
		border-left: 3px solid var(--color-ink);
		background: var(--color-paper);
	}

	.row.quiet .q {
		border-left-color: var(--color-oxblood);
	}

	.q:hover {
		background: var(--color-orange-wash);
	}

	.q-title {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: var(--text-base);
		font-weight: 500;
		color: var(--color-ink);
	}

	.q-count {
		flex: none;
		font-size: var(--text-micro);
		color: var(--color-ink-3);
	}

	.found {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 4px;
		min-width: 0;
	}

	.mark {
		display: block;
		border-radius: 2px;
		transition: opacity var(--ease), box-shadow var(--ease);
	}

	.mark :global(.co-mark) {
		width: 26px;
		height: 26px;
	}

	.mark.dim {
		opacity: 0.22;
	}

	.mark.lit {
		box-shadow: 0 0 0 2px var(--color-surface), 0 0 0 3px var(--color-ink);
	}

	.mark[data-band='DISQUALIFIED'] :global(.co-mark) {
		opacity: 0.45;
	}

	.more {
		font-size: var(--text-micro);
		padding-left: var(--spacing-1);
	}

	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: var(--spacing-2);
		height: 100%;
		color: var(--color-ink-3);
	}

	@media (max-width: 1100px) {
		.row {
			grid-template-columns: minmax(0, 1fr);
			gap: var(--spacing-1);
			padding-bottom: var(--spacing-2);
		}
	}
</style>
