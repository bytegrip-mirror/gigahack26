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
	import Tag from '$lib/ui/Tag.svelte';
	import LoadingVeil from '$lib/ui/LoadingVeil.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Metrics from '$lib/ui/Metrics.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Toolbar from '$lib/ui/Toolbar.svelte';
	import Panel from '$lib/ui/Panel.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Fields from '$lib/ui/Fields.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';

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

	const serviceLine = $derived(session.serviceLine);
	const current = $derived(policies.find((policy) => policy.id === selectedId) ?? null);

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

	function select(policy: Policy) {
		selectedId = policy.id;
		creating = false;
		dirty = false;
		draft = {
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

	function startNew(line = serviceLine) {
		selectedId = null;
		creating = true;
		dirty = true;
		draft = { ...BLANK, serviceLine: line || '' };
	}

	function decayOf(name: string): number | null {
		return decayClasses.find((entry) => entry.name === name)?.halfLifeDays ?? null;
	}

	function weight(ageDays: number, halfLifeDays: number): number {
		return Math.max(0.02, Math.pow(2, -ageDays / halfLifeDays));
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

	async function save_() {
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

	async function duplicate(policy: Policy) {
		const { id, key, nats, halfLifeDays, createdAt, updatedAt, signalCount, ...rest } = policy;
		try {
			const created = await createPolicy({ ...rest, title: `${policy.title} (copy)` });
			policies = await fetchPolicies();
			select(created);
			toasts.push('Duplicated. Edit it and save.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	async function toggleActive(policy: Policy) {
		const { id, key, nats, halfLifeDays, createdAt, updatedAt, signalCount, ...rest } = policy;
		try {
			const updated = await updatePolicy(policy.id, { ...rest, active: !policy.active });
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
		if (id === 'open') select(policy);
		else if (id === 'duplicate') await duplicate(policy);
		else if (id === 'toggle') await toggleActive(policy);
		else if (id === 'key') {
			const ok = await copy(policy.key);
			toasts.push(ok ? 'Question key copied.' : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
		} else if (id === 'delete') {
			select(policy);
			await remove();
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

<View>
	<Topbar title="Signal Studio">
		{#snippet subtitle()}
			{#if loadError}
				The policy set could not be read — a failure, not an empty set
			{:else}
				{policies.length} questions · {scored} producing signals on {serviceLine}
			{/if}
		{/snippet}
		{#snippet tools()}
			<Button variant="primary" icon="plus" onclick={() => startNew()}>New question</Button>
			<Menu items={overflow} onselect={runOverflow} label="More studio actions" />
		{/snippet}
	</Topbar>

	<div class="flex min-h-0 flex-1 max-compact:flex-col">
		<section
			class="flex w-list min-w-0 flex-none flex-col border-r border-rule bg-paper max-compact:h-rail max-compact:w-full max-compact:max-w-none max-compact:border-r-0 max-compact:border-b"
			aria-label="Signal questions"
		>
			<Toolbar label="Filter questions">
				<Input
					size="sm"
					icon="search"
					bind:value={query}
					placeholder="Filter questions"
					aria-label="Filter questions"
					class="min-w-0 flex-1"
				/>
			</Toolbar>

			<div class="scroll relative min-h-0 flex-1">
				<LoadingVeil show={loading} label="Loading questions" />
				{#if loading && policies.length === 0}
					<p class="flex items-center gap-2 px-4 py-3 text-md text-ink-3"><Spinner /> Loading</p>
				{:else}
					{#each grouped as [line, items], index (line)}
						<div class={index > 0 ? 'border-t border-rule' : ''}>
							<p class="type-label px-3 pt-2 pb-1">{line} · {items.length}</p>
							<ul class="m-0 list-none p-0">
								{#each items as policy (policy.id)}
									<li class={['flex items-start justify-between border-b border-rule pr-1 hover:bg-surface', policy.id === selectedId ? 'bg-surface' : 'bg-paper']}>
										<button
											type="button"
											class={['flex min-w-0 flex-1 flex-col gap-1 border-l-2 py-2 pr-2 pl-3 text-left transition-colors', policy.id === selectedId ? 'border-orange' : 'border-transparent']}
											aria-current={policy.id === selectedId ? 'true' : undefined}
											onclick={() => select(policy)}
										>
											<span class="text-md leading-snug font-medium text-ink">{policy.title}</span>
											<span class="line-clamp-2 text-sm leading-normal text-ink-3">{policy.text}</span>
											<span class="mt-0.5 flex flex-wrap items-center gap-1">
												<Tag>{tier(policy.weightTier)}</Tag>
												{#if policy.polarity !== 'POSITIVE'}
													<Tag tone={policy.polarity === 'DISQUALIFYING' ? 'oxblood' : 'default'}>
														{policy.polarity === 'DISQUALIFYING' ? 'disqualifying' : 'counter'}
													</Tag>
												{/if}
												<Tag tone="bare">{theme(policy.theme)}</Tag>
												{#if !policy.active}<Tag tone="bare">off</Tag>{/if}
												<span class="num text-micro text-ink-4">{policy.signalCount} signals</span>
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
					{#if matched.length === 0 && !loading}
						<p class="p-3 text-sm leading-normal text-ink-4">No question matches "{query}".</p>
					{/if}
				{/if}
			</div>
		</section>

		<section class="flex min-w-0 flex-1 flex-col bg-surface" aria-label="Question editor">
			<div class="scroll relative min-h-0 flex-1">
				<Panel title={creating ? 'New question' : (current?.title ?? 'Question')}>
					{#snippet tools()}
						{#if dirty}<Tag tone="wash">unsaved</Tag>{/if}
					{/snippet}

					<Field label="Title" hint="The name this question carries on the board. Left empty, it is taken from the question." for="sq-title">
						<Input id="sq-title" bind:value={draft.title} oninput={() => (dirty = true)} placeholder="Hiring for process excellence" />
					</Field>

					<Field label="Question" for="sq-text">
						<Textarea id="sq-text" rows={3} bind:value={draft.text} oninput={() => (dirty = true)} placeholder="Is the company consolidating shared service centres into a captive operation?" />
					</Field>

					<Field label="Rubric — what counts, and what does not" hint="The rubric is what lets the classifier make the inference a senior rep would, instead of matching a word." for="sq-instructions">
						<Textarea id="sq-instructions" rows={5} bind:value={draft.instructions} oninput={() => (dirty = true)} placeholder="Counts: …  Does not count: …" />
					</Field>
				</Panel>

				<Panel title="Scoring">
					<Fields>
						<Field label="Service line" for="sq-line">
							<Select
								id="sq-line"
								label="Service line"
								bind:value={draft.serviceLine}
								onchange={() => (dirty = true)}
								options={[
									...serviceLines.map((line) => ({ value: line.key, label: line.name })),
									...(!serviceLines.some((line) => line.key === draft.serviceLine)
										? [{ value: draft.serviceLine, label: `${draft.serviceLine} (not configured)` }]
										: [])
								]}
							/>
						</Field>
						<Field label="Weight" for="sq-weight">
							<Select
								id="sq-weight"
								label="Weight"
								bind:value={draft.weightTier}
								onchange={() => (dirty = true)}
								options={[
									{ value: 'HIGH', label: 'High · 1.5 nats' },
									{ value: 'MEDIUM', label: 'Medium · 0.7' },
									{ value: 'LOW', label: 'Low · 0.3' }
								]}
							/>
						</Field>
						<Field label="Polarity" for="sq-polarity">
							<Select
								id="sq-polarity"
								label="Polarity"
								bind:value={draft.polarity}
								onchange={() => (dirty = true)}
								options={[
									{ value: 'POSITIVE', label: 'Positive' },
									{ value: 'NEGATIVE', label: 'Counter' },
									{ value: 'DISQUALIFYING', label: 'Disqualifying' }
								]}
							/>
						</Field>
						<Field label="Theme" for="sq-theme">
							<Input id="sq-theme" list="themes-present" bind:value={draft.theme} oninput={() => (dirty = true)} />
							<datalist id="themes-present">
								{#each themes as name (name)}
									<option value={name}></option>
								{/each}
							</datalist>
							<p class="type-sub">Rows sharing a theme are capped together, so a single loud topic cannot carry a company.</p>
						</Field>
						<Field label="Half-life" for="sq-decay">
							<Select
								id="sq-decay"
								label="Half-life"
								bind:value={draft.decayClass}
								onchange={() => (dirty = true)}
								options={decayClasses.map((decay) => ({ value: decay.name, label: `${tier(decay.name)} · ${decay.halfLifeDays}d` }))}
							/>
							<p class="type-sub">How long this evidence keeps its weight. A dated event is worth less next quarter; a structural shift is worth less next year.</p>
							{#if draftHalfLife}
								<div class="mt-2 flex flex-col gap-0.5">
									{#each [0, 30, 90, 180, 365] as age (age)}
										<span class="flex items-center gap-2">
											<span class="type-label w-bar">{age === 0 ? 'today' : `${age}d`}</span>
											<span class="block h-1 flex-1 overflow-hidden rounded-sm bg-sunken">
												<span class="block h-full bg-orange" style:width={`${Math.round(weight(age, draftHalfLife) * 100)}%`}></span>
											</span>
											<span class="num w-row text-right text-ink-4">{weight(age, draftHalfLife).toFixed(2)}</span>
										</span>
									{/each}
								</div>
							{/if}
						</Field>
					</Fields>
					<Checkbox bind:checked={draft.active} onchange={() => (dirty = true)}>
						Active — the board scores this question
					</Checkbox>
				</Panel>

				<Panel title="What this question will look for">
					{#snippet tools()}
						{#if (draft.cues ?? []).length === 0}<Tag tone="bare">derived</Tag>{/if}
					{/snippet}
					{#if (draft.cues ?? []).length > 0}
						<div class="flex flex-wrap gap-1">
							{#each draft.cues ?? [] as cue (cue)}
								<button type="button" class="inline-flex cursor-pointer items-center gap-1 rounded-sm border border-rule bg-surface px-2 py-0.5 text-sm hover:border-ink-3" title="Remove this cue" onclick={() => dropCue(cue)}>
									{cue}<Icon name="close" size="sm" />
								</button>
							{/each}
						</div>
						<div class="flex gap-2">
							<Input
								size="sm"
								class="min-w-0 flex-1"
								placeholder="Add a phrase this question should match"
								bind:value={cueDraft}
								onkeydown={(event) => {
									if (event.key === 'Enter') {
										event.preventDefault();
										addCue();
									}
								}}
							/>
							<Button size="sm" onclick={addCue}>Add</Button>
						</div>
						<p class="type-sub">Set on the question. Empty the list to let the rubric derive its own.</p>
					{:else if derivedCues.length > 0}
						<div class="flex flex-wrap gap-1">
							{#each derivedCues as cue (cue)}<Tag tone="wash">{cue}</Tag>{/each}
						</div>
						<p class="type-sub">
							Derived from the wording — nothing is configured, so these are the phrases the engine will match.
							Keep them to edit the list, or leave it empty and they are re-derived from the rubric.
						</p>
						<Button size="sm" onclick={keepDerived}>Use these as the cue list</Button>
					{:else}
						<p class="type-sub">No cue vocabulary yet. Write at least ten characters of question and rubric.</p>
					{/if}
				</Panel>

				<Panel title="What argues against it">
					<div class="flex flex-wrap items-center gap-1">
						{#each draft.counterCues ?? [] as cue (cue)}
							<button type="button" class="inline-flex cursor-pointer items-center gap-1 rounded-sm border border-oxblood-line bg-oxblood-wash px-2 py-0.5 text-sm text-oxblood hover:border-ink-3" title="Remove" onclick={() => dropCounterCue(cue)}>
								{cue}<Icon name="close" size="sm" />
							</button>
						{/each}
						{#if (draft.counterCues ?? []).length === 0}
							<span class="type-sub">
								None. A passage containing one of these subtracts from the score instead of adding to it.
							</span>
						{/if}
					</div>
					<div class="flex gap-2">
						<Input
							size="sm"
							class="min-w-0 flex-1"
							placeholder="A phrase that means this is not a buying signal"
							bind:value={counterDraft}
							onkeydown={(event) => {
								if (event.key === 'Enter') {
									event.preventDefault();
									addCounterCue();
								}
							}}
						/>
						<Button size="sm" onclick={addCounterCue}>Add</Button>
					</div>
				</Panel>

				<Panel title="Live preview">
					{#snippet tools()}
						{#if previewing}<span class="inline-flex items-center gap-1 text-sm leading-normal text-ink-4"><Spinner /> Loading</span>{/if}
					{/snippet}

					<div class="relative flex flex-col gap-2">
						<LoadingVeil show={veil} label="Loading the preview" />

						{#if !preview}
							<p class="type-sub">The corpus is scanned as you type on the local rubric engine, so it is instant and deterministic. Applying runs the configured engine.</p>
						{:else}
							<Metrics>
								<Metric value={preview.matchedCompanies} label="Companies" />
								<Metric value={shortNumber(preview.matchedEvidence)} label="Documents" hint={`of ${shortNumber(preview.scannedEvidence)}`} />
								<Metric value={duration(preview.tookMs)} label="Scan" />
							</Metrics>

							{#if preview.matches.length > 0}
								<table class="table-grid">
									<thead>
										<tr>
											<th scope="col">Company</th>
											<th scope="col" class="w-topbar text-right">Docs</th>
											<th scope="col" class="w-topbar text-right">Top conf</th>
											<th scope="col">Passage</th>
										</tr>
									</thead>
									<tbody>
										{#each preview.matches.slice(0, 10) as match (match.companyId)}
											<tr>
												<td><a href="/?co={match.companyId}">{match.company}</a></td>
												<td class="num text-right">{match.evidence}</td>
												<td class="num text-right">{match.topConfidence.toFixed(2)}</td>
												<td class="font-display text-base leading-normal text-ink-2 italic">“{match.quote}”</td>
											</tr>
										{/each}
									</tbody>
								</table>
								{#if preview.matches.length > 10}
									<p class="type-sub">10 strongest of {preview.matchedCompanies}.</p>
								{/if}
							{:else}
								<p class="type-sub">Nothing in the corpus answers this. That is a result: the question would not move the board.</p>
							{/if}
						{/if}
					</div>
				</Panel>
			</div>

			<footer class="flex flex-none items-center gap-2 border-t border-ink bg-paper px-4 py-2">
				<Button variant="ink" onclick={save_} disabled={saving}>
					{#if saving}<Spinner />{/if}
					{creating ? 'Create question' : 'Save'}
				</Button>
				<Button onclick={apply} disabled={applying || !draft.serviceLine}>
					{#if applying}<Spinner />{/if}
					{applying ? 'Scoring' : 'Apply to the board'}
				</Button>
				<span class="ml-auto"></span>
				{#if current}<span class="text-sm leading-normal text-ink-4 num" title="Question key">{current.key}</span>{/if}
			</footer>
		</section>
	</div>
</View>
