<script lang="ts">
	import {
		createPolicy,
		deletePolicy,
		fetchPolicies,
		previewPolicy,
		rescore,
		updatePolicy,
		type Policy,
		type PolicyInput,
		type Preview
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { duration, plural, shortNumber, theme, tier } from '$lib/format';

	const BLANK: PolicyInput = {
		serviceLine: '',
		text: '',
		instructions: '',
		weightTier: 'HIGH',
		polarity: 'POSITIVE',
		theme: 'custom',
		decayClass: 'STRUCTURAL',
		cues: [],
		counterCues: [],
		active: true
	};

	let policies = $state<Policy[]>([]);
	let loading = $state(true);
	let selectedId = $state<string | null>(null);
	let draft = $state<PolicyInput>({ ...BLANK });
	let creating = $state(false);
	let saving = $state(false);
	let dirty = $state(false);
	let preview = $state<Preview | null>(null);
	let previewing = $state(false);
	let rescoring = $state(false);
	let lastRescore = $state<string | null>(null);

	const serviceLine = $derived(session.serviceLine);
	const selected = $derived(policies.find((policy) => policy.id === selectedId) ?? null);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		loading = true;
		fetchPolicies().then((result) => {
			policies = result;
			loading = false;
			if (!selectedId) {
				const first = result.find((policy) => policy.serviceLine === line) ?? result[0];
				if (first) select(first);
			}
		});
	});

	$effect(() => {
		const text = draft.text;
		const instructions = draft.instructions;
		const line = draft.serviceLine;
		if (text.trim().length < 10 || !line) {
			preview = null;
			return;
		}
		previewing = true;
		const timer = setTimeout(() => {
			previewPolicy({
				serviceLine: line,
				text,
				instructions,
				tier: draft.weightTier,
				polarity: draft.polarity,
				theme: draft.theme
			})
				.then((result) => (preview = result))
				.catch(() => (preview = null))
				.finally(() => (previewing = false));
		}, 550);
		return () => clearTimeout(timer);
	});

	function select(policy: Policy) {
		selectedId = policy.id;
		creating = false;
		dirty = false;
		draft = {
			serviceLine: policy.serviceLine,
			text: policy.text,
			instructions: policy.instructions,
			weightTier: policy.weightTier,
			polarity: policy.polarity,
			theme: policy.theme,
			decayClass: policy.decayClass,
			cues: policy.cues,
			counterCues: policy.counterCues,
			active: policy.active
		};
	}

	function startNew() {
		selectedId = null;
		creating = true;
		dirty = true;
		draft = { ...BLANK, serviceLine: serviceLine || '' };
	}

	function markDirty() {
		dirty = true;
	}

	async function save() {
		if (draft.text.trim().length < 10) {
			toasts.push('Write the question a sales manager would ask.', 'warn');
			return;
		}
		saving = true;
		try {
			const saved = creating
				? await createPolicy(draft)
				: await updatePolicy(selectedId as string, draft);
			policies = await fetchPolicies();
			select(saved);
			toasts.push('Saved. Re-score to apply it to the board.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			saving = false;
		}
	}

	async function remove() {
		if (!selectedId) return;
		try {
			await deletePolicy(selectedId);
			policies = await fetchPolicies();
			selectedId = null;
			creating = false;
			if (policies.length > 0) select(policies[0]);
			toasts.push('Question deleted, along with every signal it produced.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function applyToBoard() {
		if (!draft.serviceLine) return;
		rescoring = true;
		lastRescore = null;
		try {
			const result = await rescore({
				serviceLine: draft.serviceLine,
				questionId: selectedId,
				question: draft.text
			});
			lastRescore = `${result.movedRows} rows moved in ${duration(result.tookMs)} across ${shortNumber(result.documentsScanned)} documents.`;
			toasts.push(lastRescore, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			rescoring = false;
		}
	}

	const grouped = $derived.by(() => {
		const map = new Map<string, Policy[]>();
		for (const policy of policies) {
			const list = map.get(policy.serviceLine) ?? [];
			list.push(policy);
			map.set(policy.serviceLine, list);
		}
		return [...map.entries()];
	});
</script>

<svelte:head><title>Signal Studio · Orange Systems</title></svelte:head>

<div class="head">
	<div class="stack gap-4">
		<h1>Signal Studio</h1>
		<p class="muted small">
			A signal question is a plain-language question plus the rubric that says what counts. The board is nothing
			more than the current set of these.
		</p>
	</div>
	<button class="btn btn-primary" onclick={startNew}>New question</button>
</div>

<div class="layout">
	<aside class="card list">
		{#if loading}
			<p class="empty"><span class="spinner"></span> Loading…</p>
		{:else}
			{#each grouped as [line, items] (line)}
				<div class="group">
					<h3>{line}</h3>
					{#each items as policy (policy.id)}
						<button class="item" class:active={policy.id === selectedId} onclick={() => select(policy)}>
							<div class="row gap-6" style="flex-wrap: wrap">
								<span class="chip chip-orange tiny">{tier(policy.weightTier)}</span>
								{#if policy.polarity === 'DISQUALIFYING'}
									<span class="chip chip-danger tiny">disqualifying</span>
								{:else if policy.polarity === 'NEGATIVE'}
									<span class="chip tiny">counter</span>
								{/if}
								<span class="chip tiny">{theme(policy.theme)}</span>
								{#if !policy.active}<span class="chip tiny">off</span>{/if}
							</div>
							<span class="item-text">{policy.text}</span>
							<span class="tiny muted">
								{plural(policy.signalCount, 'signal')} · half-life {policy.halfLifeDays}d
							</span>
						</button>
					{/each}
				</div>
			{/each}
		{/if}
	</aside>

	<div class="stack gap-16" style="min-width: 0">
		<section class="card editor">
			<div class="row" style="justify-content: space-between; flex-wrap: wrap; gap: 10px">
				<h3>{creating ? 'New signal question' : (selected?.key ?? 'Signal question')}</h3>
				{#if dirty}<span class="chip chip-orange tiny">unsaved changes</span>{/if}
			</div>

			<label class="stack gap-4">
				<span class="small">The question, in the words a sales manager would use</span>
				<textarea
					class="textarea"
					rows="3"
					bind:value={draft.text}
					oninput={markDirty}
					placeholder="Is the company consolidating shared service centres into a captive operation?"
				></textarea>
			</label>

			<label class="stack gap-4">
				<span class="small">Rubric — what counts, and what does not</span>
				<textarea
					class="textarea"
					rows="5"
					bind:value={draft.instructions}
					oninput={markDirty}
					placeholder="Counts: …  Does not count: …"
				></textarea>
				<span class="tiny muted">
					This paragraph is the whole trick: it lets the classifier make the same business inference a
					senior rep would, instead of matching the word “automation”.
				</span>
			</label>

			<div class="fields">
				<label class="stack gap-4">
					<span class="tiny muted">Service line</span>
					<input class="input" bind:value={draft.serviceLine} oninput={markDirty} list="service-lines" />
					<datalist id="service-lines">
						{#each [...new Set(policies.map((policy) => policy.serviceLine))] as line (line)}
							<option value={line}></option>
						{/each}
					</datalist>
				</label>
				<label class="stack gap-4">
					<span class="tiny muted">Importance</span>
					<select class="select" bind:value={draft.weightTier} onchange={markDirty}>
						<option value="HIGH">High — 1.5 nats</option>
						<option value="MEDIUM">Medium — 0.7 nats</option>
						<option value="LOW">Low — 0.3 nats</option>
					</select>
				</label>
				<label class="stack gap-4">
					<span class="tiny muted">Direction</span>
					<select class="select" bind:value={draft.polarity} onchange={markDirty}>
						<option value="POSITIVE">Positive</option>
						<option value="NEGATIVE">Counter-evidence</option>
						<option value="DISQUALIFYING">Disqualifying</option>
					</select>
				</label>
				<label class="stack gap-4">
					<span class="tiny muted">Theme</span>
					<input class="input" bind:value={draft.theme} oninput={markDirty} placeholder="automation" />
				</label>
				<label class="stack gap-4">
					<span class="tiny muted">How it ages</span>
					<select class="select" bind:value={draft.decayClass} onchange={markDirty}>
						<option value="EVENT">Point event — half-life 30d</option>
						<option value="HIRING">Hiring or leadership — 90d</option>
						<option value="STRUCTURAL">Structural — 180d</option>
					</select>
				</label>
				<label class="row gap-6" style="align-items: flex-end; padding-bottom: 8px">
					<input type="checkbox" bind:checked={draft.active} onchange={markDirty} />
					<span class="small">Active</span>
				</label>
			</div>

			<div class="row gap-8" style="flex-wrap: wrap">
				<button class="btn btn-primary" onclick={save} disabled={saving}>
					{#if saving}<span class="spinner"></span>{/if}
					{creating ? 'Create the question' : 'Save changes'}
				</button>
				<button class="btn" onclick={applyToBoard} disabled={rescoring || !draft.serviceLine}>
					{#if rescoring}<span class="spinner"></span>{/if}
					{rescoring ? 'Re-scoring…' : 'Apply to the board now'}
				</button>
				{#if !creating && selectedId}
					<button class="btn btn-danger" style="margin-left: auto" onclick={remove}>
						Delete
					</button>
				{/if}
			</div>

			{#if lastRescore}
				<p class="small" style="color: var(--success)">{lastRescore}</p>
			{/if}
		</section>

		<section class="card preview">
			<div class="row" style="justify-content: space-between; flex-wrap: wrap; gap: 10px">
				<h3>Live preview</h3>
				{#if previewing}<span class="row gap-6 tiny muted"><span class="spinner"></span> scanning…</span>{/if}
			</div>

			{#if !preview}
				<p class="small muted">
					Start typing a question and the corpus is scanned as you go. The preview runs on the local rubric
					engine so it is instant and deterministic; the full engine runs when you apply the question.
				</p>
			{:else}
				<div class="tally">
					<div>
						<span class="mono big">{preview.matchedCompanies}</span>
						<span class="tiny muted">companies match</span>
					</div>
					<div>
						<span class="mono big">{shortNumber(preview.matchedEvidence)}</span>
						<span class="tiny muted">of {shortNumber(preview.scannedEvidence)} documents</span>
					</div>
					<div>
						<span class="mono big">{duration(preview.tookMs)}</span>
						<span class="tiny muted">scan time</span>
					</div>
					<div>
						<span class="mono tiny">{preview.engine}</span>
						<span class="tiny muted">preview engine</span>
					</div>
				</div>

				{#if preview.matches.length > 0}
					<table class="grid">
						<thead>
							<tr>
								<th>Company</th>
								<th style="width: 90px; text-align: right">Documents</th>
								<th style="width: 90px; text-align: right">Top confidence</th>
								<th>Strongest passage</th>
							</tr>
						</thead>
						<tbody>
							{#each preview.matches.slice(0, 12) as match (match.companyId)}
								<tr>
									<td><a href="/companies/{match.companyId}">{match.company}</a></td>
									<td style="text-align: right" class="mono">{match.evidence}</td>
									<td style="text-align: right" class="mono">{match.topConfidence.toFixed(2)}</td>
									<td class="tiny muted quote-cell">“{match.quote}”</td>
								</tr>
							{/each}
						</tbody>
					</table>
					{#if preview.matches.length > 12}
						<p class="tiny muted">Showing the 12 strongest of {preview.matchedCompanies}.</p>
					{/if}
				{:else}
					<p class="small muted">
						Nothing in the corpus answers this question. That is a real result — it means the question
						would not move the board, so it needs different wording or a different source.
					</p>
				{/if}
			{/if}
		</section>
	</div>
</div>

<style>
	.head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 20px;
		margin-bottom: 16px;
	}

	.layout {
		display: grid;
		grid-template-columns: minmax(280px, 330px) minmax(0, 1fr);
		gap: 16px;
		align-items: start;
	}

	.list {
		padding: 12px;
		max-height: calc(100vh - 200px);
		overflow: auto;
	}

	.group + .group {
		margin-top: 14px;
	}

	.group h3 {
		margin: 0 6px 6px;
	}

	.item {
		display: flex;
		flex-direction: column;
		gap: 5px;
		width: 100%;
		text-align: left;
		border: 1px solid transparent;
		background: transparent;
		border-radius: var(--radius);
		padding: 9px 10px;
	}

	.item:hover {
		background: var(--surface-sunken);
	}

	.item.active {
		background: var(--orange-050);
		border-color: var(--orange-200);
	}

	.item-text {
		font-size: 13px;
		line-height: 1.4;
		color: var(--ink-2);
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.editor,
	.preview {
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}

	.fields {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
		gap: 12px;
	}

	.tally {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
		gap: 10px;
	}

	.tally > div {
		display: flex;
		flex-direction: column;
		background: var(--surface-sunken);
		border: 1px solid var(--line);
		border-radius: var(--radius);
		padding: 9px 11px;
	}

	.big {
		font-size: 21px;
		font-weight: 600;
		line-height: 1.1;
	}

	.quote-cell {
		max-width: 380px;
		font-style: italic;
	}

	.empty {
		padding: 16px;
		display: flex;
		align-items: center;
		gap: 8px;
		color: var(--ink-3);
	}

	@media (max-width: 1080px) {
		.layout {
			grid-template-columns: 1fr;
		}
		.list {
			max-height: 340px;
		}
	}
</style>
