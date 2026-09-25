<script lang="ts">
	import {
		createPolicy,
		deletePolicy,
		fetchPolicies,
		fetchServiceLines,
		previewPolicy,
		rescore,
		updatePolicy,
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
	let loading = $state(true);
	let selectedId = $state<string | null>(null);
	let draft = $state<PolicyInput>({ ...BLANK });
	let creating = $state(false);
	let dirty = $state(false);
	let saving = $state(false);
	let preview = $state<Preview | null>(null);
	let previewing = $state(false);
	let applying = $state(false);
	let query = $state('');
	let scope = $state<'line' | 'all'>('line');

	const serviceLine = $derived(session.serviceLine);
	const current = $derived(policies.find((policy) => policy.id === selectedId) ?? null);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		Promise.all([fetchPolicies(), fetchServiceLines(false)])
			.then(([result, lines]) => {
				if (cancelled) return;
				serviceLines = lines;
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
		const base = scope === 'line' ? policies.filter((p) => p.serviceLine === serviceLine) : policies;
		if (!needle) return base;
		return base.filter((policy) =>
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
		return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
	});

	const elsewhere = $derived(policies.filter((policy) => policy.serviceLine !== serviceLine).length);
	const scored = $derived(policies.filter((policy) => policy.active && policy.signalCount > 0).length);

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
		{ id: 'duplicate', label: 'Duplicate this question', icon: 'copy', disabled: !current },
		{ id: 'delete', label: 'Delete this question', icon: 'trash', danger: true, separatorBefore: true, disabled: creating || !selectedId }
	]);

	async function runOverflow(id: string) {
		if (id === 'export') exportPolicies();
		else if (id === 'duplicate' && current) await duplicate(current);
		else if (id === 'delete') await remove();
	}
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Signal Studio</h1>
			<p class="sub num">
				{policies.length} questions · {scored} producing signals on {serviceLine}
			</p>
		</div>

		<div class="topbar-tools">
			<button class="btn btn-primary" onclick={() => startNew()}>
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
				<div class="seg scope" role="tablist" aria-label="Scope">
					<button role="tab" aria-selected={scope === 'line'} title={serviceLine} onclick={() => (scope = 'line')}>
						{serviceLine}
					</button>
					<button role="tab" aria-selected={scope === 'all'} onclick={() => (scope = 'all')}>
						All lines
					</button>
				</div>
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
											onclick={() => select(policy)}
										>
											<span class="q-title">{policy.title}</span>
											<span class="q-text">{policy.text}</span>
											<span class="q-meta">
												<Tag>{tier(policy.weightTier)}</Tag>
												{#if policy.polarity !== 'POSITIVE'}
													<Tag variant={policy.polarity === 'DISQUALIFYING' ? 'oxblood' : 'default'}>
														{policy.polarity === 'DISQUALIFYING' ? 'disqualifying' : 'counter'}
													</Tag>
												{/if}
												<Tag variant="bare">{theme(policy.theme)}</Tag>
												{#if !policy.active}<Tag variant="bare">off</Tag>{/if}
												<span class="num dim">{policy.signalCount} signals</span>
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
					{#if scope === 'line' && elsewhere > 0}
						<p class="sub dim pad">{elsewhere} more on other service lines.</p>
					{/if}
					{#if matched.length === 0 && !loading}
						<p class="sub dim pad">No question matches “{query}”.</p>
					{/if}
				{/if}
			</div>
		</section>

		<section class="editor" aria-label="Question editor">
			<div class="editor-body scroll">
				<div class="panel">
					<div class="panel-head">
						<h2 class="dsp">{creating ? 'New question' : (current?.title ?? 'Question')}</h2>
						{#if dirty}<Tag variant="wash">unsaved</Tag>{/if}
					</div>

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
					<div class="panel-head"><h3 class="label">Scoring</h3></div>
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
								<option value="HIGH">High · 1.5 nats</option>
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
					<label class="check">
						<input type="checkbox" bind:checked={draft.active} onchange={() => (dirty = true)} />
						<span>Active — the board scores this question</span>
					</label>
				</div>

				<div class="panel">
					<div class="panel-head">
						<h3 class="label">Live preview</h3>
						{#if previewing}<span class="sub dim"><span class="spinner"></span> scanning</span>{/if}
					</div>

					{#if !preview}
						<p class="sub dim">The corpus is scanned as you type on the local rubric engine, so it is instant and deterministic. Applying runs the configured engine.</p>
					{:else}
						<div class="metrics">
							<Metric value={preview.matchedCompanies} label="Companies" />
							<Metric value={shortNumber(preview.matchedEvidence)} label="Documents" hint={`of ${shortNumber(preview.scannedEvidence)}`} />
							<Metric value={duration(preview.tookMs)} label="Scan" />
						</div>

						{#if preview.matches.length > 0}
							<table class="grid">
								<thead>
									<tr>
										<th scope="col" class="c-name">Company</th>
										<th scope="col" class="c c-docs">Docs</th>
										<th scope="col" class="c c-conf">Top conf</th>
										<th scope="col">Passage</th>
									</tr>
								</thead>
								<tbody>
									{#each preview.matches.slice(0, 10) as match (match.companyId)}
										<tr>
											<td class="c-name"><a href="/?co={match.companyId}">{match.company}</a></td>
											<td class="num c c-docs">{match.evidence}</td>
											<td class="num c c-conf">{match.topConfidence.toFixed(2)}</td>
											<td class="small-quote">“{match.quote}”</td>
										</tr>
									{/each}
								</tbody>
							</table>
							{#if preview.matches.length > 10}
								<p class="sub dim">10 strongest of {preview.matchedCompanies}.</p>
							{/if}
						{:else}
							<p class="sub dim">Nothing in the corpus answers this. That is a result: the question would not move the board.</p>
						{/if}
					{/if}
				</div>
			</div>

			<footer class="editor-foot">
				<button class="btn btn-ink" onclick={save_} disabled={saving}>
					{#if saving}<span class="spinner"></span>{/if}
					{creating ? 'Create question' : 'Save'}
				</button>
				<button class="btn" onclick={apply} disabled={applying || !draft.serviceLine}>
					{#if applying}<span class="spinner"></span>{/if}
					{applying ? 'Scoring' : 'Apply to the board'}
				</button>
				<span class="spacer"></span>
				{#if current}<span class="sub dim num" title="Question key">{current.key}</span>{/if}
			</footer>
		</section>
	</div>
</div>

<style>
	.panes {
		flex: 1;
		min-height: 0;
		grid-template-columns: 320px minmax(0, 1fr);
	}

	.questions {
		display: flex;
		flex-direction: column;
		min-height: 0;
		border-right: 1px solid var(--rule);
		background: var(--paper);
	}

	.q-toolbar {
		display: flex;
		flex-direction: column;
		gap: var(--sp-2);
		padding: var(--sp-2);
		border-bottom: 1px solid var(--rule);
		flex: none;
	}

	.scope {
		width: 100%;
	}

	.scope button {
		flex: 1;
		justify-content: center;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		display: block;
		font-size: var(--fs-sm);
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		flex: 1;
		min-width: 0;
		padding-left: var(--sp-2);
		border: 1px solid var(--rule-strong);
		border-radius: 2px;
		background: var(--surface);
		color: var(--ink-3);
	}

	.search:focus-within {
		border-color: var(--orange);
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
		border-top: 1px solid var(--rule);
	}

	.group-head {
		display: block;
		padding: var(--sp-2) var(--sp-3) 4px;
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
		border-bottom: 1px solid var(--rule);
		background: var(--paper);
		padding-right: var(--sp-1);
	}

	.group li:hover {
		background: var(--surface);
	}

	.q {
		display: flex;
		flex-direction: column;
		gap: 3px;
		width: 100%;
		text-align: left;
		padding: var(--sp-2) var(--sp-2) var(--sp-2) 11px;
		border-left: 2px solid transparent;
		transition: border-color var(--ease);
	}

	.group li:has(.q[aria-current='true']) {
		background: var(--surface);
	}

	.q[aria-current='true'] {
		border-left-color: var(--orange);
	}

	.q-title {
		font-size: var(--fs-md);
		font-weight: 500;
		color: var(--ink);
		line-height: 1.3;
	}

	.q-text {
		font-size: var(--fs-sm);
		line-height: 1.4;
		color: var(--ink-3);
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

	.q-meta .num {
		font-size: var(--fs-micro);
	}

	.pad {
		padding: var(--sp-3);
	}

	.editor {
		display: flex;
		flex-direction: column;
		min-height: 0;
		background: var(--surface);
	}

	.editor-body {
		position: relative;
		flex: 1;
		min-height: 0;
	}

	.editor-foot {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
		padding: var(--sp-2) var(--sp-4);
		border-top: 1px solid var(--ink);
		background: var(--paper);
		flex: none;
	}

	.editor-foot .spacer {
		margin-left: auto;
	}

	.panel {
		padding: var(--sp-4);
	}

	.panel-head {
		margin-bottom: var(--sp-1);
	}

	.panel-head h2 {
		font-size: var(--fs-xl);
	}

	.c-docs {
		width: 64px;
	}

	.c-conf {
		width: 82px;
	}

	.small-quote {
		font-family: var(--font-display);
		font-size: var(--fs-base);
		font-style: italic;
		line-height: 1.4;
		color: var(--ink-2);
	}

	@media (max-width: 1000px) {
		.panes {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: minmax(0, 220px) minmax(0, 1fr);
		}
		.questions {
			border-right: 0;
			border-bottom: 1px solid var(--rule);
		}
	}
</style>
