<script lang="ts">
	import {
		createIcpProfile,
		deleteIcpProfile,
		fetchIcpProfiles,
		fetchSettings,
		previewIcpProfile,
		updateIcpProfile,
		type IcpMatch,
		type IcpProfile
	} from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { regionChoices, regionLabel } from '$lib/regions';
	import { shortNumber } from '$lib/format';
	import { portal } from '$lib/portal';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import type { MenuItem } from '$lib/components/menu';

	const BANDS = [
		'1-10',
		'11-50',
		'51-200',
		'201-500',
		'501-1000',
		'1001-5000',
		'5001-10000',
		'10001+'
	];

	/** Twelve hues at four depths. Wide enough that two books are told apart at a
	 * glance in the list, and the hex box below it takes anything else. */
	const PALETTE = [
		'#5f2f2f', '#6f4a1f', '#6f6220', '#5f6f2f', '#3f6f3f', '#2f6f5f',
		'#2f626f', '#2f4a6f', '#3a3a6f', '#552f6f', '#6f2f5f', '#6f2f42',
		'#8f4a4a', '#9f6f2f', '#9f8f3a', '#8f9f4a', '#5f9f5f', '#4a9f8f',
		'#4a8f9f', '#4a6f9f', '#55559f', '#7a4a9f', '#9f4a8f', '#9f4a62',
		'#c96f6f', '#d99a4f', '#d9c95f', '#bfd97a', '#8fcf8f', '#7acfb8',
		'#7ab8cf', '#7a9fcf', '#8a8acf', '#ab7acf', '#cf7ab8', '#cf7a94',
		'#e6b3b3', '#e6cfa6', '#e6e0a6', '#d9e6b8', '#c2e6c2', '#b8e6da',
		'#b8dae6', '#b8c9e6', '#c2c2e6', '#d4b8e6', '#e6b8da', '#e6b8c4'
	];

	type Draft = {
		id: string | null;
		name: string;
		color: string;
		countries: string;
		regions: string;
		industries: string;
		minEmployees: number;
		sizeBands: string;
		excludeCompetitors: boolean;
		untouchedOnly: boolean;
		active: boolean;
	};

	const BLANK: Draft = {
		id: null,
		name: '',
		color: '#2f6f4f',
		countries: '',
		regions: '',
		industries: '',
		minEmployees: 0,
		sizeBands: '',
		excludeCompetitors: false,
		untouchedOnly: false,
		active: true
	};

	let profiles = $state<IcpProfile[]>([]);
	let loading = $state(true);
	let query = $state('');
	let draft = $state<Draft>({ ...BLANK });
	let dirty = $state(false);
	let saving = $state(false);
	let busyOn = $state<string | null>(null);
	let preview = $state<IcpMatch | null>(null);
	let previewing = $state(false);
	let industriesPresent = $state<string[]>([]);
	let industryDraft = $state('');
	let countryDraft = $state('');
	let paletteOpen = $state(false);
	let hexDraft = $state('');

	function list(value: string): string[] {
		return value
			.split(',')
			.map((entry) => entry.trim())
			.filter((entry) => entry !== '');
	}

	function join(values: string[]): string {
		return [...new Set(values)].join(',');
	}

	function toggleIn(value: string, entry: string): string {
		const held = list(value);
		const folded = entry.toLowerCase();
		const next = held.some((item) => item.toLowerCase() === folded)
			? held.filter((item) => item.toLowerCase() !== folded)
			: [...held, entry];
		return join(next);
	}

	const choices = $derived(regionChoices(workspace.regions));
	const chosenCountries = $derived(list(draft.countries));
	const chosenRegions = $derived(list(draft.regions));
	const chosenIndustries = $derived(list(draft.industries));
	const chosenBands = $derived(list(draft.sizeBands));

	const matched = $derived.by(() => {
		const needle = query.trim().toLowerCase();
		if (!needle) return profiles;
		return profiles.filter((profile) => profile.name.toLowerCase().includes(needle));
	});

	const reach = $derived(preview ? preview.companies : 0);

	$effect(() => {
		let cancelled = false;
		Promise.all([fetchIcpProfiles(), fetchSettings()])
			.then(([all, settings]) => {
				if (cancelled) return;
				profiles = all;
				industriesPresent = settings.industriesPresent ?? [];
			})
			.catch(() => {})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	/*
	 * The count comes from the same predicate the board uses, so what this screen
	 * promises and what the board then shows cannot disagree.
	 */
	$effect(() => {
		const input = {
			countries: draft.countries,
			regions: draft.regions,
			industries: draft.industries,
			minEmployees: draft.minEmployees,
			sizeBands: draft.sizeBands,
			excludeCompetitors: draft.excludeCompetitors,
			untouchedOnly: draft.untouchedOnly
		};
		let cancelled = false;
		previewing = true;
		const timer = setTimeout(() => {
			previewIcpProfile(input)
				.then((result) => {
					if (!cancelled) preview = result;
				})
				.catch(() => {
					if (!cancelled) preview = null;
				})
				.finally(() => {
					if (!cancelled) previewing = false;
				});
		}, 300);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	function load() {
		return fetchIcpProfiles()
			.then((list) => (profiles = list))
			.catch(() => (profiles = []));
	}

	function startNew() {
		draft = { ...BLANK };
		dirty = true;
	}

	function open(profile: IcpProfile) {
		draft = {
			id: profile.id,
			name: profile.name,
			color: profile.color ?? BLANK.color,
			countries: join(profile.countries),
			regions: join(profile.regions),
			industries: join(profile.industries),
			minEmployees: profile.minEmployees,
			sizeBands: join(profile.sizeBands),
			excludeCompetitors: profile.excludeCompetitors,
			untouchedOnly: profile.untouchedOnly,
			active: profile.active
		};
		dirty = false;
	}

	function discard() {
		const current = profiles.find((profile) => profile.id === draft.id);
		if (current) open(current);
	}

	function body(source: Draft) {
		return {
			name: source.name.trim(),
			color: source.color,
			countries: source.countries,
			regions: source.regions,
			industries: source.industries,
			minEmployees: source.minEmployees,
			sizeBands: source.sizeBands,
			excludeCompetitors: source.excludeCompetitors,
			untouchedOnly: source.untouchedOnly,
			active: source.active
		};
	}

	async function save() {
		if (draft.name.trim().length < 2) {
			toasts.push('Give the profile a name a rep would recognise.', 'warn');
			return;
		}
		saving = true;
		try {
			if (draft.id) await updateIcpProfile(draft.id, body(draft));
			else await createIcpProfile(body(draft));
			await load();
			const saved = profiles.find((profile) => profile.name === draft.name.trim());
			if (saved) open(saved);
			dirty = false;
			workspace.reload();
			toasts.push('Saved. The board carries it now.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			saving = false;
		}
	}

	async function toggle(profile: IcpProfile) {
		busyOn = profile.id;
		try {
			await updateIcpProfile(profile.id, {
				name: profile.name,
				color: profile.color,
				countries: join(profile.countries),
				regions: join(profile.regions),
				industries: join(profile.industries),
				minEmployees: profile.minEmployees,
				sizeBands: join(profile.sizeBands),
				excludeCompetitors: profile.excludeCompetitors,
				untouchedOnly: profile.untouchedOnly,
				active: !profile.active
			});
			await load();
			workspace.reload();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busyOn = null;
		}
	}

	async function remove(profile: IcpProfile) {
		busyOn = profile.id;
		try {
			await deleteIcpProfile(profile.id);
			await load();
			if (draft.id === profile.id) draft = { ...BLANK };
			workspace.reload();
			toasts.push('Profile deleted. No company changed.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busyOn = null;
		}
	}

	function rowMenu(profile: IcpProfile): MenuItem[] {
		return [
			{ id: 'edit', label: 'Open it', icon: 'eye' },
			{
				id: 'toggle',
				label: profile.active ? 'Switch off' : 'Switch on',
				icon: profile.active ? 'close' : 'check',
				separatorBefore: true
			},
			{ id: 'delete', label: 'Delete the profile', icon: 'trash', danger: true }
		];
	}

	function runRow(profile: IcpProfile, id: string) {
		if (id === 'edit') open(profile);
		else if (id === 'toggle') void toggle(profile);
		else if (id === 'delete') void remove(profile);
	}

	function addCue(kind: 'country' | 'industry' | 'region', value: string) {
		const entry = value.trim();
		if (entry === '') return;
		if (kind === 'country') draft.countries = toggleIn(draft.countries, entry.toUpperCase());
		else if (kind === 'industry') draft.industries = toggleIn(draft.industries, entry);
		else draft.regions = toggleIn(draft.regions, entry);
		dirty = true;
	}

	function openPalette() {
		hexDraft = draft.color;
		paletteOpen = true;
	}

	function chooseColour(value: string) {
		draft.color = value;
		dirty = true;
		paletteOpen = false;
	}

	function applyHex() {
		const value = hexDraft.trim();
		if (/^#[0-9a-fA-F]{3,8}$/.test(value)) chooseColour(value.toLowerCase());
	}
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">ICP Studio</h1>
			<p class="sub">
				{profiles.length} {profiles.length === 1 ? 'profile' : 'profiles'} ·
				{profiles.filter((profile) => profile.active).length} in force · the board is the companies in them
			</p>
		</div>
		<div class="topbar-tools">
			<button class="btn btn-primary" onclick={startNew}>
				<Icon name="plus" size={15} />
				New profile
			</button>
		</div>
	</header>

	<div class="toolbar">
		<label class="search">
			<Icon name="search" size={14} />
			<span class="sr-only">Filter profiles</span>
			<input class="input input-sm" type="search" bind:value={query} placeholder="Filter profiles" />
		</label>
		<span class="spacer"></span>
		<span class="sub dim">A profile defines a market · the board narrows to the ones in force</span>
	</div>

	<div class="panes">
		<section class="profiles" aria-label="ICP profiles">
			<LoadingVeil show={loading} label="Loading profiles" />
			<ul>
				{#each matched as profile (profile.id)}
					<li>
						<button
							class="row"
							class:current={draft.id === profile.id}
							aria-current={draft.id === profile.id}
							onclick={() => open(profile)}
						>
							<span class="swatch" style={`background:${profile.color ?? 'var(--color-rule-strong)'}`}></span>
							<span class="row-body">
								<span class="row-name">{profile.name}</span>
								<span class="sub dim">
									{shortNumber(profile.companies)} {profile.companies === 1 ? 'company' : 'companies'}
									{#if !profile.active} · out of force{/if}
								</span>
							</span>
						</button>
						<Menu items={rowMenu(profile)} onselect={(id) => runRow(profile, id)} label={`Actions for ${profile.name}`} />
					</li>
				{:else}
					<li class="empty">
						<p class="sub">
							{query ? 'No profile matches that.' : 'No profiles yet. The board shows every company until you make one.'}
						</p>
					</li>
				{/each}
			</ul>
		</section>

		<section class="editor" aria-label="The profile">
			<div class="editor-head">
				<h2 class="dsp">{draft.id ? draft.name || 'Untitled profile' : 'New profile'}</h2>
				{#if !draft.id}<span class="tag tag-wash">unsaved</span>{/if}
				{#if !draft.active}<span class="tag tag-wash">out of force</span>{/if}
				<span class="spacer"></span>
				{#if dirty}<span class="sub dim">Unsaved changes</span>{/if}
			</div>

			<div class="editor-body scroll">
				<section class="panel">
					<div class="panel-head"><h3 class="label">Identity</h3></div>
					<label class="field">
						<span class="label">Profile name</span>
						<input
							class="input"
							bind:value={draft.name}
							oninput={() => (dirty = true)}
							placeholder="DACH industrial automation"
						/>
					</label>
					<label class="check">
						<input
							type="checkbox"
							checked={draft.active}
							onchange={() => {
								draft.active = !draft.active;
								dirty = true;
							}}
						/>
						<span>In force — the board counts this book</span>
					</label>
				</section>

				<section class="panel">
					<div class="panel-head">
						<h3 class="label">Geography</h3>
						<span class="sub dim">{chosenRegions.length + chosenCountries.length || 'any'}</span>
					</div>
					<div class="chips">
						{#each chosenRegions as region (region)}
							<button
								class="chip"
								title="Every country this region covers"
								onclick={() => {
									draft.regions = toggleIn(draft.regions, region);
									dirty = true;
								}}
							>
								{regionLabel(workspace.regions, region)}
								<Icon name="close" size={11} />
							</button>
						{/each}
						{#each chosenCountries as country (country)}
							<button
								class="chip"
								onclick={() => {
									draft.countries = toggleIn(draft.countries, country);
									dirty = true;
								}}
							>
								{country}
								<Icon name="close" size={11} />
							</button>
						{/each}
						{#if chosenRegions.length === 0 && chosenCountries.length === 0}
							<span class="sub dim">Anywhere.</span>
						{/if}
					</div>
					<div class="add">
						<Menu
							text="Add a region"
							size="sm"
							align="start"
							label="Regions to sell into"
							items={choices.map((choice) => ({
								id: choice.key,
								label: choice.label,
								checked: chosenRegions.includes(choice.key)
							}))}
							onselect={(key) => addCue('region', key)}
						/>
						<form
							class="cue-add"
							onsubmit={(event) => {
								event.preventDefault();
								addCue('country', countryDraft);
								countryDraft = '';
							}}
						>
							<input
								class="input input-sm"
								bind:value={countryDraft}
								placeholder="Country code, e.g. DE"
								aria-label="Add a country"
							/>
							<button class="btn btn-sm" type="submit">Add</button>
						</form>
					</div>
				</section>

				<section class="panel">
					<div class="panel-head">
						<h3 class="label">Industries</h3>
						<span class="sub dim">{chosenIndustries.length || 'any'}</span>
					</div>
					<div class="chips">
						{#each chosenIndustries as industry (industry)}
							<button
								class="chip"
								onclick={() => {
									draft.industries = toggleIn(draft.industries, industry);
									dirty = true;
								}}
							>
								{industry}
								<Icon name="close" size={11} />
							</button>
						{/each}
						{#if chosenIndustries.length === 0}
							<span class="sub dim">Any industry.</span>
						{/if}
					</div>
					<div class="add">
						<Menu
							text="Add an industry"
							size="sm"
							align="start"
							label="Industries to sell into"
							items={industriesPresent.map((industry) => ({
								id: industry,
								label: industry,
								checked: chosenIndustries.some((chosen) => chosen.toLowerCase() === industry.toLowerCase())
							}))}
							onselect={(industry) => addCue('industry', industry)}
						/>
						<form
							class="cue-add"
							onsubmit={(event) => {
								event.preventDefault();
								addCue('industry', industryDraft);
								industryDraft = '';
							}}
						>
							<input
								class="input input-sm"
								bind:value={industryDraft}
								placeholder="Or type one"
								aria-label="Add an industry"
							/>
							<button class="btn btn-sm" type="submit">Add</button>
						</form>
					</div>
				</section>

				<section class="panel">
					<div class="panel-head"><h3 class="label">Company size</h3></div>
					<div class="chips">
						{#each BANDS as band (band)}
							<button
								class="chip"
								class:on={chosenBands.includes(band)}
								onclick={() => {
									draft.sizeBands = toggleIn(draft.sizeBands, band);
									dirty = true;
								}}
							>
								{band}
							</button>
						{/each}
					</div>
					<label class="field">
						<span class="label">Headcount floor</span>
						<input
							class="input input-sm num"
							type="number"
							min="0"
							bind:value={draft.minEmployees}
							oninput={() => (dirty = true)}
						/>
					</label>
					{#if chosenBands.length === 0 && draft.minEmployees === 0}
						<span class="sub dim">Any size. Headcount is estimated from the size band.</span>
					{/if}
				</section>

				<section class="panel">
					<div class="panel-head"><h3 class="label">Exclusions</h3></div>
					<label class="check">
						<input
							type="checkbox"
							checked={draft.excludeCompetitors}
							onchange={() => {
								draft.excludeCompetitors = !draft.excludeCompetitors;
								dirty = true;
							}}
						/>
						<span>Vendors — companies that sell what we sell</span>
					</label>
					<label class="check">
						<input
							type="checkbox"
							checked={draft.untouchedOnly}
							onchange={() => {
								draft.untouchedOnly = !draft.untouchedOnly;
								dirty = true;
							}}
						/>
						<span>Accounts already worked</span>
					</label>
				</section>

				<section class="panel">
					<div class="panel-head">
						<h3 class="label">Reach</h3>
						{#if previewing}<span class="sub dim">counting</span>{/if}
					</div>
					<div class="metrics">
						<Metric value={shortNumber(reach)} label="Companies in this book" tone="hot" />
					</div>
					{#if preview && preview.sample.length > 0}
						<p class="sub">
							{preview.sample.join(' · ')}{#if preview.companies > preview.sample.length}, and {shortNumber(
									preview.companies - preview.sample.length
								)} more{/if}
						</p>
					{:else if preview}
						<p class="note warn">
							<Icon name="alert" size={14} />
							<span>No company we hold matches this. Loosen a filter or clear one.</span>
						</p>
					{/if}
				</section>

				<section class="panel">
					<div class="panel-head"><h3 class="label">Appearance</h3></div>
					<button class="colour-btn" onclick={openPalette}>
						<span class="colour-swatch" style={`background:${draft.color}`}></span>
						<span class="num">{draft.color}</span>
						<span class="spacer"></span>
						<span class="sub dim">Choose</span>
					</button>
					<span class="sub dim">Marks this book in the list, and wherever a book is chosen.</span>
				</section>
			</div>

			<footer class="editor-foot">
				<button class="btn btn-ink" onclick={save} disabled={saving}>
					{#if saving}<span class="spinner"></span>{/if}
					{draft.id ? 'Save the profile' : 'Create the profile'}
				</button>
				{#if draft.id}
					<button class="btn btn-quiet" onclick={discard}>Discard changes</button>
				{/if}
				<span class="spacer"></span>
				<span class="sub dim">{draft.id ? draft.name : 'not saved yet'}</span>
			</footer>
		</section>
	</div>
</div>

{#if paletteOpen}
	<div
		class="scrim"
		use:portal
		role="presentation"
		onclick={(event) => {
			if (event.target === event.currentTarget) paletteOpen = false;
		}}
	>
		<div class="palette" role="dialog" aria-modal="true" aria-label="Choose a colour">
			<header class="palette-head">
				<h2 class="dsp">Choose a colour</h2>
				<span class="spacer"></span>
				<button class="icon-btn" onclick={() => (paletteOpen = false)} aria-label="Close">
					<Icon name="close" size={15} />
				</button>
			</header>
			<div class="palette-grid">
				{#each PALETTE as hex (hex)}
					<button
						class="cell"
						class:chosen={draft.color.toLowerCase() === hex}
						style={`background:${hex}`}
						aria-label={hex}
						title={hex}
						onclick={() => chooseColour(hex)}
					></button>
				{/each}
			</div>
			<div class="palette-foot">
				<label class="field">
					<span class="label">Or a hex value</span>
					<input
						class="input input-sm num"
						bind:value={hexDraft}
						onkeydown={(event) => {
							if (event.key === 'Enter') applyHex();
						}}
						placeholder="#2f6f4f"
					/>
				</label>
				<input
					type="color"
					class="colour-input"
					bind:value={draft.color}
					oninput={() => (dirty = true)}
					aria-label="Pick any colour"
				/>
				<span class="spacer"></span>
				<button class="btn btn-sm" onclick={applyHex}>Use it</button>
			</div>
		</div>
	</div>
{/if}

<style>
	.topbar .sub {
		color: var(--color-ink-3);
	}

	.toolbar .spacer,
	.editor-head .spacer,
	.colour-btn .spacer,
	.palette-head .spacer,
	.palette-foot .spacer,
	.editor-foot .spacer {
		margin-left: auto;
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		color: var(--color-ink-3);
	}

	.search .input {
		width: 220px;
	}

	.panes {
		display: grid;
		grid-template-columns: 288px minmax(0, 1fr);
		flex: 1;
		min-height: 0;
		border-top: 1px solid var(--color-rule);
	}

	.profiles {
		position: relative;
		min-height: 0;
		overflow-y: auto;
		border-right: 1px solid var(--color-rule);
		background: var(--color-surface);
	}

	.profiles ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.profiles li {
		display: flex;
		align-items: center;
		border-bottom: 1px solid var(--color-rule);
	}

	.profiles li.empty {
		padding: var(--spacing-4);
		display: block;
	}

	.row {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		flex: 1;
		min-width: 0;
		padding: var(--spacing-2) var(--spacing-3);
		text-align: left;
		cursor: pointer;
	}

	.row:hover {
		background: var(--color-paper);
	}

	.row.current {
		background: var(--color-orange-wash);
	}

	.swatch {
		width: 10px;
		height: 10px;
		flex: none;
		border-radius: 2px;
		border: 1px solid var(--color-ink-4);
	}

	.row-body {
		min-width: 0;
	}

	.row-name {
		display: block;
		font-size: var(--text-base);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.editor {
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	.editor-head {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-3) var(--spacing-4);
		border-bottom: 1px solid var(--color-rule);
		flex: none;
	}

	/*
	 * The panels tile the width rather than stacking in a 760px column: this
	 * screen is for comparing criteria, and a column of narrow panels left half
	 * the window empty. Each panel's own fields stay one line to a decision.
	 */
	.editor-body {
		flex: 1;
		min-height: 0;
		padding: var(--spacing-4);
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
		gap: var(--spacing-3);
		align-content: start;
	}

	.panel {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
	}

	.panel-head {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}

	.panel-head .label {
		color: var(--color-ink-2);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--spacing-1);
		min-height: 22px;
	}

	.chip {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: var(--text-micro);
		padding: 1px 5px;
		border: 1px solid var(--color-rule-strong);
		background: var(--color-surface);
		color: var(--color-ink-2);
		cursor: pointer;
	}

	.chip:hover {
		border-color: var(--color-orange-line);
		color: var(--color-orange-ink);
	}

	.chip.on {
		border-color: var(--color-ink);
		color: var(--color-ink);
	}

	.add {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		flex-wrap: wrap;
	}

	.cue-add {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
	}

	.cue-add .input {
		width: 160px;
	}

	.panel .metrics {
		margin-left: 0;
	}

	.panel .note {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}

	.colour-btn {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-2) var(--spacing-3);
		border: 1px solid var(--color-rule-strong);
		background: var(--color-surface);
		cursor: pointer;
		text-align: left;
	}

	.colour-btn:hover {
		border-color: var(--color-ink);
	}

	.colour-swatch {
		width: 22px;
		height: 22px;
		flex: none;
		border-radius: 2px;
		border: 1px solid var(--color-ink-4);
	}

	.editor-foot {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-3) var(--spacing-4);
		border-top: 1px solid var(--color-rule);
		flex: none;
	}

	.scrim {
		animation: fade-in var(--ease-enter) both;
		position: fixed;
		inset: 0;
		background: rgb(18 16 13 / 0.34);
		display: flex;
		align-items: center;
		justify-content: center;
		z-index: var(--z-index-sheet);
	}

	.palette {
		animation: rise var(--ease-enter) both;
		width: min(680px, calc(100vw - var(--spacing-5) * 2));
		max-height: calc(100vh - var(--spacing-5) * 2);
		overflow-y: auto;
		background: var(--color-surface);
		border: 1px solid var(--color-ink);
		padding: var(--spacing-4);
		display: flex;
		flex-direction: column;
		gap: var(--spacing-3);
	}

	.palette-head {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
	}

	.palette-grid {
		display: grid;
		grid-template-columns: repeat(8, 1fr);
		gap: var(--spacing-1);
	}

	.cell {
		aspect-ratio: 1;
		border: 1px solid var(--color-rule-strong);
		border-radius: 2px;
		cursor: pointer;
	}

	.cell.chosen {
		outline: 2px solid var(--color-ink);
		outline-offset: 1px;
	}

	.palette-foot {
		display: flex;
		align-items: flex-end;
		gap: var(--spacing-3);
	}

	.colour-input {
		width: 44px;
		height: var(--spacing-ctl);
		padding: 0;
		border: 1px solid var(--color-rule-strong);
		background: none;
		cursor: pointer;
	}

	@media (max-width: 1000px) {
		.panes {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: 200px minmax(0, 1fr);
		}

		.profiles {
			border-right: 0;
			border-bottom: 1px solid var(--color-rule);
		}
	}
</style>
