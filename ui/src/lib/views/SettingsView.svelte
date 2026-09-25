<script lang="ts">
	import {
		createServiceLine,
		deleteServiceLine,
		fetchMeta,
		fetchRegionCountries,
		fetchServiceLines,
		fetchSettings,
		saveSettings,
		updateServiceLine,
		type RegionCountry,
		type ServiceLine,
		type Settings
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { shortNumber } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';

	const SECTIONS = [
		{ id: 'voice', label: 'Voice and style', hint: 'How drafts read' },
		{ id: 'profile', label: 'Customer profile', hint: 'Who counts as a prospect' },
		{ id: 'lines', label: 'Service lines', hint: 'What we sell' },
		{ id: 'scoring', label: 'Scoring', hint: 'Where the bands sit' }
	];

	const TONES = ['direct', 'consultative', 'warm', 'matter-of-fact'];
	const FORMALITY = ['casual', 'neutral', 'formal'];
	const LENGTHS = ['short', 'medium', 'long'];
	const LANGUAGES = ['en', 'ro', 'de', 'fr', 'it', 'es'];

	let section = $state('voice');
	let settings = $state<Settings | null>(null);
	let draft = $state<Settings | null>(null);
	let lines = $state<ServiceLine[]>([]);
	let regionCountries = $state<RegionCountry[]>([]);
	let bands = $state<Record<string, number>>({});
	let loading = $state(true);
	let saving = $state(false);
	let countries = $state('');
	let industries = $state('');
	let newLine = $state({ name: '', valueProposition: '' });
	let lineBusy = $state<string | null>(null);

	const serviceLine = $derived(session.serviceLine);
	const dirty = $derived(
		settings !== null &&
			draft !== null &&
			(countries !== settings.icpCountries.join(', ') ||
				industries !== settings.icpIndustries.join(', ') ||
				JSON.stringify({ ...draft, icpCountries: [], icpIndustries: [] }) !==
					JSON.stringify({ ...settings, icpCountries: [], icpIndustries: [] }))
	);

	$effect(() => {
		if (!serviceLine) return;
		let cancelled = false;
		loading = true;
		Promise.all([fetchSettings(), fetchServiceLines(true), fetchMeta(serviceLine), fetchRegionCountries()])
			.then(([config, lineList, meta, countryRows]) => {
				if (cancelled) return;
				settings = config;
				draft = { ...config };
				countries = config.icpCountries.join(', ');
				industries = config.icpIndustries.join(', ');
				lines = lineList;
				bands = meta.bands;
				regionCountries = countryRows;
			})
			.catch((cause) => toasts.push((cause as Error).message, 'error'))
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	function regionsOf(all: RegionCountry[]): { key: string; name: string; codes: string[] }[] {
		const grouped = new Map<string, { name: string; codes: string[] }>();
		for (const entry of all) {
			const group = grouped.get(entry.continentKey) ?? { name: entry.continentKey, codes: [] };
			group.codes.push(entry.code);
			grouped.set(entry.continentKey, group);
		}
		return [...grouped.entries()].map(([key, group]) => ({ key, name: label(key), codes: group.codes }));
	}

	function label(key: string): string {
		return key.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
	}

	function addRegion(codes: string[]) {
		const merged = new Set([...parse(countries), ...codes]);
		countries = [...merged].join(', ');
	}

	function parse(raw: string): string[] {
		return raw
			.split(',')
			.map((entry) => entry.trim().toUpperCase())
			.filter(Boolean);
	}

	async function save() {
		if (!draft) return;
		saving = true;
		try {
			const saved = await saveSettings(
				{ ...draft, icpCountries: parse(countries), icpIndustries: parse(industries) },
				serviceLine
			);
			settings = saved;
			draft = { ...saved };
			countries = saved.icpCountries.join(', ');
			industries = saved.icpIndustries.join(', ');
			bands = (await fetchMeta(serviceLine)).bands;
			toasts.push('Saved.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			saving = false;
		}
	}

	function discard() {
		if (!settings) return;
		draft = { ...settings };
		countries = settings.icpCountries.join(', ');
		industries = settings.icpIndustries.join(', ');
	}

	async function addLine() {
		if (newLine.name.trim().length < 2) {
			toasts.push('Give the service line a name.', 'warn');
			return;
		}
		lineBusy = 'new';
		try {
			await createServiceLine({
				name: newLine.name.trim(),
				description: null,
				valueProposition: newLine.valueProposition.trim() || null,
				sortOrder: lines.length,
				active: true
			});
			lines = await fetchServiceLines(true);
			newLine = { name: '', valueProposition: '' };
			toasts.push('Service line added.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			lineBusy = null;
		}
	}

	async function toggleLine(line: ServiceLine) {
		lineBusy = line.id;
		try {
			await updateServiceLine(line.id, {
				name: line.name,
				description: line.description,
				valueProposition: line.valueProposition,
				sortOrder: line.sortOrder,
				active: !line.active
			});
			lines = await fetchServiceLines(true);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			lineBusy = null;
		}
	}

	async function removeLine(line: ServiceLine) {
		lineBusy = line.id;
		try {
			await deleteServiceLine(line.id);
			lines = await fetchServiceLines(true);
			toasts.push(`${line.name} removed.`, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			lineBusy = null;
		}
	}

	const voicePreview = $derived.by(() => {
		if (!draft) return '';
		const parts = [
			`Tone: ${draft.voiceTone}.`,
			`Register: ${draft.voiceFormality}.`,
			`Length: ${draft.voiceLength}.`,
			`Write in ${draft.voiceLanguage}.`,
			`Address the reader as "${draft.voiceReader}".`
		];
		const avoid = parse(draft.voiceAvoid.toLowerCase());
		if (avoid.length > 0) parts.push(`Never use: ${avoid.join(', ')}.`);
		if (draft.voiceNotes.trim()) parts.push(draft.voiceNotes.trim());
		return parts.join(' ');
	});
</script>

<div class="view">
	<header class="topbar">
		<div>
			<h1 class="dsp">Settings</h1>
			<p class="label">Everything the board and the writer read, in one place.</p>
		</div>
		<div class="row">
			{#if dirty}<button class="btn" onclick={discard}>Discard</button>{/if}
			<button class="btn btn-primary" onclick={save} disabled={saving || !dirty}>
				{#if saving}<span class="spinner"></span>{/if}
				{dirty ? 'Save and re-score' : 'Saved'}
			</button>
		</div>
	</header>

	{#if loading && !draft}
		<p class="note"><span class="spinner"></span> Loading settings</p>
	{:else if draft}
		<div class="panes">
			<LoadingVeil show={saving} label="Saving and re-scoring" />
			<nav class="sections" aria-label="Settings sections">
				{#each SECTIONS as option (option.id)}
					<button
						class="section-link"
						aria-current={section === option.id ? 'true' : undefined}
						onclick={() => (section = option.id)}
					>
						<span class="section-label">{option.label}</span>
						<span class="section-hint">{option.hint}</span>
					</button>
				{/each}
			</nav>

			<div class="body scroll">
				{#if section === 'voice'}
					<section class="block">
						<h2 class="dsp">Voice and style</h2>
						<p class="sub">
							This is the brief the writer works from. It applies to every draft, and it is the reason two
							companies with the same score read differently.
						</p>
						<div class="pair">
							<label class="field">
								<span class="label">Tone</span>
								<select class="select" bind:value={draft.voiceTone}>
									{#each TONES as option (option)}<option value={option}>{option}</option>{/each}
								</select>
							</label>
							<label class="field">
								<span class="label">Register</span>
								<select class="select" bind:value={draft.voiceFormality}>
									{#each FORMALITY as option (option)}<option value={option}>{option}</option>{/each}
								</select>
							</label>
							<label class="field">
								<span class="label">Length</span>
								<select class="select" bind:value={draft.voiceLength}>
									{#each LENGTHS as option (option)}<option value={option}>{option}</option>{/each}
								</select>
							</label>
							<label class="field">
								<span class="label">Language</span>
								<select class="select" bind:value={draft.voiceLanguage}>
									{#each LANGUAGES as option (option)}<option value={option}>{option}</option>{/each}
								</select>
							</label>
						</div>
						<label class="field">
							<span class="label">The ask</span>
							<input class="input" bind:value={draft.voiceAsk} />
						</label>
						<div class="pair">
							<label class="field">
								<span class="label">Sign-off</span>
								<input class="input" bind:value={draft.voiceSignOff} />
							</label>
							<label class="field">
								<span class="label">Signature</span>
								<input class="input" bind:value={draft.voiceSignature} placeholder="Name, role, company" />
							</label>
						</div>
						<label class="field">
							<span class="label">Never use these words</span>
							<input class="input" bind:value={draft.voiceAvoid} placeholder="leverage, synergy, circle back" />
							<span class="sub dim">Comma separated. Applied to every draft, including generated ones.</span>
						</label>
						<label class="field">
							<span class="label">Anything else the writer should know</span>
							<textarea class="textarea" rows="3" bind:value={draft.voiceNotes}></textarea>
						</label>
					</section>

					<section class="block">
						<h3 class="label">The brief the writer receives</h3>
						<p class="brief num">{voicePreview}</p>
						<p class="sub dim">
							{draft.modelWritesDrafts
								? 'A language model is configured, so drafts are written from the evidence under this brief.'
								: 'No model is configured, so drafts are composed from the ledger with this brief applied.'}
						</p>
					</section>
				{/if}

				{#if section === 'profile'}
					<section class="block">
						<h2 class="dsp">Where we sell</h2>
						<p class="sub">
							A company outside these markets leaves the queue with the reason shown, whatever its score.
							Empty means every market.
						</p>
						<div class="field">
							<span class="label">Add a whole region</span>
							<div class="chips">
								{#each regionsOf(regionCountries) as group (group.key)}
									<button class="tag region-pick" onclick={() => addRegion(group.codes)}>
										+ {group.name}
									</button>
								{/each}
							</div>
						</div>

						<label class="field">
							<span class="label">Countries</span>
							<textarea class="textarea" rows="2" bind:value={countries}></textarea>
						</label>
						<div class="chips">
							{#each parse(countries).slice(0, 24) as code (code)}<Tag>{code}</Tag>{/each}
							{#if parse(countries).length > 24}
								<Tag variant="bare">+{parse(countries).length - 24}</Tag>
							{/if}
						</div>
						<label class="check">
							<input type="checkbox" bind:checked={draft.geographyGate} />
							<span>Remove companies outside these markets</span>
						</label>
					</section>

					<section class="block">
						<h2 class="dsp">Who we sell to</h2>
						<div class="pair">
							<label class="field">
								<span class="label">Minimum employees</span>
								<input class="input" type="number" min="0" bind:value={draft.icpMinEmployees} />
							</label>
							<label class="field">
								<span class="label">Industries (optional)</span>
								<input class="input" bind:value={industries} />
							</label>
						</div>
						<div class="checks">
							<label class="check">
								<input type="checkbox" bind:checked={draft.sizeGate} />
								<span>Remove companies below the size floor</span>
							</label>
							<label class="check">
								<input type="checkbox" bind:checked={draft.competitorGate} />
								<span>Remove competitors — firms that sell what we sell</span>
							</label>
						</div>
						<dl class="facts">
							<dt>Companies indexed</dt><dd class="num">{shortNumber(settings?.indexedCompanies ?? 0)}</dd>
							<dt>Inside the geography</dt><dd class="num">{shortNumber(settings?.inScope?.inGeography ?? 0)}</dd>
							<dt>Above the size floor, or unknown</dt>
							<dd class="num">{shortNumber(settings?.inScope?.aboveSizeFloor ?? 0)}</dd>
							<dt>Flagged as competitors</dt><dd class="num">{shortNumber(settings?.inScope?.competitors ?? 0)}</dd>
						</dl>
					</section>
				{/if}

				{#if section === 'lines'}
					<section class="block">
						<h2 class="dsp">What we sell</h2>
						<p class="sub">
							A service line scopes the board, the questions and the leads. The value proposition is what the
							writer says we do about it.
						</p>
						<ul class="lines">
							{#each lines as line (line.id)}
								<li>
									<div class="line-head">
										<span class="line-name">{line.name}</span>
										<div class="line-tools">
											<Tag variant="bare">{line.questionCount} questions</Tag>
											<Tag variant={line.active ? 'accent' : 'bare'}>{line.active ? 'active' : 'off'}</Tag>
											<button class="btn btn-sm" disabled={lineBusy === line.id} onclick={() => toggleLine(line)}>
												{line.active ? 'Disable' : 'Enable'}
											</button>
											<button
												class="btn btn-sm danger"
												disabled={lineBusy === line.id || line.questionCount > 0}
												title={line.questionCount > 0 ? 'Move or delete its questions first' : 'Delete'}
												onclick={() => removeLine(line)}
											>
												Delete
											</button>
										</div>
									</div>
									{#if line.valueProposition}
										<p class="sub">{line.valueProposition}</p>
									{:else}
										<p class="sub dim">No value proposition yet — the writer falls back to a generic one.</p>
									{/if}
								</li>
							{/each}
						</ul>
					</section>

					<section class="block">
						<h3 class="label">Add a service line</h3>
						<div class="pair">
							<label class="field">
								<span class="label">Name</span>
								<input class="input" bind:value={newLine.name} placeholder="Managed Detection and Response" />
							</label>
							<label class="field">
								<span class="label">Value proposition</span>
								<input
									class="input"
									bind:value={newLine.valueProposition}
									placeholder="we run the SOC so your team keeps the context"
								/>
							</label>
						</div>
						<button class="btn btn-ink" disabled={lineBusy === 'new'} onclick={addLine}>Add service line</button>
					</section>
				{/if}

				{#if section === 'scoring'}
					<section class="block">
						<h2 class="dsp">Where the bands sit</h2>
						<p class="sub">
							The base rate is the share of the pool worth calling. Bands are cut from the score the ledger
							produces. The theme cap follows the Priority line, so one kind of evidence reaches it and never
							passes it.
						</p>
						<div class="pair">
							<label class="field">
								<span class="label">Base rate</span>
								<input class="input" type="number" step="0.01" min="0.01" max="0.9" bind:value={draft.baseRate} />
							</label>
							<label class="field">
								<span class="label">Priority at</span>
								<input class="input" type="number" step="0.01" bind:value={draft.priorityThreshold} />
							</label>
							<label class="field">
								<span class="label">Nurture at</span>
								<input class="input" type="number" step="0.01" bind:value={draft.nurtureThreshold} />
							</label>
							<label class="field">
								<span class="label">Watch at</span>
								<input class="input" type="number" step="0.01" bind:value={draft.watchThreshold} />
							</label>
						</div>
						<p class="sub">
							Theme cap, derived: <span class="num">{draft.themeCap.toFixed(3)}</span> nats.
						</p>
						<dl class="facts">
							<dt>Priority</dt><dd class="num">{bands.PRIORITY ?? 0}</dd>
							<dt>Nurture</dt><dd class="num">{bands.NURTURE ?? 0}</dd>
							<dt>Watch</dt><dd class="num">{bands.WATCH ?? 0}</dd>
							<dt>Insufficient evidence</dt><dd class="num">{bands.THIN ?? 0}</dd>
							<dt>Rejected</dt><dd class="num">{bands.DISQUALIFIED ?? 0}</dd>
						</dl>
					</section>
				{/if}
			</div>
		</div>
	{/if}
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
		flex-wrap: wrap;
	}

	h1 {
		font-size: 23px;
		line-height: 1.1;
	}

	.topbar .label {
		margin-top: 3px;
	}

	.row {
		display: flex;
		gap: 6px;
	}

	.panes {
		position: relative;
		flex: 1;
		min-height: 0;
		display: grid;
		grid-template-columns: 226px minmax(0, 1fr);
	}

	.sections {
		border-right: 1px solid var(--rule);
		background: var(--paper);
		padding: 10px 0;
		display: flex;
		flex-direction: column;
	}

	.section-link {
		display: flex;
		flex-direction: column;
		gap: 1px;
		text-align: left;
		padding: 8px 14px 8px 12px;
		border-left: 2px solid transparent;
		transition: background var(--ease), border-color var(--ease);
	}

	.section-link:hover {
		background: var(--surface);
	}

	.section-link[aria-current='true'] {
		background: var(--surface);
		border-left-color: var(--orange);
	}

	.section-label {
		font-size: 13.5px;
		color: var(--ink-2);
	}

	.section-link[aria-current='true'] .section-label {
		color: var(--ink);
		font-weight: 500;
	}

	.section-hint {
		font-size: 11px;
		color: var(--ink-4);
	}

	.body {
		min-height: 0;
		overflow-y: auto;
		background: var(--surface);
	}

	.block {
		padding: 16px 20px 20px;
		border-bottom: 1px solid var(--rule);
		display: flex;
		flex-direction: column;
		gap: 11px;
		max-width: 940px;
	}

	.block:last-child {
		border-bottom: 0;
	}

	h2 {
		font-size: 19px;
		line-height: 1.2;
	}

	.sub {
		font-size: 12.5px;
		line-height: 1.55;
		color: var(--ink-2);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.pair {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
		gap: 12px;
	}

	.checks {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.check {
		display: flex;
		align-items: flex-start;
		gap: 7px;
		font-size: 12.5px;
		color: var(--ink-2);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.region-pick {
		cursor: pointer;
		border-color: var(--orange-line);
		background: var(--orange-wash);
		color: var(--orange-ink);
	}

	.region-pick:hover {
		background: var(--surface);
	}

	.facts {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 4px 10px;
		margin: 0;
		font-size: 12.5px;
		max-width: 420px;
	}

	.facts dt {
		color: var(--ink-3);
	}

	.facts dd {
		margin: 0;
		text-align: right;
	}

	.brief {
		font-size: 12.5px;
		line-height: 1.6;
		padding: 10px 12px;
		background: var(--paper);
		border-left: 2px solid var(--orange);
		color: var(--ink-2);
	}

	.lines {
		list-style: none;
		margin: 0;
		padding: 0;
		border-top: 1px solid var(--rule);
	}

	.lines li {
		padding: 10px 0;
		border-bottom: 1px solid var(--rule);
	}

	.line-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		flex-wrap: wrap;
	}

	.line-name {
		font-family: var(--font-display);
		font-size: 16px;
	}

	.line-tools {
		display: flex;
		align-items: center;
		gap: 6px;
	}

	.danger {
		color: var(--oxblood);
		border-color: var(--oxblood-line);
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 18px 16px;
		font-size: 13px;
		color: var(--ink-3);
	}

	@media (max-width: 860px) {
		.panes {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: auto minmax(0, 1fr);
		}
		.sections {
			flex-direction: row;
			border-right: 0;
			border-bottom: 1px solid var(--rule);
			overflow-x: auto;
			padding: 0;
		}
		.section-hint {
			display: none;
		}
	}
</style>
