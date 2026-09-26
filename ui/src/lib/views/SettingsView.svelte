<script lang="ts">
	import {
		createServiceLine,
		deleteServiceLine,
		fetchRegionCountries,
		fetchServiceLines,
		fetchPrecision,
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
	import { shortNumber } from '$lib/format';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Panel from '$lib/ui/Panel.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Fields from '$lib/ui/Fields.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Metrics from '$lib/ui/Metrics.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import Note from '$lib/ui/Note.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';
	import LoadingVeil from '$lib/ui/LoadingVeil.svelte';
	import Button from '$lib/ui/Button.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import type { IconName } from '$lib/ui/icons';

	const SECTIONS: { id: string; label: string; hint: string; icon: IconName }[] = [
		{ id: 'voice', label: 'Voice and style', hint: 'How drafts read', icon: 'mail' },
		{ id: 'profile', label: 'Customer profile', hint: 'Who counts as a prospect', icon: 'target' },
		{ id: 'lines', label: 'Service lines', hint: 'What we sell', icon: 'layers' },
		{ id: 'scoring', label: 'Scoring', hint: 'Where the bands sit', icon: 'sliders' },
		{ id: 'pipeline', label: 'Pipeline', hint: 'What the crawl and the model did', icon: 'pipeline' }
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

<View>
	<Topbar title="Settings">
		{#snippet subtitle()}
			{#if loadError}
				The settings could not be read from the API — a failure, not a reset
			{:else}
				{shortNumber(settings?.indexedCompanies ?? 0)} companies indexed · {lines.length} service lines · applies to
			{/if}
			all of them
		{/snippet}
		{#snippet tools()}
			{#if dirty}
				<Button onclick={discard} disabled={saving}>Discard</Button>
				<Button variant="primary" onclick={save} disabled={saving}>
					{#if saving}<Spinner />{/if}
					{saving ? 'Saving' : 'Save and re-score'}
				</Button>
			{:else}
				<span class="inline-flex items-center gap-1 text-base text-accent"><Icon name="check" size="sm" /> Saved</span>
			{/if}
			<Menu
				items={[{ id: 'voice', label: 'Read the writer brief', icon: 'mail' }]}
				onselect={() => (section = 'voice')}
				label="More settings actions"
			/>
		{/snippet}
	</Topbar>

	{#if loading && !draft}
		<Note><Spinner /> Loading settings</Note>
	{:else if draft}
		<div class="relative flex min-h-0 flex-1 max-compact:flex-col">
			<LoadingVeil show={saving} label="Saving and re-scoring" />
			<nav
				class="flex w-rail flex-none flex-col border-r border-rule bg-paper py-2 max-compact:w-full max-compact:flex-row max-compact:overflow-x-auto max-compact:border-r-0 max-compact:border-b max-compact:py-0"
				aria-label="Settings sections"
			>
				{#each SECTIONS as option (option.id)}
					<button
						class={[
							'flex items-start gap-2 border-l-2 py-2 pr-3 pl-2 text-left transition-colors',
							section === option.id ? 'border-orange bg-surface' : 'border-transparent hover:bg-surface'
						]}
						aria-current={section === option.id ? 'true' : undefined}
						onclick={() => (section = option.id)}
					>
						<span class="mt-px text-ink-3"><Icon name={option.icon} size="md" /></span>
						<span class="flex min-w-0 flex-col gap-px">
							<span class={['text-md', section === option.id ? 'font-medium text-ink' : 'text-ink-2']}>{option.label}</span>
							<span class="text-micro text-ink-4 max-compact:hidden">{option.hint}</span>
						</span>
					</button>
				{/each}
				{#if dirty}
					<p class="mt-auto flex items-center gap-1 px-3 py-3 text-sm text-orange-ink max-compact:hidden">
						<Icon name="alert" size="sm" /> Unsaved changes
					</p>
				{/if}
			</nav>

			<div class="scroll min-h-0 min-w-0 flex-1 bg-surface">
				{#if section === 'voice'}
					<Panel title="Voice and style" heading="page">
						{#snippet tools()}
							<span class="text-sm leading-normal text-ink-4">applies to every draft</span>
						{/snippet}
						<Fields>
							<Field label="Tone" for="voice-tone">
								<Select id="voice-tone" label="Tone" bind:value={draft.voiceTone} options={TONES.map((option) => ({ value: option, label: option }))} />
							</Field>
							<Field label="Register" for="voice-register">
								<Select id="voice-register" label="Register" bind:value={draft.voiceFormality} options={FORMALITY.map((option) => ({ value: option, label: option }))} />
							</Field>
							<Field label="Length" for="voice-length">
								<Select id="voice-length" label="Length" bind:value={draft.voiceLength} options={LENGTHS.map((option) => ({ value: option, label: option }))} />
							</Field>
							<Field label="Language" for="voice-language">
								<Select id="voice-language" label="Language" bind:value={draft.voiceLanguage} options={LANGUAGES.map((option) => ({ value: option, label: option }))} />
							</Field>
						</Fields>
						<Field label="The ask" for="voice-ask">
							<Input id="voice-ask" bind:value={draft.voiceAsk} />
						</Field>
						<Fields>
							<Field label="Sign-off" for="voice-signoff">
								<Input id="voice-signoff" bind:value={draft.voiceSignOff} />
							</Field>
							<Field label="Signature" for="voice-signature">
								<Input id="voice-signature" bind:value={draft.voiceSignature} placeholder="Name, role, company" />
							</Field>
						</Fields>
						<Field label="Never use these words" hint="Comma separated, applied to every draft." for="voice-avoid">
							<Input id="voice-avoid" bind:value={draft.voiceAvoid} placeholder="leverage, synergy, circle back" />
						</Field>
						<Field label="Anything else the writer should know" for="voice-notes">
							<Textarea id="voice-notes" rows={3} bind:value={draft.voiceNotes} />
						</Field>
					</Panel>

					<Panel title="The brief the writer receives" heading="label">
						<p class="border-l-2 border-orange bg-paper px-3 py-2 text-base leading-relaxed text-ink-2">{voicePreview}</p>
						<p class="text-sm leading-normal text-ink-4">
							{draft.modelWritesDrafts
								? 'A language model is configured, so drafts are written from the evidence under this brief.'
								: 'No model is configured, so drafts are composed from the ledger with this brief applied.'}
						</p>
					</Panel>
				{/if}

				{#if section === 'profile'}
					<Panel title="Where we sell" heading="page">
						{#snippet tools()}
							<span class="text-sm leading-normal text-ink-4">{draft!.geographyGate ? 'gate on' : 'gate off'}</span>
						{/snippet}
						<Field label="Add a whole region">
							<div class="flex flex-wrap gap-1">
								{#each regionsOf(regionCountries) as group (group.key)}
									<button
										class="cursor-pointer rounded-sm border border-orange-line bg-orange-wash px-1 text-micro leading-normal font-medium tracking-wide text-orange-ink transition-colors hover:bg-surface"
										onclick={() => addRegion(group.codes)}
									>
										+ {group.name}
									</button>
								{/each}
							</div>
						</Field>
						<Field label="Countries" hint="Empty means every market." for="icp-countries">
							<Textarea id="icp-countries" rows={2} bind:value={countries} />
						</Field>
						<div class="flex flex-wrap gap-1">
							{#each parse(countries).slice(0, 24) as code (code)}<Tag>{code}</Tag>{/each}
							{#if parse(countries).length > 24}
								<Tag tone="bare">+{parse(countries).length - 24}</Tag>
							{/if}
						</div>
						<Checkbox bind:checked={draft.geographyGate}>Remove companies outside these markets — the reason is shown on the card</Checkbox>
					</Panel>

					<Panel title="Who we sell to" heading="page">
						<Fields>
							<Field label="Minimum employees" for="icp-min-employees">
								<Input id="icp-min-employees" type="number" min="0" bind:value={draft.icpMinEmployees} />
							</Field>
							<Field label="Industries (optional)" for="icp-industries">
								<Input id="icp-industries" list="industries-present" bind:value={industries} />
								<datalist id="industries-present">
									{#each settings?.industriesPresent ?? [] as name (name)}
										<option value={name}></option>
									{/each}
								</datalist>
							</Field>
						</Fields>
						<div class="flex flex-col gap-1">
							<Checkbox bind:checked={draft.sizeGate}>Remove companies below the size floor</Checkbox>
							<Checkbox bind:checked={draft.competitorGate}>Remove competitors — firms that sell what we sell</Checkbox>
							<Checkbox bind:checked={draft.industryGate}>Remove companies outside those industries — the reason is shown on the card</Checkbox>
						</div>
					</Panel>

					<Panel title="What that leaves" heading="label">
						<Metrics>
							<Metric value={shortNumber(settings?.indexedCompanies ?? 0)} label="Indexed" />
							<Metric value={shortNumber(settings?.inScope?.inGeography ?? 0)} label="In geography" />
							<Metric value={shortNumber(settings?.inScope?.aboveSizeFloor ?? 0)} label="Above the floor" />
							<Metric
								value={shortNumber(settings?.inScope?.competitors ?? 0)}
								label="Competitors"
								tone={(settings?.inScope?.competitors ?? 0) > 0 ? 'bad' : null}
							/>
							<Metric value={shortNumber(settings?.inScope?.inIndustry ?? 0)} label="In industry" />
						</Metrics>
					</Panel>
				{/if}

				{#if section === 'lines'}
					<Panel title="What we sell" heading="page">
						{#snippet tools()}
							<span class="text-sm leading-normal text-ink-4">{lines.length} configured</span>
						{/snippet}
						<ul class="m-0 list-none border-t border-rule p-0">
							{#each lines as line (line.id)}
								<li class="border-b border-rule py-2">
									<div class="flex items-center justify-between gap-3">
										<span class="font-display text-lg">{line.name}</span>
										<div class="flex items-center gap-1">
											<Tag tone="bare">{line.questionCount} questions</Tag>
											<Tag tone={line.active ? 'accent' : 'bare'}>{line.active ? 'active' : 'off'}</Tag>
											{#if lineBusy === line.id}<Spinner />{/if}
											<Menu
												items={lineMenu(line)}
												onselect={(id) => runLine(line, id)}
												size="sm"
												label={`Actions for ${line.name}`}
											/>
										</div>
									</div>
									<p class="type-sub">
										{line.valueProposition ?? 'No value proposition yet — the writer falls back to a generic one.'}
									</p>
								</li>
							{/each}
						</ul>
					</Panel>

					<Panel title="Add a service line" heading="label">
						<Fields>
							<Field label="Name" for="line-name">
								<Input id="line-name" bind:value={newLine.name} placeholder="Managed Detection and Response" />
							</Field>
							<Field label="Value proposition" for="line-value">
								<Input id="line-value" bind:value={newLine.valueProposition} placeholder="we run the SOC so your team keeps the context" />
							</Field>
						</Fields>
						<div>
							<Button variant="ink" icon="plus" disabled={lineBusy === 'new'} onclick={addLine}>Add service line</Button>
						</div>
					</Panel>
				{/if}

				{#if section === 'scoring'}
					<Panel title="Where the bands sit" heading="page">
						{#snippet tools()}
							<span class="num text-sm leading-normal text-ink-4">cap {draft!.themeCap.toFixed(3)} nats</span>
						{/snippet}
						<Fields>
							<Field label="Base rate" for="scoring-base-rate">
								<Input id="scoring-base-rate" type="number" step="0.01" min="0.01" max="0.9" bind:value={draft.baseRate} />
							</Field>
							<Field label="Priority at" for="scoring-priority">
								<Input id="scoring-priority" type="number" step="0.01" bind:value={draft.priorityThreshold} />
							</Field>
							<Field label="Nurture at" for="scoring-nurture">
								<Input id="scoring-nurture" type="number" step="0.01" bind:value={draft.nurtureThreshold} />
							</Field>
							<Field label="Watch at" for="scoring-watch">
								<Input id="scoring-watch" type="number" step="0.01" bind:value={draft.watchThreshold} />
							</Field>
							<Field
								label="Confidence shrink"
								for="scoring-confidence"
								hint={`At 1 a row counts for what its confidence says. Lower pulls every row toward "don't know", so one over-confident reading cannot carry a company on its own.`}
							>
								<Input id="scoring-confidence" type="number" step="0.05" min="0.1" max="1" bind:value={draft.confidenceShrink} />
							</Field>
						</Fields>
						<p class="text-sm leading-normal text-ink-4">The base rate is the share of the pool worth calling.</p>
					</Panel>

					<Panel title="The board right now" heading="label">
						<Metrics>
							<Metric value={bands.PRIORITY ?? 0} label="Priority" tone="hot" />
							<Metric value={bands.NURTURE ?? 0} label="Nurture" />
							<Metric value={bands.WATCH ?? 0} label="Watch" />
							<Metric value={bands.THIN ?? 0} label="Thin" />
							<Metric value={bands.DISQUALIFIED ?? 0} label="Rejected" />
						</Metrics>
					</Panel>

					<Panel title="Where these numbers would put the board" heading="label">
						{#snippet tools()}
							{#if curve.length === 0}<span class="text-sm leading-normal text-ink-4">no scores loaded</span>{/if}
						{/snippet}
						{#if curve.length > 0}
							{@const simulated = simulate(curve, draft)}
							<Metrics>
								<Metric value={simulated.PRIORITY} label="Priority" tone="hot" />
								<Metric value={simulated.NURTURE} label="Nurture" />
								<Metric value={simulated.WATCH} label="Watch" />
								<Metric value={simulated.THIN} label="Thin" />
							</Metrics>
							{#if simulated.PRIORITY !== (bands.PRIORITY ?? 0)}
								<Note tone="warn">
									Priority would go from {bands.PRIORITY ?? 0} to {simulated.PRIORITY}.
								</Note>
							{/if}
						{/if}
					</Panel>
				{/if}

				{#if section === 'pipeline'}
					<Panel title="What a pass costs, at your own rate" heading="page">
						{#if telemetry}
							{@const pairs = telemetry.signalsBySource?.classificationPairs ?? 0}
							{@const read = telemetry.overlay?.pairsSeen ?? 0}
							{@const cached = telemetry.classificationCache?.entries ?? 0}
							<Metrics>
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
							</Metrics>
							<Field label="Your cost per 1,000 model calls" for="pipeline-cost">
								<Input id="pipeline-cost" type="number" min="0" step="0.1" bind:value={perThousand} />
								{#snippet hint()}
									We do not know your contract, so the rate is yours to set and the counts are the system's. At the
									rate you enter, reading this corpus costs
									<strong>{(read / 1000 * perThousand).toFixed(2)}</strong> and would have cost
									<strong>{(pairs / 1000 * perThousand).toFixed(2)}</strong> asked question by question.
								{/snippet}
							</Field>
						{:else}
							<p class="text-sm leading-normal text-ink-4">No telemetry yet.</p>
						{/if}
					</Panel>
					<Panel title="What the model half cost, and what it found" heading="page">
						{#snippet tools()}
							{#if telemetry?.overlay?.present}
								<span class="num text-sm leading-normal text-ink-4">{telemetry.overlay.engine}</span>
							{:else}
								<span class="text-sm leading-normal text-ink-4">no overlay shipped</span>
							{/if}
						{/snippet}
						{#if telemetry?.overlay?.present}
							<Metrics>
								<Metric
									value={shortNumber(telemetry.overlay.pairsSeen ?? 0)}
									label="Pairs read by the model"
								/>
								<Metric
									value={shortNumber(telemetry.signalsBySource?.classificationPairs ?? 0)}
									label="Pairs in the corpus"
								/>
								<Metric
									value={((telemetry.overlay.strikeRate ?? 0) * 100).toFixed(2) + '%'}
									label="Quotes struck"
									tone={(telemetry.overlay.strikeRate ?? 0) > 0.02 ? 'bad' : 'good'}
								/>
							</Metrics>
							{#if telemetry.overlay.sampling}
								{@const sampling = telemetry.overlay.sampling}
								<p class="text-sm leading-normal text-ink-4">
									The cue matcher was silent on {shortNumber(sampling.cueSilentSampled)} pairs sampled at random; the model found a signal on {(sampling.cueSilentYield * 100).toFixed(2)}% of them. That is what the cheap engine alone misses.
								</p>
							{/if}
						{:else}
							<p class="text-sm leading-normal text-ink-4">Every row was answered by the local rubric engine.</p>
						{/if}
					</Panel>
					<Panel title="What the crawl reached" heading="page">
						{#snippet tools()}
							<span class="num text-sm leading-normal text-ink-4">
								{telemetry?.freshness?.newestCaptureAgeHours != null
									? `newest capture ${telemetry.freshness.newestCaptureAgeHours}h old`
									: 'no captures'}
							</span>
						{/snippet}
						<Metrics>
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
						</Metrics>
					</Panel>

					<Panel title="What answered each row" heading="page">
						<Metrics>
							{#each Object.entries(telemetry?.signalsByEngine ?? {}) as [engine, count] (engine)}
								<Metric value={shortNumber(count)} label={engine} />
							{/each}
						</Metrics>
						<Metrics>
							<Metric
								value={shortNumber(telemetry?.classificationCache?.entries ?? 0)}
								label="Cached classifications"
							/>
							<Metric
								value={shortNumber(telemetry?.classificationCache?.reused ?? 0)}
								label="Answers reused"
								tone="good"
							/>
						</Metrics>
					</Panel>

					<Panel title="What the team did with what it was shown" heading="page">
						{#if (precision?.decisions ?? 0) > 0}
							<Metrics>
								<Metric value={String(precision?.decisions ?? 0)} label="Rows reviewed" />
								<Metric value={String(precision?.accepted ?? 0)} label="Accepted" tone="good" />
								<Metric value={String(precision?.rejected ?? 0)} label="Rejected" tone="bad" />
								<Metric
									value={precision?.queuePrecision != null ? precision.queuePrecision.toFixed(2) : '—'}
									label="Accepted share"
								/>
							</Metrics>
						{/if}
					</Panel>
				{/if}
			</div>
		</div>
	{/if}
</View>
