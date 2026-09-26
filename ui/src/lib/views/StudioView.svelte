<script lang="ts">
	import { page } from '$app/state';
	import {
		createPolicy,
		deletePolicy,
		fetchPolicies,
		exportPolicies as exportPolicySet,
		fetchDecayClasses,
		fetchServiceLines,
		fetchThemes,
		importPolicies,
		deriveCues,
		previewPolicy,
		rescore,
		updatePolicy,
		type DecayOption,
		type Policy,
		type PolicyInput,
		type ServiceLine,
		type Preview
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { duration, shortNumber, theme, tier } from '$lib/format';
	import { copy, csv, save } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import type { MenuItem } from '$lib/components/menu';

	type Pending = { kind: 'open' | 'new' | 'delete'; policy?: Policy };

	const BLANK: PolicyInput = {
		title: '',
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
	let serviceLines = $state<ServiceLine[]>([]);
	let themes = $state<string[]>([]);
	let decayClasses = $state<DecayOption[]>([]);
	let loading = $state(true);
	let loadError = $state<string | null>(null);
	let selectedId = $state<string | null>(null);
	let draft = $state<PolicyInput>({ ...BLANK });
	let creating = $state(false);
	let dirty = $state(false);
	let saving = $state(false);
	let preview = $state<Preview | null>(null);
	let previewing = $state(false);
	let derivedCues = $state<string[]>([]);
	let cueDraft = $state('');
	let counterDraft = $state('');
	let veil = $state(false);
	let applying = $state(false);
	let query = $state('');
	let pending = $state<Pending | null>(null);

	const serviceLine = $derived(session.serviceLine);
	const current = $derived(policies.find((policy) => policy.id === selectedId) ?? null);
	const editable = $derived(creating || current !== null || loading);

	const pendingNote = $derived.by(() => {
		if (!pending) return '';
		if (pending.kind === 'new') return 'Unsaved changes to this question — starting a new one discards them.';
		if (pending.kind === 'delete') return 'Unsaved changes to this question — deleting another one discards them.';
		return `Unsaved changes to this question — opening “${pending.policy?.title ?? 'another question'}” discards them.`;
	});

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		Promise.all([fetchPolicies(), fetchServiceLines(false), fetchThemes(), fetchDecayClasses()])
			.then(([result, lines, themeList, decays]) => {
				if (cancelled) return;
				serviceLines = lines;
				themes = themeList;
				decayClasses = decays;
				policies = result;
				loading = false;
				const wanted = page.url.searchParams.get('q');
				const asked = wanted ? result.find((policy) => policy.key === wanted) : undefined;
				if (asked) select(asked);
				else if (!selectedId) {
					const first = result.find((policy) => policy.serviceLine === line) ?? result[0];
					if (first) select(first);
				}
			})
			.catch((cause) => {
				if (cancelled) return;
				loading = false;
				loadError = (cause as Error).message;
			});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		if (!previewing) {
			veil = false;
			return;
		}
		const timer = setTimeout(() => (veil = true), 220);
		return () => clearTimeout(timer);
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
		deriveCues(text, instructions)
			.then((list) => {
				if (!cancelled) derivedCues = list;
			})
			.catch(() => {
				if (!cancelled) derivedCues = [];
			});
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

	function toInput(policy: Policy): PolicyInput {
		return {
			title: policy.title,
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

	function select(policy: Policy) {
		selectedId = policy.id;
		creating = false;
		dirty = false;
		draft = toInput(policy);
	}

	function startNew(line = serviceLine) {
		selectedId = null;
		creating = true;
		dirty = true;
		draft = { ...BLANK, serviceLine: line || '' };
	}

	function guard(kind: Pending['kind'], policy?: Policy): boolean {
		if (!dirty) return false;
		pending = { kind, policy };
		return true;
	}

	function openQuestion(policy: Policy) {
		if (guard('open', policy)) return;
		select(policy);
	}

	function newQuestion() {
		if (guard('new')) return;
		startNew();
	}

	function keepEditing() {
		pending = null;
	}

	async function deleteQuestion(policy: Policy) {
		if (policy.id !== selectedId && guard('delete', policy)) return;
		select(policy);
		await remove();
	}

	async function resolvePending(action: 'save' | 'discard') {
		const target = pending;
		if (!target) return;
		if (action === 'save' && !(await save_())) return;
		pending = null;
		if (target.kind === 'new') {
			startNew();
			return;
		}
		if (!target.policy) return;
		select(target.policy);
		if (target.kind === 'delete') await remove();
	}

	function decayOf(name: string): number | null {
		return decayClasses.find((entry) => entry.name === name)?.halfLifeDays ?? null;
	}

	function normaliseCue(value: string) {
		return value.trim().toLowerCase().replace(/\s+/g, ' ');
	}

	function addCue() {
		const cue = normaliseCue(cueDraft);
		if (cue.length < 3) return;
		draft.cues = [...(draft.cues ?? []).filter((existing) => existing !== cue), cue];
		cueDraft = '';
		dirty = true;
	}

	function dropCue(cue: string) {
		draft.cues = (draft.cues ?? []).filter((existing) => existing !== cue);
		dirty = true;
	}

	function keepDerived() {
		draft.cues = [...derivedCues];
		dirty = true;
	}

	function addCounterCue() {
		const cue = normaliseCue(counterDraft);
		if (cue.length < 3) return;
		draft.counterCues = [...(draft.counterCues ?? []).filter((existing) => existing !== cue), cue];
		counterDraft = '';
		dirty = true;
	}

	function dropCounterCue(cue: string) {
		draft.counterCues = (draft.counterCues ?? []).filter((existing) => existing !== cue);
		dirty = true;
	}

	async function save_(): Promise<boolean> {
		if (draft.text.trim().length < 10) {
			toasts.push('Write the question a sales manager would ask.', 'warn');
			return false;
		}
		saving = true;
		try {
			const saved =
				creating || !selectedId ? await createPolicy(draft) : await updatePolicy(selectedId, draft);
			selectedId = saved.id;
			policies = await fetchPolicies();
			select(saved);
			pending = null;
			toasts.push('Saved.', 'success');
			return true;
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
			return false;
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
			pending = null;
			if (policies.length > 0) select(policies[0]);
			toasts.push('Question deleted with the signals it produced.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function duplicate(policy: Policy) {
		try {
			const created = await createPolicy({ ...toInput(policy), title: `${policy.title} (copy)` });
			policies = await fetchPolicies();
			select(created);
			toasts.push('Duplicated. Edit it and save.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function toggleActive(policy: Policy) {
		try {
			const updated = await updatePolicy(policy.id, { ...toInput(policy), active: !policy.active });
			policies = await fetchPolicies();
			if (selectedId === policy.id) select(updated);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function apply() {
		if (!draft.serviceLine) return;
		applying = true;
		try {
			const result = await rescore({
				serviceLine: draft.serviceLine,
				questionId: selectedId,
				question: draft.text
			});
			toasts.push(`${result.movedRows} rows moved in ${duration(result.tookMs)}.`, 'success');
			policies = await fetchPolicies();
			workspace.reload();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			applying = false;
		}
	}

	const matched = $derived.by(() => {
		const needle = query.trim().toLowerCase();
		if (!needle) return policies;
		return policies.filter((policy) =>
			`${policy.title} ${policy.text} ${policy.theme} ${policy.serviceLine}`.toLowerCase().includes(needle)
		);
	});

	const grouped = $derived.by(() => {
		const map = new Map<string, Policy[]>();
		for (const policy of matched) {
			const list = map.get(policy.serviceLine) ?? [];
			list.push(policy);
			map.set(policy.serviceLine, list);
		}
		return [...map.entries()].sort(([a], [b]) => {
			if (a === serviceLine) return -1;
			if (b === serviceLine) return 1;
			return a.localeCompare(b);
		});
	});

	const scored = $derived(policies.filter((policy) => policy.active && policy.signalCount > 0).length);
	const draftHalfLife = $derived(decayOf(draft.decayClass) ?? 0);

	function exportPolicies() {
		save(
			'signal-questions.csv',
			csv(
				['title', 'service_line', 'question', 'rubric', 'weight', 'polarity', 'theme', 'half_life_days', 'active', 'signals'],
				policies.map((policy) => [
					policy.title,
					policy.serviceLine,
					policy.text,
					policy.instructions,
					policy.weightTier,
					policy.polarity,
					policy.theme,
					policy.halfLifeDays,
					policy.active,
					policy.signalCount
				])
			)
		);
		toasts.push(`Exported ${policies.length} questions.`, 'success');
	}

	function rowMenu(policy: Policy): MenuItem[] {
		return [
			{ id: 'open', label: 'Edit this question', icon: 'pencil' },
			{ id: 'duplicate', label: 'Duplicate', icon: 'copy' },
			{
				id: 'toggle',
				label: policy.active ? 'Turn off — stop scoring it' : 'Turn on',
				icon: policy.active ? 'close' : 'check'
			},
			{ id: 'key', label: 'Copy the question key', icon: 'link', hint: policy.key },
			{ id: 'delete', label: 'Delete', icon: 'trash', danger: true, separatorBefore: true }
		];
	}

	async function runRow(policy: Policy, id: string) {
		if (id === 'open') openQuestion(policy);
		else if (id === 'duplicate') await duplicate(policy);
		else if (id === 'toggle') await toggleActive(policy);
		else if (id === 'key') {
			const ok = await copy(policy.key);
			toasts.push(ok ? 'Question key copied.' : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
		} else if (id === 'delete') {
			await deleteQuestion(policy);
		}
	}

	const overflow: MenuItem[] = $derived([
		{ id: 'export', label: 'Export every question as CSV', icon: 'download' },
		{
			id: 'export-set',
			label: 'Export the policy set as JSON',
			icon: 'download',
			hint: 'carries cues and counter-cues'
		},
		{ id: 'import-set', label: 'Import a policy set', icon: 'plus' },
		{ id: 'duplicate', label: 'Duplicate this question', icon: 'copy', disabled: !current },
		{ id: 'delete', label: 'Delete this question', icon: 'trash', danger: true, separatorBefore: true, disabled: creating || !selectedId }
	]);

	async function exportSet() {
		try {
			const set = await exportPolicySet();
			save('policy-set.json', JSON.stringify(set, null, 1), 'application/json');
			toasts.push(`Exported ${set.length} questions as a set.`, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function importSet(event: Event) {
		const input = event.target as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (!file) return;
		try {
			const text = await file.text();
			const parsed = JSON.parse(text) as Parameters<typeof importPolicies>[0];
			if (!Array.isArray(parsed)) throw new Error('A policy set is a JSON array of questions.');
			const result = await importPolicies(parsed);
			policies = await fetchPolicies();
			toasts.push(
				`Imported: ${result.created} added, ${result.updated} replaced, ${result.skipped} skipped.`,
				'success'
			);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function runOverflow(id: string) {
		if (id === 'export') exportPolicies();
		else if (id === 'export-set') await exportSet();
		else if (id === 'import-set') document.getElementById('policy-set-input')?.click();
		else if (id === 'duplicate' && current) await duplicate(current);
		else if (id === 'delete') await remove();
	}
</script>

<input
	id="policy-set-input"
	type="file"
	accept="application/json,.json"
	class="sr-only"
	onchange={importSet}
/>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Signal Studio</h1>
			<p class="sub num">
				{#if loadError}
					The policy set could not be read — a failure, not an empty set
				{:else}
					{policies.length} questions · {scored} producing signals on {serviceLine}
				{/if}
			</p>
		</div>

		<div class="topbar-tools">
			<button class="btn btn-primary" onclick={() => newQuestion()}>
				<Icon name="plus" size={15} />
				New question
			</button>
			<Menu items={overflow} onselect={runOverflow} label="More studio actions" />
		</div>
	</header>

	<div class="panes">
		<section class="questions" aria-label="Signal questions">
			<div class="q-toolbar">
				<label class="search">
					<span class="sr-only">Filter questions</span>
					<Icon name="search" size={14} />
					<input class="input input-sm" placeholder="Filter questions" bind:value={query} />
				</label>
			</div>

			<div class="q-list scroll">
				<LoadingVeil show={loading} label="Loading questions" />
				{#if loading && policies.length === 0}
					<p class="note"><span class="spinner"></span> Loading</p>
				{:else}
					{#each grouped as [line, items] (line)}
						<div class="group">
							<p class="label group-head">{line} · {items.length}</p>
							<ul>
								{#each items as policy (policy.id)}
									<li>
										<button
											class="q"
											aria-current={policy.id === selectedId ? 'true' : undefined}
											onclick={() => openQuestion(policy)}
										>
											<span class="q-title">{policy.title}</span>
											<span class="q-text">{policy.text}</span>
											<span class="q-meta">
												{#if !policy.active}<Tag variant="bare">off</Tag>{/if}
												{#if policy.polarity !== 'POSITIVE'}
													<Tag variant={policy.polarity === 'DISQUALIFYING' ? 'oxblood' : 'default'}>
														{policy.polarity === 'DISQUALIFYING' ? 'removes them' : 'against them'}
													</Tag>
												{/if}
												<span class="q-theme">{theme(policy.theme)}</span>
												<span class="q-count num">{policy.signalCount} signals</span>
											</span>
										</button>
										<Menu
											items={rowMenu(policy)}
											onselect={(id) => runRow(policy, id)}
											size="sm"
											label={`Actions for ${policy.title}`}
										/>
									</li>
								{/each}
							</ul>
						</div>
					{/each}
					{#if !loading && policies.length === 0}
						<div class="pad empty">
							{#if loadError}
								<p class="sub dim">The questions could not be read — a failure, not an empty set.</p>
							{:else}
								<p class="sub dim">No questions yet. The board scores nothing until a question exists.</p>
								<button class="btn btn-sm" onclick={() => newQuestion()}>
									<Icon name="plus" size={14} />
									New question
								</button>
							{/if}
						</div>
					{:else if !loading && matched.length === 0}
						<p class="sub dim pad">No question matches “{query}”.</p>
					{/if}
				{/if}
			</div>
		</section>

		<section class="editor" aria-label="Question editor">
			{#if editable}
				<div class="editor-title">
					<h2 class="dsp">{creating ? 'New question' : (current?.title ?? 'Question')}</h2>
					{#if dirty}<Tag variant="wash">unsaved</Tag>{/if}
				</div>
			{/if}

			<div class="editor-body">
			{#if editable}
				<div class="editor-fields scroll">
					<div class="panel">
						<div class="panel-head"><h3 class="label">What to ask</h3></div>

						<label class="field">
							<span class="label">Title</span>
							<input class="input" bind:value={draft.title} oninput={() => (dirty = true)} placeholder="Hiring for process excellence" />
							<span class="sub dim">The name this question carries on the board. Left empty, it is taken from the question.</span>
						</label>

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
							<span class="sub dim">The rubric is what lets the classifier make the inference a senior rep would, instead of matching a word.</span>
						</label>
					</div>

					<div class="panel">
						<div class="panel-head"><h3 class="label">How it scores</h3></div>
						<div class="fields">
							<label class="field">
								<span class="label">Service line</span>
								<select class="select" bind:value={draft.serviceLine} onchange={() => (dirty = true)}>
									{#each serviceLines as line (line.id)}
										<option value={line.key}>{line.name}</option>
									{/each}
									{#if !serviceLines.some((line) => line.key === draft.serviceLine)}
										<option value={draft.serviceLine}>{draft.serviceLine} (not configured)</option>
									{/if}
								</select>
							</label>
							<label class="field">
								<span class="label">Weight</span>
								<select class="select" bind:value={draft.weightTier} onchange={() => (dirty = true)}>
									<option value="HIGH">High · +1.5</option>
									<option value="MEDIUM">Medium · +0.7</option>
									<option value="LOW">Low · +0.3</option>
								</select>
							</label>
							<label class="field">
								<span class="label">Polarity</span>
								<select class="select" bind:value={draft.polarity} onchange={() => (dirty = true)}>
									<option value="POSITIVE">Counts for them</option>
									<option value="NEGATIVE">Counts against them</option>
									<option value="DISQUALIFYING">Removes them from the queue</option>
								</select>
							</label>
							<label class="field">
								<span class="label">Theme</span>
								<input class="input" list="themes-present" bind:value={draft.theme} oninput={() => (dirty = true)} />
								<datalist id="themes-present">
									{#each themes as name (name)}
										<option value={name}></option>
									{/each}
								</datalist>
								<span class="sub dim">Rows sharing a theme are capped together, so a single loud topic cannot carry a company.</span>
							</label>
							<label class="field">
								<span class="label">Half-life</span>
								<select class="select" bind:value={draft.decayClass} onchange={() => (dirty = true)}>
									{#each decayClasses as decay (decay.name)}
										<option value={decay.name}>{tier(decay.name)} · {decay.halfLifeDays}d</option>
									{/each}
								</select>
								<span class="sub dim">
									{draftHalfLife
										? `Reading today it counts in full. At ${Math.round(draftHalfLife)} days old it counts half, and half again every ${Math.round(draftHalfLife)} after that.`
										: 'How long this evidence keeps its weight.'}
								</span>
							</label>
						</div>
						<label class="check">
							<input type="checkbox" bind:checked={draft.active} onchange={() => (dirty = true)} />
							<span>Active — the board scores this question</span>
						</label>
					</div>

					<div class="panel">
						<div class="panel-head">
							<h3 class="label">What it matches</h3>
							{#if (draft.cues ?? []).length === 0}<Tag variant="bare">derived from the wording</Tag>{/if}
						</div>

						<p class="sub dim match-note">A passage has to contain one of these to be read at all.</p>
						{#if (draft.cues ?? []).length > 0}
							<div class="chips">
								{#each draft.cues ?? [] as cue (cue)}
									<button class="chip" type="button" title="Remove this phrase" onclick={() => dropCue(cue)}>
										{cue}<Icon name="close" size={11} />
									</button>
								{/each}
							</div>
							<p class="sub dim">Set on the question. Empty the list to let the rubric derive its own.</p>
						{:else if derivedCues.length > 0}
							<div class="chips">
								{#each derivedCues as cue (cue)}<Tag variant="wash">{cue}</Tag>{/each}
							</div>
							<button class="btn btn-sm" type="button" onclick={keepDerived}>Use these as the phrase list</button>
						{:else}
							<p class="sub dim">No phrases yet. Write at least ten characters of question and rubric.</p>
						{/if}

						<div class="cue-add">
							<input
								class="input input-sm"
								placeholder="Add a phrase this question should match"
								bind:value={cueDraft}
								onkeydown={(event) => {
									if (event.key === 'Enter') {
										event.preventDefault();
										addCue();
									}
								}}
							/>
							<button class="btn btn-sm" type="button" onclick={addCue}>Add</button>
						</div>

						<p class="label against-label">And what argues against it</p>
						<p class="sub dim match-note">A passage containing one of these subtracts from the score instead of adding to it.</p>
						<div class="chips">
							{#each draft.counterCues ?? [] as cue (cue)}
								<button class="chip chip-bad" type="button" title="Remove" onclick={() => dropCounterCue(cue)}>
									{cue}<Icon name="close" size={11} />
								</button>
							{/each}
							{#if (draft.counterCues ?? []).length === 0}
								<span class="sub dim">None configured.</span>
							{/if}
						</div>
						<div class="cue-add">
							<input
								class="input input-sm"
								placeholder="A phrase that means this is not a buying signal"
								bind:value={counterDraft}
								onkeydown={(event) => {
									if (event.key === 'Enter') {
										event.preventDefault();
										addCounterCue();
									}
								}}
							/>
							<button class="btn btn-sm" type="button" onclick={addCounterCue}>Add</button>
						</div>
					</div>
				</div>

				<aside class="aside scroll" aria-label="Live preview">
					<div class="panel">
						<div class="panel-head">
							<h3 class="label">What this would find</h3>
							{#if previewing}<span class="sub dim"><span class="spinner"></span> Reading</span>{/if}
						</div>

						<div class="preview-body">
							<LoadingVeil show={veil} label="Reading the corpus" />

						{#if !preview}
							<p class="sub dim">
								The corpus is read as you type, on the local engine, so this costs nothing and answers the same
								way twice. Applying runs the engine you have configured.
							</p>
						{:else}
							<div class="metrics">
								<Metric value={preview.matchedCompanies} label="Companies" />
								<Metric value={shortNumber(preview.matchedEvidence)} label="Documents" />
								<Metric value={duration(preview.tookMs)} label="Scan" />
							</div>

							{#if preview.matches.length > 0}
								<table class="tbl">
									<thead>
										<tr>
											<th scope="col" class="c-name">Company</th>
											<th scope="col" class="c c-docs">Docs</th>
											<th scope="col" class="c c-conf">Conf</th>
										</tr>
									</thead>
									<tbody>
										{#each preview.matches.slice(0, 12) as match (match.companyId)}
											<tr>
												<td class="c-name"><a href="/?co={match.companyId}">{match.company}</a></td>
												<td class="num c c-docs">{match.evidence}</td>
												<td class="num c c-conf">{match.topConfidence.toFixed(2)}</td>
											</tr>
											<tr class="quote-row">
												<td colspan="3" class="small-quote">“{match.quote}”</td>
											</tr>
										{/each}
									</tbody>
								</table>
								{#if preview.matches.length > 12}
									<p class="sub dim">12 strongest of {preview.matchedCompanies}.</p>
								{/if}
							{:else}
								<p class="sub dim">
									Nothing in the corpus answers this. That is a result: the question would not move the board.
								</p>
							{/if}
						{/if}
						</div>
					</div>
				</aside>
			{:else}
				<div class="editor-fields scroll">
					<div class="panel">
						<div class="panel-head">
							<h2 class="dsp">No question open</h2>
						</div>
						{#if loadError}
							<p class="sub dim">The questions could not be read, so there is nothing to edit.</p>
						{:else}
							<p class="sub dim">
								Choose a question in the list, or start a new one. Until a question exists, the board
								has nothing to score.
							</p>
							<button class="btn btn-sm" onclick={() => newQuestion()}>
								<Icon name="plus" size={14} />
								New question
							</button>
						{/if}
					</div>
				</div>
			{/if}
			</div>

			{#if pending}
				<div class="pending">
					<Icon name="alert" size={14} />
					<span>{pendingNote}</span>
					<span class="spacer"></span>
					<button class="btn btn-sm btn-quiet" onclick={keepEditing} disabled={saving}>Keep editing</button>
					<button class="btn btn-sm" onclick={() => resolvePending('discard')} disabled={saving}>
						Discard and continue
					</button>
					<button class="btn btn-sm btn-ink" onclick={() => resolvePending('save')} disabled={saving}>
						Save and continue
					</button>
				</div>
			{/if}

			<footer class="editor-foot">
				{#if editable}
					<button class="btn btn-ink" onclick={save_} disabled={saving}>
						{#if saving}<span class="spinner"></span>{/if}
						{creating ? 'Create question' : 'Save'}
					</button>
					<button
						class="btn"
						title={dirty || creating ? 'Save first — the board scores the question as it is stored, not as it is typed.' : 'Read every document again and re-rank on this question'}
						onclick={apply}
						disabled={applying || !draft.serviceLine || dirty || creating}
					>
						{#if applying}<span class="spinner"></span>{/if}
						{applying ? 'Scoring' : 'Apply to the board'}
					</button>
					{#if dirty && !creating}
						<span class="sub dim">The board still holds the saved version of this question.</span>
					{/if}
				{:else}
					<button class="btn btn-ink" onclick={() => newQuestion()}>
						<Icon name="plus" size={15} />
						New question
					</button>
				{/if}
				<span class="spacer"></span>
				{#if current}<span class="sub dim num" title="Question key">{current.key}</span>{/if}
			</footer>
		</section>
	</div>
</div>

<style>
	.chip {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px var(--spacing-2);
		border: 1px solid var(--color-rule);
		border-radius: 2px;
		background: var(--color-surface);
		font: inherit;
		font-size: var(--text-sm);
		cursor: pointer;
	}

	.chip:hover {
		border-color: var(--color-ink-3);
	}

	.chip-bad {
		border-color: var(--color-oxblood-line);
		background: var(--color-oxblood-wash);
		color: var(--color-oxblood);
	}

	.cue-add {
		display: flex;
		gap: var(--spacing-2);
		margin-top: var(--spacing-2);
	}

	.cue-add .input {
		flex: 1;
	}

	.panes {
		flex: 1;
		min-height: 0;
		grid-template-columns: 264px minmax(0, 1fr);
	}

	.questions {
		display: flex;
		flex-direction: column;
		min-height: 0;
		border-right: 1px solid var(--color-rule);
		background: var(--color-paper);
	}

	.q-toolbar {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
		padding: var(--spacing-2);
		border-bottom: 1px solid var(--color-rule);
		flex: none;
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		flex: 1;
		min-width: 0;
		padding-left: var(--spacing-2);
		border: 1px solid var(--color-rule-strong);
		border-radius: 2px;
		background: var(--color-surface);
		color: var(--color-ink-3);
	}

	.search:focus-within {
		border-color: var(--color-orange);
	}

	.search .input {
		border: 0;
		background: transparent;
		padding-left: 0;
		box-shadow: none;
	}

	.q-list {
		position: relative;
		flex: 1;
		min-height: 0;
	}

	.group + .group {
		border-top: 1px solid var(--color-rule);
	}

	.group-head {
		display: block;
		padding: var(--spacing-2) var(--spacing-3) 4px;
	}

	.group ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.group li {
		display: grid;
		grid-template-columns: minmax(0, 1fr) auto;
		align-items: start;
		border-bottom: 1px solid var(--color-rule);
		background: var(--color-paper);
		padding-right: var(--spacing-1);
	}

	.group li:hover {
		background: var(--color-surface);
	}

	.q {
		display: flex;
		flex-direction: column;
		gap: 3px;
		width: 100%;
		text-align: left;
		padding: var(--spacing-2) var(--spacing-2) var(--spacing-2) 11px;
		border-left: 2px solid transparent;
		transition: border-color var(--ease);
	}

	.group li:has(.q[aria-current='true']) {
		background: var(--color-surface);
	}

	.q[aria-current='true'] {
		border-left-color: var(--color-orange);
	}

	.q-title {
		font-size: var(--text-md);
		font-weight: 500;
		color: var(--color-ink);
		line-height: 1.3;
	}

	.q-text {
		font-size: var(--text-sm);
		line-height: 1.4;
		color: var(--color-ink-3);
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.q-meta {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 4px;
		margin-top: 2px;
	}

	.q-meta .num,
	.q-theme {
		font-size: var(--text-micro);
		color: var(--color-ink-3);
	}

	.pad {
		padding: var(--spacing-3);
	}

	.pad.empty {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: var(--spacing-2);
	}

	.pending {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--spacing-2);
		padding: var(--spacing-2) var(--spacing-4);
		border-top: 1px solid var(--color-rule-strong);
		background: var(--color-orange-wash);
		color: var(--color-orange-ink);
		font-size: var(--text-sm);
		flex: none;
	}

	.pending .spacer {
		margin-left: auto;
	}

	.editor {
		display: flex;
		flex-direction: column;
		min-height: 0;
		background: var(--color-surface);
	}

	.editor-title {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		flex-wrap: wrap;
		padding: var(--spacing-3) var(--spacing-4) 0;
		flex: none;
	}

	.editor-title h2 {
		font-size: var(--text-xl);
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.editor-body {
		position: relative;
		display: grid;
		grid-template-columns: minmax(0, 1fr) minmax(300px, 21rem);
		flex: 1;
		min-height: 0;
	}

	.editor-fields {
		display: flex;
		flex-direction: column;
		min-height: 0;
		min-width: 0;
		max-width: 760px;
	}

	.editor-fields .panel {
		border-bottom: 1px solid var(--color-rule);
	}

	.editor-fields .panel:last-child {
		border-bottom: 0;
	}

	.aside {
		min-height: 0;
		border-left: 1px solid var(--color-rule);
		background: var(--color-paper);
	}

	.aside .panel {
		padding: var(--spacing-3);
	}

	.aside .metrics {
		margin-bottom: var(--spacing-2);
	}

	.match-note {
		margin-bottom: var(--spacing-2);
	}

	.quote-row .small-quote {
		padding-bottom: var(--spacing-2);
		border-bottom: 1px solid var(--color-rule);
	}

	.against-label {
		margin-top: var(--spacing-4);
	}

	.editor-foot {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-2) var(--spacing-4);
		border-top: 1px solid var(--color-ink);
		background: var(--color-paper);
		flex: none;
	}

	.editor-foot .spacer {
		margin-left: auto;
	}

	.panel {
		padding: var(--spacing-4);
	}

	.preview-body {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
	}

	.panel-head {
		margin-bottom: var(--spacing-1);
	}

	.panel-head h2 {
		font-size: var(--text-xl);
	}

	.c-docs {
		width: 64px;
	}

	.c-conf {
		width: 82px;
	}

	.small-quote {
		font-family: var(--font-display);
		font-size: var(--text-base);
		font-style: italic;
		line-height: 1.4;
		color: var(--color-ink-2);
	}

	@media (max-width: 1280px) {
		.editor-body {
			display: block;
			overflow-y: auto;
		}
		.editor-fields,
		.aside {
			overflow: visible;
		}
		.aside {
			border-left: 0;
			border-top: 1px solid var(--color-rule);
		}
	}

	@media (max-width: 1000px) {
		.panes {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: minmax(0, 220px) minmax(0, 1fr);
		}
		.questions {
			border-right: 0;
			border-bottom: 1px solid var(--color-rule);
		}
	}</style>
