<script lang="ts">
	import NotificationBell from '$lib/components/NotificationBell.svelte';
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

	type Draft = {
		id: string | null;
		name: string;
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
			<NotificationBell />
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

<style>
	.topbar .sub {
		color: var(--color-ink-3);
	}

	.toolbar .spacer,
	.editor-head .spacer,
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
	/*
	 * One column, not as many panels as will fit across the pane. A profile is
	 * filled in in the order the questions are asked — who it is, where, how big,
	 * what to keep out — and a panel sitting beside another reads as a second
	 * answer to the same question rather than as the next one. The body scrolls
	 * for the same reason: the form is longer than the pane now.
	 */
	.editor-body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: var(--spacing-4);
		display: flex;
		flex-direction: column;
		gap: var(--spacing-3);
	}

	.panel {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
		/* The same measure the settings form uses. A text field the width of the
		   pane is a field nobody can read a value back from. */
		max-width: 900px;
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

	.editor-foot {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-3) var(--spacing-4);
		border-top: 1px solid var(--color-rule);
		flex: none;
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
