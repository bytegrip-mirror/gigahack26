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
	import { duration, shortNumber, theme, tier } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';

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
	let dirty = $state(false);
	let saving = $state(false);
	let preview = $state<Preview | null>(null);
	let previewing = $state(false);
	let applying = $state(false);
	let applied = $state<string | null>(null);

	const serviceLine = $derived(session.serviceLine);
	const current = $derived(policies.find((policy) => policy.id === selectedId) ?? null);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		fetchPolicies()
			.then((result) => {
				if (cancelled) return;
				policies = result;
				loading = false;
				if (!selectedId) {
					const first = result.find((policy) => policy.serviceLine === line) ?? result[0];
					if (first) select(first);
				}
			})
			.catch(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		const text = draft.text;
		const instructions = draft.instructions;
		const line = draft.serviceLine;
		const tierValue = draft.weightTier;
		const polarityValue = draft.polarity;
		if (text.trim().length < 10 || !line) {
			preview = null;
			return;
		}
		let cancelled = false;
		previewing = true;
		const timer = setTimeout(() => {
			previewPolicy({
				serviceLine: line,
				text,
				instructions,
				tier: tierValue,
				polarity: polarityValue
			})
				.then((result) => {
					if (!cancelled) preview = result;
				})
				.catch(() => {
					if (!cancelled) preview = null;
				})
				.finally(() => {
					if (!cancelled) previewing = false;
				});
		}, 500);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
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
			selectedId = saved.id;
			policies = await fetchPolicies();
			select(saved);
			toasts.push('Saved.', 'success');
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
			toasts.push('Question deleted with the signals it produced.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function apply() {
		if (!draft.serviceLine) return;
		applying = true;
		applied = null;
		try {
			const result = await rescore({
				serviceLine: draft.serviceLine,
				questionId: selectedId,
				question: draft.text
			});
			applied = `${result.movedRows} rows moved in ${duration(result.tookMs)}`;
			toasts.push(applied, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			applying = false;
		}
	}

	const scoped = $derived.by(() => {
		const map = new Map<string, Policy[]>();
		for (const policy of policies) {
			const list = map.get(policy.serviceLine) ?? [];
			list.push(policy);
			map.set(policy.serviceLine, list);
		}
		const mine = [...map.entries()].filter(([line]) => line === serviceLine);
		return mine.length > 0 ? mine : [...map.entries()];
	});
	const elsewhere = $derived(policies.filter((policy) => policy.serviceLine !== serviceLine).length);
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Signal Studio</h1>
			<p class="label">{policies.length} questions · board re-ranks from these alone</p>
		</div>
		<button class="btn btn-primary" onclick={startNew}>New question</button>
	</header>

	<div class="panes">
		<section class="list scroll" aria-label="Signal questions">
			{#if loading}
				<p class="note"><span class="spinner"></span> Loading</p>
			{:else}
				{#each scoped as [line, items] (line)}
					<div class="group">
						<p class="label">{line} · {items.length}</p>
						<ul>
							{#each items as policy (policy.id)}
								<li>
									<button
										class="q"
										aria-current={policy.id === selectedId ? 'true' : undefined}
										onclick={() => select(policy)}
									>
										<span class="q-tags">
											<Tag>{tier(policy.weightTier)}</Tag>
											{#if policy.polarity !== 'POSITIVE'}
												<Tag variant={policy.polarity === 'DISQUALIFYING' ? 'oxblood' : 'default'}>
													{policy.polarity === 'DISQUALIFYING' ? 'disqualifying' : 'counter'}
												</Tag>
											{/if}
											<Tag variant="bare">{theme(policy.theme)}</Tag>
											{#if !policy.active}<Tag variant="bare">off</Tag>{/if}
										</span>
										<span class="q-text">{policy.text}</span>
										<span class="num sub dim">{policy.signalCount} signals · H{policy.halfLifeDays}d</span>
									</button>
								</li>
							{/each}
						</ul>
					</div>
				{/each}
				{#if elsewhere > 0}
					<p class="sub dim pad">{elsewhere} more on other service lines.</p>
				{/if}
			{/if}
		</section>

		<section class="editor scroll" aria-label="Question editor">
			<div class="block">
				<div class="block-head">
					<p class="label">{creating ? 'New question' : (current?.key ?? 'Question')}</p>
					{#if dirty}<Tag variant="wash">unsaved</Tag>{/if}
				</div>

				<label class="field">
					<span class="label">Question</span>
					<textarea
						class="textarea"
						rows="3"
						bind:value={draft.text}
						oninput={() => (dirty = true)}
						placeholder="Is the company consolidating shared service centres into a captive operation?"
					></textarea>
				</label>

				<label class="field">
					<span class="label">Rubric — what counts, and what does not</span>
					<textarea
						class="textarea"
						rows="5"
						bind:value={draft.instructions}
						oninput={() => (dirty = true)}
						placeholder="Counts: …  Does not count: …"
					></textarea>
					<span class="sub dim">
						The rubric lets the classifier make the inference a senior rep would, rather than matching a word.
					</span>
				</label>

				<div class="grid5">
					<label class="field">
						<span class="label">Service line</span>
						<input class="input" bind:value={draft.serviceLine} oninput={() => (dirty = true)} />
					</label>
					<label class="field">
						<span class="label">Weight</span>
						<select class="select" bind:value={draft.weightTier} onchange={() => (dirty = true)}>
							<option value="HIGH">High · 1.5</option>
							<option value="MEDIUM">Medium · 0.7</option>
							<option value="LOW">Low · 0.3</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Polarity</span>
						<select class="select" bind:value={draft.polarity} onchange={() => (dirty = true)}>
							<option value="POSITIVE">Positive</option>
							<option value="NEGATIVE">Counter</option>
							<option value="DISQUALIFYING">Disqualifying</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Theme</span>
						<input class="input" bind:value={draft.theme} oninput={() => (dirty = true)} />
					</label>
					<label class="field">
						<span class="label">Half-life</span>
						<select class="select" bind:value={draft.decayClass} onchange={() => (dirty = true)}>
							<option value="EVENT">Event · 30d</option>
							<option value="HIRING">Hiring · 90d</option>
							<option value="STRUCTURAL">Structural · 180d</option>
						</select>
					</label>
				</div>

				<div class="actions">
					<button class="btn btn-ink" onclick={save} disabled={saving}>
						{#if saving}<span class="spinner"></span>{/if}
						{creating ? 'Create' : 'Save'}
					</button>
					<button class="btn" onclick={apply} disabled={applying || !draft.serviceLine}>
						{#if applying}<span class="spinner"></span>{/if}
						{applying ? 'Scoring' : 'Apply to board'}
					</button>
					<label class="toggle">
						<input type="checkbox" bind:checked={draft.active} onchange={() => (dirty = true)} />
						<span>Active</span>
					</label>
					{#if applied}<span class="sub dim">{applied}</span>{/if}
					{#if !creating && selectedId}
						<button class="btn btn-quiet btn-sm del" onclick={remove}>Delete</button>
					{/if}
				</div>
			</div>

			<div class="block preview">
				<div class="block-head">
					<p class="label">Live preview</p>
					{#if previewing}<span class="sub dim"><span class="spinner"></span> scanning</span>{/if}
				</div>

				{#if !preview}
					<p class="sub dim">
						The corpus is scanned as you type, on the local rubric engine, so it is instant and deterministic.
						The full engine runs when you apply.
					</p>
				{:else}
					<div class="tally">
						<div><span class="num big">{preview.matchedCompanies}</span><span class="label">companies</span></div>
						<div>
							<span class="num big">{shortNumber(preview.matchedEvidence)}</span>
							<span class="label">of {shortNumber(preview.scannedEvidence)} docs</span>
						</div>
						<div><span class="num big">{duration(preview.tookMs)}</span><span class="label">scan</span></div>
						<div><span class="sub dim">{preview.engine}</span><span class="label">engine</span></div>
					</div>

					{#if preview.matches.length > 0}
						<table class="grid">
							<thead>
								<tr>
									<th>Company</th>
									<th class="n">Docs</th>
									<th class="n">Top conf</th>
									<th>Passage</th>
								</tr>
							</thead>
							<tbody>
								{#each preview.matches.slice(0, 10) as match (match.companyId)}
									<tr>
										<td><a href="/?co={match.companyId}">{match.company}</a></td>
										<td class="num n">{match.evidence}</td>
										<td class="num n">{match.topConfidence.toFixed(2)}</td>
										<td class="small-quote">“{match.quote}”</td>
									</tr>
								{/each}
							</tbody>
						</table>
						{#if preview.matches.length > 10}
							<p class="sub dim">10 strongest of {preview.matchedCompanies}.</p>
						{/if}
					{:else}
						<p class="sub dim">
							Nothing in the corpus answers this. That is a result: the question would not move the board.
						</p>
					{/if}
				{/if}
			</div>
		</section>
	</div>
</div>

<style>
	.view {
		display: flex;
		flex-direction: column;
		height: 100%;
		min-height: 0;
	}

	.topbar {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: 20px;
		padding: 11px 16px 12px;
		border-bottom: 1px solid var(--ink);
		background: var(--surface);
	}

	h1 {
		font-size: 23px;
		line-height: 1.1;
	}

	.ident .label {
		margin-top: 3px;
	}

	.panes {
		flex: 1;
		min-height: 0;
		display: grid;
		grid-template-columns: 296px minmax(0, 1fr);
	}

	.list {
		min-height: 0;
		border-right: 1px solid var(--rule);
		background: var(--paper);
		padding: 10px 0 16px;
	}

	.group + .group {
		margin-top: 10px;
	}

	.group > .label {
		padding: 0 13px 5px;
	}

	.group ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.q {
		display: flex;
		flex-direction: column;
		gap: 4px;
		width: 100%;
		text-align: left;
		padding: 8px 13px 8px 11px;
		border-left: 2px solid transparent;
		border-bottom: 1px solid var(--rule);
		transition: background var(--ease), border-color var(--ease);
	}

	.q:hover {
		background: var(--surface);
	}

	.q[aria-current='true'] {
		background: var(--surface);
		border-left-color: var(--orange);
	}

	.q-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.q-text {
		font-size: 12.5px;
		line-height: 1.4;
		color: var(--ink-2);
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.sub {
		font-size: 11.5px;
		line-height: 1.45;
		color: var(--ink-3);
	}

	.pad {
		padding: 10px 13px 0;
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 12px 13px;
		font-size: 13px;
		color: var(--ink-3);
	}

	.editor {
		min-height: 0;
		overflow-y: auto;
		background: var(--surface);
	}

	.block {
		padding: 14px 16px;
		border-bottom: 1px solid var(--rule);
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.block-head {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.grid5 {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
		gap: 10px;
	}

	.actions {
		display: flex;
		align-items: center;
		gap: 7px;
		flex-wrap: wrap;
	}

	.toggle {
		display: flex;
		align-items: center;
		gap: 5px;
		font-size: 12.5px;
		color: var(--ink-2);
	}

	.del {
		margin-left: auto;
		color: var(--oxblood);
	}

	.preview {
		border-bottom: 0;
	}

	.tally {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
		gap: 8px;
	}

	.tally > div {
		display: flex;
		flex-direction: column;
		gap: 2px;
		border: 1px solid var(--rule);
		padding: 8px 10px;
	}

	.big {
		font-size: 22px;
		font-weight: 500;
		line-height: 1.05;
	}

	.n {
		text-align: right;
		width: 60px;
	}

	.small-quote {
		font-family: var(--font-display);
		font-size: 12.5px;
		font-style: italic;
		line-height: 1.4;
		color: var(--ink-2);
	}

	@media (max-width: 1000px) {
		.panes {
			grid-template-columns: 1fr;
			grid-template-rows: auto minmax(0, 1fr);
		}
		.list {
			border-right: 0;
			border-bottom: 1px solid var(--rule);
			max-height: 220px;
		}
	}
</style>
