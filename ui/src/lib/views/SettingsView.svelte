<script lang="ts">
	import NotificationBell from '$lib/components/NotificationBell.svelte';
	import {
		createServiceLine,
		deleteServiceLine,
		fetchRegionCountries,
		fetchServiceLines,
		fetchPrecision,
		markCompetitor,
		fetchBoard,
		fetchSettings,
		fetchTelemetry,
		saveSettings,
		updateServiceLine,
		type RegionCountry,
		type ServiceLine,
		type Settings,
		type Telemetry
	} from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { date, shortNumber } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import type { MenuItem } from '$lib/components/menu';
	import type { IconName } from '$lib/components/icons';

	const SECTIONS: { id: string; label: string; hint: string; icon: IconName }[] = [
		{ id: 'voice', label: 'Voice and style', hint: 'How drafts read', icon: 'mail' },
		{ id: 'qualification', label: 'Qualification', hint: 'Who counts as a prospect', icon: 'target' },
		{ id: 'lines', label: 'Service lines', hint: 'The lines you sell', icon: 'layers' },
		{ id: 'scoring', label: 'Scoring', hint: 'Where the bands sit', icon: 'sliders' },
		{ id: 'reading', label: 'Reading', hint: 'What has been read, and when', icon: 'clock' }
	];

	const TONES = ['direct', 'consultative', 'warm', 'matter-of-fact'];
	const FORMALITY = ['casual', 'neutral', 'formal'];
	const LENGTHS = ['short', 'medium', 'long'];
	const LANGUAGES = ['en', 'ro', 'de', 'fr', 'it', 'es'];

	let section = $state('voice');
	let telemetry = $state<Telemetry | null>(null);
	let loadError = $state<string | null>(null);
	let precision = $state<Awaited<ReturnType<typeof fetchPrecision>> | null>(null);
	let curve = $state<number[]>([]);
	let perThousand = $state(0);
	let settings = $state<Settings | null>(null);
	let draft = $state<Settings | null>(null);
	let lines = $state<ServiceLine[]>([]);
	let regionCountries = $state<RegionCountry[]>([]);
	let loading = $state(true);
	let saving = $state(false);
	let countries = $state('');
	let industries = $state('');
	let blacklist = $state<string[]>([]);
	const exclusions = $derived(settings?.exclusions ?? []);
	/*
	 * There are ninety-two of these — every consultancy the corpus knows sells what
	 * we sell — and rendering all of them is a wall of chips, not a list anyone
	 * reads. The first screenful is enough to see what the gate is doing, and the
	 * rest is one click away.
	 */
	/**
	 * The notes box grows to fit what is in it.
	 *
	 * `rows="3"` is a fixed height, and notes longer than three lines were cut
	 * through the middle of a line at the box's bottom edge — an editable box that
	 * hides its own content reads as broken, and the fix is not a bigger number
	 * because the next person writes more. `rows` stays as the floor so an empty
	 * box is still a box.
	 */
	function grow(node: HTMLTextAreaElement, _notes: string) {
		const floor = node.scrollHeight;
		const fit = () => {
			node.style.height = 'auto';
			node.style.height = `${Math.max(floor, node.scrollHeight)}px`;
		};
		fit();
		node.addEventListener('input', fit);
		return {
			// The notes are the action's argument, so this runs again whenever they
			// are assigned — they arrive with the settings fetch, which is a change
			// no keystroke announced.
			update: () => fit(),
			destroy: () => node.removeEventListener('input', fit)
		};
	}

	const EXCLUSIONS_SHOWN = 12;
	let allExclusions = $state(false);
	const shownExclusions = $derived(allExclusions ? exclusions : exclusions.slice(0, EXCLUSIONS_SHOWN));
	let blacklistDraft = $state('');
	let rowBusy = $state<string | null>(null);
	let newLine = $state({ name: '', valueProposition: '' });
	let lineBusy = $state<string | null>(null);

	const bands = $derived(workspace.meta?.bands ?? {});
	const dirty = $derived(
		settings !== null &&
			draft !== null &&
			(blacklist.join('|') !== (settings.blacklist ?? []).join('|') ||
				countries !== settings.icpCountries.join(', ') ||
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
				fetchTelemetry()
					.then((result) => (telemetry = result))
					.catch(() => (telemetry = null));
				fetchPrecision()
					.then((result) => (precision = result))
					.catch(() => (precision = null));
				fetchBoard({ serviceLine: workspace.meta?.serviceLine ?? 'Intelligent Automation' })
					.then((rows) => (curve = rows.filter((row) => row.band !== 'DISQUALIFIED').map((row) => row.probability)))
					.catch(() => (curve = []));
				draft = { ...config };
				countries = config.icpCountries.join(', ');
				industries = config.icpIndustries.join(', ');
				lines = lineList;
				regionCountries = countryRows;
			})
			.catch((cause) => {
				loadError = (cause as Error).message;
				toasts.push((cause as Error).message, 'error');
			})
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

	async function bringBack(company: { id: string; name: string }) {
		rowBusy = company.id;
		try {
			await markCompetitor(company.id, false);
			await refreshSettings();
			workspace.reload();
			toasts.push(`${company.name} is callable again.`, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			rowBusy = null;
		}
	}

	async function refreshSettings() {
		const fresh = await fetchSettings();
		settings = fresh;
		draft = { ...fresh };
		blacklist = [...(fresh.blacklist ?? [])];
	}

	function addBlacklist() {
		const entry = blacklistDraft.trim();
		if (entry.length < 2) return;
		if (!blacklist.some((held) => held.toLowerCase() === entry.toLowerCase())) {
			blacklist = [...blacklist, entry];
		}
		blacklistDraft = '';
	}

	function dropBlacklist(entry: string) {
		blacklist = blacklist.filter((held) => held !== entry);
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

	function simulate(curve: number[], thresholds: Settings) {
		const out = { PRIORITY: 0, NURTURE: 0, WATCH: 0, THIN: 0 };
		for (const value of curve) {
			if (value >= thresholds.priorityThreshold) out.PRIORITY += 1;
			else if (value >= thresholds.nurtureThreshold) out.NURTURE += 1;
			else if (value >= thresholds.watchThreshold) out.WATCH += 1;
			else out.THIN += 1;
		}
		return out;
	}

	async function save() {
		if (!draft) return;
		saving = true;
		try {
			const saved = await saveSettings({
				...draft,
				blacklist: blacklist.join(','),
				icpCountries: parse(countries),
				icpIndustries: parse(industries)
			});
			settings = saved;
			draft = { ...saved };
			blacklist = [...(saved.blacklist ?? [])];
			countries = saved.icpCountries.join(', ');
			industries = saved.icpIndustries.join(', ');
			workspace.reload();
			toasts.push('Saved. The board is ranked on it now.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			saving = false;
		}
	}

	function discard() {
		if (!settings) return;
		draft = { ...settings };
		blacklist = [...(settings.blacklist ?? [])];
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
			await workspace.reload();
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
				{#if loadError}
					The settings could not be read.
				{:else}
					{shortNumber(settings?.indexedCompanies ?? 0)} companies indexed · {lines.length} service lines · applies to
				{/if}
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
			<NotificationBell />
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
						<!--
							One grid for the whole form. It used to alternate between a
							four-column `.fields` row and lone fields capped at 460px, so
							every other row ended two thirds of the way across the panel and
							nothing lined up with what was above it.
						-->
						<div class="voice-grid">
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

							<label class="field span-2">
								<span class="label">The ask</span>
								<input class="input" bind:value={draft.voiceAsk} />
							</label>
							<label class="field">
								<span class="label">Sign-off</span>
								<input class="input" bind:value={draft.voiceSignOff} />
							</label>
							<label class="field">
								<span class="label">Signature</span>
								<input class="input" bind:value={draft.voiceSignature} placeholder="Name, role, company" />
							</label>

							<label class="field span-2">
								<span class="label">Never use these words</span>
								<input class="input" bind:value={draft.voiceAvoid} placeholder="leverage, synergy, circle back" />
								<span class="sub dim">Comma separated, applied to every draft.</span>
							</label>
							<label class="field span-2">
								<span class="label">Anything else the writer should know</span>
								<textarea
									class="textarea"
									rows="4"
									use:grow={draft.voiceNotes}
									bind:value={draft.voiceNotes}
								></textarea>
							</label>
						</div>
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">Writer brief</h3></div>
						<p class="brief">{voicePreview}</p>
						<p class="sub dim">
							{draft.modelWritesDrafts
								? 'A language model is configured, so drafts are written from the evidence under this brief.'
								: 'No model is configured, so drafts are composed from the ledger with this brief applied.'}
						</p>
					</section>
				{/if}

				{#if section === 'qualification'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Where we sell</h2>
							<span class="sub dim">{draft.geographyGate ? 'on' : 'off'}</span>
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
							<span>Keep out companies outside these markets</span>
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
								<input class="input" list="industries-present" bind:value={industries} />
								<datalist id="industries-present">
									{#each settings?.industriesPresent ?? [] as name (name)}
										<option value={name}></option>
									{/each}
								</datalist>
							</label>
						</div>
						<div class="checks">
							<label class="check">
								<input type="checkbox" bind:checked={draft.sizeGate} />
								<span>Keep out companies below the size floor</span>
							</label>
							<label class="check">
								<input type="checkbox" bind:checked={draft.competitorGate} />
								<span>Keep out competitors — firms that sell what we sell</span>
							</label>
							<label class="check">
								<input type="checkbox" bind:checked={draft.industryGate} />
								<span>Keep out companies outside those industries</span>
							</label>
						</div>
					</section>

					<section class="panel">
						<div class="panel-head">
							<h3 class="label">Never call these</h3>
							{#if blacklist.length > 0}<span class="sub dim num">{blacklist.length}</span>{/if}
						</div>
						<p class="sub dim">
							A name or a domain. A company on this list leaves the queue with the reason shown on its card,
							wherever it sits on the board — and it takes effect straight away. This list
							adds to the exclusions below; it cannot remove one of them.
						</p>
						<div class="chips">
							{#each blacklist as entry (entry)}
								<button
									class="chip"
									type="button"
									title="Remove from the blacklist"
									onclick={() => dropBlacklist(entry)}
								>
									{entry}<Icon name="close" size={11} />
								</button>
							{/each}
							{#if blacklist.length === 0}
								<span class="sub dim">Nothing excluded by hand.</span>
							{/if}
						</div>
						<div class="blacklist-add">
							<input
								class="input input-sm"
								placeholder="A company name or a domain"
								bind:value={blacklistDraft}
								onkeydown={(event) => {
									if (event.key === 'Enter') {
										event.preventDefault();
										addBlacklist();
									}
								}}
							/>
							<button class="btn btn-sm" type="button" onclick={addBlacklist}>Add</button>
						</div>

						{#if exclusions.length > 0}
							<p class="label against-label">Also kept out</p>
							<p class="sub dim">
								Companies that sell what we sell. Take one off and it is callable again.
							</p>
							<div class="chips">
								{#each shownExclusions as company (company.id)}
									<button
										class="chip"
										type="button"
										title={`Bring ${company.name} back into the queue`}
										disabled={rowBusy === company.id}
										onclick={() => bringBack(company)}
									>
										{company.name}<Icon name="close" size={11} />
									</button>
								{/each}
								<button class="tag region-pick" type="button" onclick={() => (allExclusions = !allExclusions)}>
									{allExclusions ? 'Show fewer' : `+${exclusions.length - shownExclusions.length} more`}
								</button>
							</div>
						{/if}
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">In scope</h3></div>
						<div class="metrics">
							<Metric value={shortNumber(settings?.indexedCompanies ?? 0)} label="Indexed" />
							<Metric value={shortNumber(settings?.inScope?.inGeography ?? 0)} label="In geography" />
							<Metric value={shortNumber(settings?.inScope?.aboveSizeFloor ?? 0)} label="Above the floor" />
							<Metric
								value={shortNumber(settings?.inScope?.competitors ?? 0)}
								label="Competitors"
								tone={(settings?.inScope?.competitors ?? 0) > 0 ? 'bad' : null}
							/>
							<Metric value={shortNumber(settings?.inScope?.inIndustry ?? 0)} label="In industry" />
						</div>
						<p class="sub dim">
							Counted over the profile as last saved, not the edits on this screen. Save to move them.
						</p>
					</section>
				{/if}

				{#if section === 'lines'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Configured lines</h2>
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
						</div>
						<div class="fields">
							<label class="field">
								<span class="label">Base rate</span>
								<input class="input" type="number" step="0.01" min="0.01" max="0.9" bind:value={draft.baseRate} />
								<span class="sub dim">
									The share of your pool worth calling at all. Every reading is measured against it, so a
									company scores well by beating this rather than by clearing a fixed bar.
								</span>
							</label>
							<label class="field">
								<span class="label">Priority at</span>
								<input class="input" type="number" step="0.01" bind:value={draft.priorityThreshold} />
								<span class="sub dim">
									At or above this the board calls it ready. Set it where a rep would actually pick up the
									phone.
								</span>
							</label>
							<label class="field">
								<span class="label">Nurture at</span>
								<input class="input" type="number" step="0.01" bind:value={draft.nurtureThreshold} />
								<span class="sub dim">
									Above Watch and below Priority: worth keeping close, not worth a call yet.
								</span>
							</label>
							<label class="field">
								<span class="label">Watch at</span>
								<input class="input" type="number" step="0.01" bind:value={draft.watchThreshold} />
								<span class="sub dim">
									Below this a company leaves the queue. We keep reading it, but nothing on file lifts it
									above the market default.
								</span>
							</label>
							<label class="field">
								<span class="label">Confidence shrink</span>
								<input
									class="input"
									type="number"
									step="0.05"
									min="0.1"
									max="1"
									bind:value={draft.confidenceShrink}
								/>
								<span class="sub dim">
									At 1 a row counts for what its confidence says. Lower pulls every row toward "don't know",
									so one over-confident reading cannot carry a company on its own.
								</span>
							</label>
						</div>
						<p class="sub dim">
							One theme can only carry a company as far as the Priority line above, so no single loud topic
							reaches it on its own.
						</p>
					</section>

					<section class="panel">
						<div class="panel-head"><h3 class="label">Board now</h3></div>
						<div class="metrics">
							<Metric value={bands.PRIORITY ?? 0} label="Priority" tone="hot" />
							<Metric value={bands.NURTURE ?? 0} label="Nurture" />
							<Metric value={bands.WATCH ?? 0} label="Watch" />
							<Metric value={bands.THIN ?? 0} label="Unproven" />
							<Metric value={bands.DISQUALIFIED ?? 0} label="Out of the queue" />
						</div>
					</section>

					<section class="panel">
						<div class="panel-head">
							<h3 class="label">Effect on the board</h3>
							{#if curve.length === 0}<span class="sub dim">no scores loaded</span>{/if}
						</div>
						{#if curve.length > 0}
							{@const simulated = simulate(curve, draft)}
							<div class="metrics">
								<Metric value={simulated.PRIORITY} label="Priority" tone="hot" />
								<Metric value={simulated.NURTURE} label="Nurture" />
								<Metric value={simulated.WATCH} label="Watch" />
								<Metric value={simulated.THIN} label="Unproven" />
							</div>
							
							{#if simulated.PRIORITY !== (bands.PRIORITY ?? 0)}
								<p class="note warn">
									Priority would go from {bands.PRIORITY ?? 0} to {simulated.PRIORITY}.
								</p>
							{/if}
						{/if}
					</section>
				{/if}

				{#if section === 'reading'}
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Pass cost, at your rate</h2>
						</div>
						{#if telemetry}
							{@const pairs = telemetry.signalsBySource?.classificationPairs ?? 0}
							{@const read = telemetry.overlay?.pairsSeen ?? 0}
							{@const cached = telemetry.classificationCache?.entries ?? 0}
							<div class="metrics">
								<Metric value={shortNumber(pairs)} label="Pairs if every question read every document" />
								<Metric
									value={shortNumber(read)}
									label="Pairs the model actually read"
								/>
								<Metric
									value={shortNumber(Math.max(0, pairs - read))}
									label="Pairs never sent"
									tone="good"
								/>
							</div>
							<label class="field">
								<span class="label">Cost per 1000 model calls</span>
								<input class="input" type="number" min="0" step="0.1" bind:value={perThousand} />
								<span class="sub dim">
									We do not know your contract, so the rate is yours to set and the counts are the system's. At the
									rate you enter, reading every document costs
									<strong>{(read / 1000 * perThousand).toFixed(2)}</strong> and would have cost
									<strong>{(pairs / 1000 * perThousand).toFixed(2)}</strong> asked question by question.
								</span>
							</label>
							
						{:else}
							<p class="sub dim">No telemetry yet.</p>
						{/if}
					</section>
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Model pass</h2>
							{#if telemetry?.overlay?.present}
								<span class="sub dim" title={telemetry.overlay.engine}>
									read by the model on {date(telemetry.overlay.generatedAt)}
								</span>
							{:else}
								<span class="sub dim">no model reading is shipped</span>
							{/if}
						</div>
						{#if telemetry?.overlay?.present}
							<div class="metrics">
								<Metric
									value={shortNumber(telemetry.overlay.pairsSeen ?? 0)}
									label="Pairs the model answered"
								/>
								<Metric
									value={shortNumber(telemetry.signalsBySource?.classificationPairs ?? 0)}
									label="Pairs if every question read every document"
								/>
								<Metric
									value={((telemetry.overlay.strikeRate ?? 0) * 100).toFixed(2) + '%'}
									label="Quotes we could not verify"
									tone={(telemetry.overlay.strikeRate ?? 0) > 0.02 ? 'bad' : 'good'}
								/>
							</div>
							<p class="sub dim">
								The model returned a quotation we could not find in the stored page, so we dropped it rather
								than show it.
							</p>
							{#if telemetry.overlay.sampling}
								{@const sampling = telemetry.overlay.sampling}
								<p class="sub dim">
									The cue matcher was silent on {shortNumber(sampling.cueSilentSampled)} pairs sampled at random; the model found a signal on {(sampling.cueSilentYield * 100).toFixed(2)}% of them. That is what the cheap engine alone misses.
								</p>
							{/if}
						{:else}
							<p class="sub dim">Every row was answered by the local rubric engine.</p>
						{/if}
					</section>
					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Crawl reached</h2>
							<span class="sub dim num">
								{telemetry?.freshness?.newestCaptureAgeHours != null
									? `newest document ${telemetry.freshness.newestCaptureAgeHours}h old`
									: 'no documents yet'}
							</span>
						</div>
						<div class="metrics">
							<Metric value={shortNumber(telemetry?.coverage?.companies ?? 0)} label="Companies" />
							<Metric value={shortNumber(telemetry?.coverage?.documents ?? 0)} label="Documents" />
							<Metric
								value={shortNumber(telemetry?.coverage?.withDocuments ?? 0)}
								label="With evidence"
							/>
							<Metric
								value={String(telemetry?.coverage?.withoutDocuments ?? 0)}
								label="Blind spots"
								tone={(telemetry?.coverage?.withoutDocuments ?? 0) > 0 ? 'bad' : 'good'}
							/>
						</div>
						
					</section>

					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Answered each row</h2>
						</div>
						<!-- One grid, not two: two grids each lay out their own columns, so
						     the cache figures only lined up under the engine figures while
						     both happened to hold exactly two. -->
						<div class="metrics">
							{#each Object.entries(telemetry?.signalsByEngine ?? {}) as [engine, count] (engine)}
								<Metric value={shortNumber(count)} label={engine} />
							{/each}
							<Metric
								value={shortNumber(telemetry?.classificationCache?.entries ?? 0)}
								label="Cached classifications"
							/>
							<Metric
								value={shortNumber(telemetry?.classificationCache?.reused ?? 0)}
								label="Answers reused"
								tone="good"
							/>
						</div>
					</section>

					<section class="panel">
						<div class="panel-head">
							<h2 class="dsp">Team review</h2>
						</div>
						{#if (precision?.decisions ?? 0) > 0}
							<div class="metrics">
								<Metric value={String(precision?.decisions ?? 0)} label="Rows reviewed" />
								<Metric value={String(precision?.accepted ?? 0)} label="Accepted" tone="good" />
								<Metric value={String(precision?.rejected ?? 0)} label="Rejected" tone="bad" />
								<Metric
									value={precision?.queuePrecision != null ? precision.queuePrecision.toFixed(2) : '—'}
									label="Accepted share"
								/>
							</div>
						{:else}
							<!-- Without this the panel was a heading over an empty box: the
							     figures are behind the guard, so nobody having judged a row
							     left the panel looking broken rather than looking new. -->
							<p class="sub dim">
								Nobody has judged a row yet. Accept or reject a company on its page and it lands here —
								it is what the precision figure on the board is counted from.
							</p>
						{/if}
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
		gap: var(--spacing-1);
		font-size: var(--text-base);
		color: var(--color-accent);
	}

	.sections {
		border-right: 1px solid var(--color-rule);
		background: var(--color-paper);
		padding: var(--spacing-2) 0;
		display: flex;
		flex-direction: column;
	}

	.section-link {
		display: flex;
		align-items: flex-start;
		gap: var(--spacing-2);
		text-align: left;
		padding: var(--spacing-2) var(--spacing-3) var(--spacing-2) 10px;
		border-left: 2px solid transparent;
		transition: background var(--ease), border-color var(--ease);
	}

	.section-link :global(.icon) {
		color: var(--color-ink-3);
		margin-top: 1px;
	}

	.section-link:hover {
		background: var(--color-surface);
	}

	.section-link[aria-current='true'] {
		background: var(--color-surface);
		border-left-color: var(--color-orange);
	}

	.section-text {
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
	}

	.section-label {
		font-size: var(--text-md);
		color: var(--color-ink-2);
	}

	.section-link[aria-current='true'] .section-label {
		color: var(--color-ink);
		font-weight: 500;
	}

	.section-hint {
		font-size: var(--text-micro);
		color: var(--color-ink-4);
	}

	.unsaved {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		margin-top: auto;
		padding: var(--spacing-3);
		font-size: var(--text-sm);
		color: var(--color-orange-ink);
	}

	.body {
		min-height: 0;
		background: var(--color-surface);
	}

	/*
	 * Four columns, and every field names how many it takes, so each row ends at
	 * the same right edge and the columns hold from one row to the next. It folds
	 * to two and then to one as the pane narrows, and the spans reset with it —
	 * a `span 2` in a one-column grid would ask for two columns that are not there.
	 */
	.voice-grid {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: var(--spacing-3) var(--spacing-4);
		align-items: start;
	}

	.voice-grid .span-2 {
		grid-column: span 2;
	}

	@media (max-width: 1200px) {
		.voice-grid {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}

	@media (max-width: 900px) {
		.voice-grid {
			grid-template-columns: minmax(0, 1fr);
		}

		.voice-grid .span-2 {
			grid-column: auto;
		}
	}

	/* The notes box is sized by its content — see the `grow` action. */
	.voice-grid .textarea {
		resize: none;
	}

	/*
	 * The panel takes the pane. It was capped at 900px, which left a third of the
	 * screen empty to the right of every section — including the rule each panel
	 * draws along its own foot, which stopped in mid-air.
	 */
	.panel > .field {
		/* A lone text field the width of the pane is one you cannot read a value
		   back from. The fields inside a `.fields` grid are sized by the grid. */
		max-width: 460px;
	}

	/*
	 * The figures are a grid that fills the panel, not a row of divided blocks
	 * packed against the left. Three metrics measured 221, 170 and 109 pixels and
	 * left six hundred of the panel empty, and because each was sized by its own
	 * text the labels under them started at three different places. Their left
	 * dividers also came along when the row wrapped, drawing a rule down the start
	 * of the second line, which reads as a separator between nothing and nothing.
	 */
	.panel .metrics {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
		gap: var(--spacing-4) var(--spacing-5);
		align-items: start;
	}

	/* :global — the figure is drawn by the Metric component, so a scoped selector
	   here matches nothing and Svelte says so. */
	.panel :global(.metric) {
		padding: 0;
		border-top: 2px solid var(--color-rule);
		padding-top: var(--spacing-2);
	}

	/* The number leads and the words under it stay out of its way. */
	.panel :global(.metric-val) {
		font-size: var(--text-title);
		line-height: 1;
	}

	.panel :global(.metric-lab) {
		font-size: var(--text-sm);
		line-height: 1.35;
	}

	/* A sentence about the figures is read, so it keeps a readable measure even
	   where the figures beside it now use the whole panel. */
	.panel > .sub.dim,
	.panel .field .sub {
		max-width: 78ch;
	}

	.panel-head h2 {
		font-size: var(--text-xl);
	}

	.brief {
		font-size: var(--text-base);
		line-height: 1.6;
		padding: var(--spacing-2) var(--spacing-3);
		background: var(--color-paper);
		border-left: 2px solid var(--color-orange);
		color: var(--color-ink-2);
	}

	.checks {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--spacing-1);
		margin-top: var(--spacing-2);
	}

	.chip {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px var(--spacing-2);
		border: 1px solid var(--color-rule);
		border-radius: var(--radius);
		background: var(--color-surface);
		font: inherit;
		font-size: var(--text-sm);
		cursor: pointer;
	}

	.chip:hover {
		border-color: var(--color-oxblood-line);
		background: var(--color-oxblood-wash);
		color: var(--color-oxblood);
	}

	.blacklist-add {
		display: flex;
		gap: var(--spacing-2);
		margin-top: var(--spacing-2);
	}

	.blacklist-add .input {
		flex: 1;
		max-width: 340px;
	}

	.against-label {
		margin-top: var(--spacing-4);
	}

	.region-pick {
		cursor: pointer;
		border-color: var(--color-orange-line);
		background: var(--color-orange-wash);
		color: var(--color-orange-ink);
	}

	.region-pick:hover {
		background: var(--color-surface);
	}

	.lines {
		list-style: none;
		margin: 0;
		padding: 0;
		border-top: 1px solid var(--color-rule);
	}

	.lines li {
		padding: var(--spacing-2) 0;
		border-bottom: 1px solid var(--color-rule);
	}

	.line-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--spacing-3);
	}

	.line-name {
		font-family: var(--font-display);
		font-size: var(--text-lg);
	}

	.line-tools {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
	}

	@media (max-width: 900px) {
		.panes {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: auto minmax(0, 1fr);
		}
		.sections {
			flex-direction: row;
			border-right: 0;
			border-bottom: 1px solid var(--color-rule);
			overflow-x: auto;
			padding: 0;
		}
		.section-hint,
		.unsaved {
			display: none;
		}
	}</style>
