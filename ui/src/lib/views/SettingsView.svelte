<script lang="ts">
	import {
		createServiceLine,
		deleteServiceLine,
		fetchRegionCountries,
		fetchServiceLines,
		fetchSettings,
		saveSettings,
		updateServiceLine,
		type RegionCountry,
		type ServiceLine,
		type Settings
	} from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { shortNumber } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import type { MenuItem } from '$lib/components/menu';
	import type { IconName } from '$lib/components/icons';

	const SECTIONS: { id: string; label: string; hint: string; icon: IconName }[] = [
		{ id: 'voice', label: 'Voice and style', hint: 'How drafts read', icon: 'mail' },
		{ id: 'profile', label: 'Customer profile', hint: 'Who counts as a prospect', icon: 'target' },
		{ id: 'lines', label: 'Service lines', hint: 'What we sell', icon: 'layers' },
		{ id: 'scoring', label: 'Scoring', hint: 'Where the bands sit', icon: 'sliders' }
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
	let loading = $state(true);
	let saving = $state(false);
	let countries = $state('');
	let industries = $state('');
	let newLine = $state({ name: '', valueProposition: '' });
	let lineBusy = $state<string | null>(null);

	const bands = $derived(workspace.meta?.bands ?? {});
	const dirty = $derived(
		settings !== null &&
			draft !== null &&
			(countries !== settings.icpCountries.join(', ') ||
				industries !== settings.icpIndustries.join(', ') ||
				JSON.stringify({ ...draft, icpCountries: [], icpIndustries: [] }) !==
					JSON.stringify({ ...settings, icpCountries: [], icpIndustries: [] }))
	);

	$effect(() => {
		let cancelled = false;
		loading = true;
		Promise.all([fetchSettings(), fetchServiceLines(true), fetchRegionCountries()])
			.then(([config, lineList, countryRows]) => {
				if (cancelled) return;
				settings = config;
				draft = { ...config };
				countries = config.icpCountries.join(', ');
				industries = config.icpIndustries.join(', ');
				lines = lineList;
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
			const saved = await saveSettings({
				...draft,
				icpCountries: parse(countries),
				icpIndustries: parse(industries)
			});
			settings = saved;
			draft = { ...saved };
			countries = saved.icpCountries.join(', ');
			industries = saved.icpIndustries.join(', ');
			workspace.reload();
			toasts.push('Saved. Every service line re-scored.', 'success');
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
			workspace.reload();
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

	function lineMenu(line: ServiceLine): MenuItem[] {
		return [
			{ id: 'toggle', label: line.active ? 'Disable — hide it everywhere' : 'Enable', icon: line.active ? 'close' : 'check' },
			{
				id: 'delete',
				label: line.questionCount > 0 ? `Delete — blocked by ${line.questionCount} questions` : 'Delete',
				icon: 'trash',
				danger: true,
				disabled: line.questionCount > 0,
				separatorBefore: true
			}
		];
	}

	async function runLine(line: ServiceLine, id: string) {
		if (id === 'toggle') await toggleLine(line);
		else if (id === 'delete') await removeLine(line);
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
		<div class="ident">
			<h1 class="dsp">Settings</h1>
			<p class="sub num">
				{shortNumber(settings?.indexedCompanies ?? 0)} companies indexed · {lines.length} service lines · applies to
				all of them
			</p>
		</div>
		<div class="topbar-tools">
			{#if dirty}
				<button class="btn" onclick={discard} disabled={saving}>Discard</button>
				<button class="btn btn-primary" onclick={save} disabled={saving}>
					{#if saving}<span class="spinner"></span>{/if}
					{saving ? 'Saving' : 'Save and re-score'}
				</button>
			{:else}
				<span class="saved"><Icon name="check" size={14} /> Saved</span>
			{/if}
			<Menu
				items={[{ id: 'voice', label: 'Read the writer brief', icon: 'mail' }]}
				onselect={() => (section = 'voice')}
				label="More settings actions"
			/>
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
						<Icon name={option.icon} size={15} />
						<span class="section-text">
							<span class="section-label">{option.label}</span>
							<span class="section-hint">{option.hint}</span>
						</span>
					</button>
				{/each}
				{#if dirty}
					<p class="unsaved"><Icon name="alert" size={13} /> Unsaved changes</p>
				{/if}
			</nav>

			<div class="body scroll">
				{#if section === 'voice'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Voice and style</h2>
							<span class="sub dim">applies to every draft</span>
						</div>
						<div class="fields">
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
						<div class="fields">
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
							<span class="sub dim">Comma separated, applied to every draft.</span>
						</label>
						<label class="field">
							<span class="label">Anything else the writer should know</span>
							<textarea class="textarea" rows="3" bind:value={draft.voiceNotes}></textarea>
						</label>
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">The brief the writer receives</h3></div>
						<p class="brief">{voicePreview}</p>
						<p class="sub dim">
							{draft.modelWritesDrafts
								? 'A language model is configured, so drafts are written from the evidence under this brief.'
								: 'No model is configured, so drafts are composed from the ledger with this brief applied.'}
						</p>
					</section>
				{/if}

				{#if section === 'profile'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Where we sell</h2>
							<span class="sub dim">{draft.geographyGate ? 'gate on' : 'gate off'}</span>
						</div>
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
							<span class="sub dim">Empty means every market.</span>
						</label>
						<div class="chips">
							{#each parse(countries).slice(0, 24) as code (code)}<Tag>{code}</Tag>{/each}
							{#if parse(countries).length > 24}
								<Tag variant="bare">+{parse(countries).length - 24}</Tag>
							{/if}
						</div>
						<label class="check">
							<input type="checkbox" bind:checked={draft.geographyGate} />
							<span>Remove companies outside these markets — the reason is shown on the card</span>
						</label>
					</section>

					<section class="panel">
						<div class="panel-head"><h2 class="dsp">Who we sell to</h2></div>
						<div class="fields">
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
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">What that leaves</h3></div>
						<div class="metrics">
							<Metric value={shortNumber(settings?.indexedCompanies ?? 0)} label="Indexed" />
							<Metric value={shortNumber(settings?.inScope?.inGeography ?? 0)} label="In geography" />
							<Metric value={shortNumber(settings?.inScope?.aboveSizeFloor ?? 0)} label="Above the floor" />
							<Metric
								value={shortNumber(settings?.inScope?.competitors ?? 0)}
								label="Competitors"
								tone={(settings?.inScope?.competitors ?? 0) > 0 ? 'bad' : null}
							/>
						</div>
					</section>
				{/if}

				{#if section === 'lines'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">What we sell</h2>
							<span class="sub dim">{lines.length} configured</span>
						</div>
						<ul class="lines">
							{#each lines as line (line.id)}
								<li>
									<div class="line-head">
										<span class="line-name">{line.name}</span>
										<div class="line-tools">
											<Tag variant="bare">{line.questionCount} questions</Tag>
											<Tag variant={line.active ? 'accent' : 'bare'}>{line.active ? 'active' : 'off'}</Tag>
											{#if lineBusy === line.id}<span class="spinner"></span>{/if}
											<Menu
												items={lineMenu(line)}
												onselect={(id) => runLine(line, id)}
												size="sm"
												label={`Actions for ${line.name}`}
											/>
										</div>
									</div>
									<p class="sub">
										{line.valueProposition ?? 'No value proposition yet — the writer falls back to a generic one.'}
									</p>
								</li>
							{/each}
						</ul>
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">Add a service line</h3></div>
						<div class="fields">
							<label class="field">
								<span class="label">Name</span>
								<input class="input" bind:value={newLine.name} placeholder="Managed Detection and Response" />
							</label>
							<label class="field">
								<span class="label">Value proposition</span>
								<input class="input" bind:value={newLine.valueProposition} placeholder="we run the SOC so your team keeps the context" />
							</label>
						</div>
						<div>
							<button class="btn btn-ink" disabled={lineBusy === 'new'} onclick={addLine}>
								<Icon name="plus" size={15} /> Add service line
							</button>
						</div>
					</section>
				{/if}

				{#if section === 'scoring'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Where the bands sit</h2>
							<span class="sub dim num">cap {draft.themeCap.toFixed(3)} nats</span>
						</div>
						<div class="fields">
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
						<p class="sub dim">
							The base rate is the share of the pool worth calling. The theme cap follows the Priority line, so one
							kind of evidence reaches it and never passes it.
						</p>
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">The board right now</h3></div>
						<div class="metrics">
							<Metric value={bands.PRIORITY ?? 0} label="Priority" tone="hot" />
							<Metric value={bands.NURTURE ?? 0} label="Nurture" />
							<Metric value={bands.WATCH ?? 0} label="Watch" />
							<Metric value={bands.THIN ?? 0} label="Thin" />
							<Metric value={bands.DISQUALIFIED ?? 0} label="Rejected" />
						</div>
					</section>
				{/if}
			</div>
		</div>
	{/if}
</div>

<style>
	.panes {
		grid-template-columns: 232px minmax(0, 1fr);
	}

	.saved {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-1);
		font-size: var(--fs-base);
		color: var(--accent);
	}

	.sections {
		border-right: 1px solid var(--rule);
		background: var(--paper);
		padding: var(--sp-2) 0;
		display: flex;
		flex-direction: column;
	}

	.section-link {
		display: flex;
		align-items: flex-start;
		gap: var(--sp-2);
		text-align: left;
		padding: var(--sp-2) var(--sp-3) var(--sp-2) 10px;
		border-left: 2px solid transparent;
		transition: background var(--ease), border-color var(--ease);
	}

	.section-link :global(.icon) {
		color: var(--ink-3);
		margin-top: 1px;
	}

	.section-link:hover {
		background: var(--surface);
	}

	.section-link[aria-current='true'] {
		background: var(--surface);
		border-left-color: var(--orange);
	}

	.section-text {
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
	}

	.section-label {
		font-size: var(--fs-md);
		color: var(--ink-2);
	}

	.section-link[aria-current='true'] .section-label {
		color: var(--ink);
		font-weight: 500;
	}

	.section-hint {
		font-size: var(--fs-micro);
		color: var(--ink-4);
	}

	.unsaved {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		margin-top: auto;
		padding: var(--sp-3);
		font-size: var(--fs-sm);
		color: var(--orange-ink);
	}

	.body {
		min-height: 0;
		background: var(--surface);
	}

	.panel {
		max-width: 900px;
	}

	.panel-head h2 {
		font-size: var(--fs-xl);
	}

	.brief {
		font-size: var(--fs-base);
		line-height: 1.6;
		padding: var(--sp-2) var(--sp-3);
		background: var(--paper);
		border-left: 2px solid var(--orange);
		color: var(--ink-2);
	}

	.checks {
		display: flex;
		flex-direction: column;
		gap: var(--sp-1);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-1);
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

	.lines {
		list-style: none;
		margin: 0;
		padding: 0;
		border-top: 1px solid var(--rule);
	}

	.lines li {
		padding: var(--sp-2) 0;
		border-bottom: 1px solid var(--rule);
	}

	.line-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-3);
	}

	.line-name {
		font-family: var(--font-display);
		font-size: var(--fs-lg);
	}

	.line-tools {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
	}

	@media (max-width: 900px) {
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
		.section-hint,
		.unsaved {
			display: none;
		}
	}
</style>
