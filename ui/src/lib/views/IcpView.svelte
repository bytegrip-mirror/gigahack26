<script lang="ts">
	import { fetchMeta, fetchSettings, saveSettings, type Settings } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { shortNumber } from '$lib/format';
	import Tag from '$lib/components/Tag.svelte';

	let settings = $state<Settings | null>(null);
	let draft = $state<Settings | null>(null);
	let loading = $state(true);
	let saving = $state(false);
	let countries = $state('');
	let bands = $state<Record<string, number>>({});
	let industries = $state('');

	const dirty = $derived(
		settings !== null &&
			draft !== null &&
			(JSON.stringify({ ...draft, icpCountries: [], icpIndustries: [] }) !==
				JSON.stringify({ ...settings, icpCountries: [], icpIndustries: [] }) ||
				countries !== settings.icpCountries.join(', ') ||
				industries !== settings.icpIndustries.join(', '))
	);

	const serviceLine = $derived(session.serviceLine);

	$effect(() => {
		if (!serviceLine) return;
		let cancelled = false;
		loading = true;
		Promise.all([fetchSettings(), fetchMeta(serviceLine)])
			.then(([result, meta]) => {
				bands = meta.bands;
				if (cancelled) return;
				settings = result;
				draft = { ...result };
				countries = result.icpCountries.join(', ');
				industries = result.icpIndustries.join(', ');
			})
			.catch((cause) => toasts.push((cause as Error).message, 'error'))
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

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
			bands = (await fetchMeta(serviceLine)).bands;
			countries = saved.icpCountries.join(', ');
			industries = saved.icpIndustries.join(', ');
			toasts.push('Saved. The board has been re-scored against this profile.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			saving = false;
		}
	}

	function reset() {
		if (!settings) return;
		draft = { ...settings };
		countries = settings.icpCountries.join(', ');
		industries = settings.icpIndustries.join(', ');
	}
</script>

<div class="view">
	<header class="topbar">
		<div>
			<h1 class="dsp">Ideal customer profile</h1>
			<p class="label">
				Who counts as a prospect, and where the bands sit. Changing this re-scores every stored document.
			</p>
		</div>
		<div class="row">
			{#if dirty}<button class="btn" onclick={reset}>Discard</button>{/if}
			<button class="btn btn-primary" onclick={save} disabled={saving || !dirty}>
				{#if saving}<span class="spinner"></span>{/if}
				{dirty ? 'Save and re-score' : 'Saved'}
			</button>
		</div>
	</header>

	{#if loading && !draft}
		<p class="note"><span class="spinner"></span> Loading</p>
	{:else if draft}
		<div class="panes scroll">
			<div class="form">
			<section class="block">
				<h2 class="dsp">Where we sell</h2>
				<p class="sub">
					A company outside these markets is removed from the queue with the reason shown, whatever its score.
					Leave the list empty to accept every market.
				</p>
				<label class="field">
					<span class="label">Countries</span>
					<textarea
						class="textarea"
						rows="2"
						bind:value={countries}
						placeholder="MD, RO, DE, FR"
					></textarea>
				</label>
				<div class="chips">
					{#each parse(countries).slice(0, 24) as code (code)}
						<Tag>{code}</Tag>
					{/each}
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
				<p class="sub">
					The size floor only fires on a confident headcount. An estimate below the confidence bar is downgraded
					to a soft negative rather than a rejection.
				</p>
				<div class="pair">
					<label class="field">
						<span class="label">Minimum employees</span>
						<input class="input" type="number" min="0" bind:value={draft.icpMinEmployees} />
					</label>
					<label class="field">
						<span class="label">Industries (optional)</span>
						<input class="input" bind:value={industries} placeholder="Banking, Insurance, Telecom" />
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

			<section class="block">
				<h2 class="dsp">How the bands sit</h2>
				<p class="sub">
					The base rate is the share of the pool that is worth calling. Bands are cut from the score the ledger
					produces; the theme cap follows the Priority line automatically, so one kind of evidence can reach it
					and never pass it.
				</p>
				<div class="pair">
					<label class="field">
						<span class="label">Base rate</span>
						<input class="input" type="number" step="0.01" min="0.01" max="0.9" bind:value={draft.baseRate} />
					</label>
					<label class="field">
						<span class="label">Priority at</span>
						<input
							class="input"
							type="number"
							step="0.01"
							min="0.05"
							max="0.99"
							bind:value={draft.priorityThreshold}
						/>
					</label>
					<label class="field">
						<span class="label">Nurture at</span>
						<input
							class="input"
							type="number"
							step="0.01"
							min="0.02"
							max="0.98"
							bind:value={draft.nurtureThreshold}
						/>
					</label>
					<label class="field">
						<span class="label">Watch at</span>
						<input
							class="input"
							type="number"
							step="0.01"
							min="0.01"
							max="0.97"
							bind:value={draft.watchThreshold}
						/>
					</label>
				</div>
				<p class="sub">
					Theme cap, derived: <span class="num">{draft.themeCap.toFixed(3)}</span> nats — a single theme
					saturates exactly on the Priority line.
				</p>
			</section>

			</div>

			<aside class="aside">
			<section class="block">
				<h2 class="dsp">What this profile admits</h2>
				<dl class="facts">
					<dt>Companies indexed</dt>
					<dd class="num">{shortNumber(settings?.indexedCompanies ?? 0)}</dd>
					<dt>Inside the geography</dt>
					<dd class="num">{shortNumber(settings?.inScope?.inGeography ?? 0)}</dd>
					<dt>Above the size floor, or unknown</dt>
					<dd class="num">{shortNumber(settings?.inScope?.aboveSizeFloor ?? 0)}</dd>
					<dt>Flagged as competitors</dt>
					<dd class="num">{shortNumber(settings?.inScope?.competitors ?? 0)}</dd>
				</dl>
				<p class="sub dim">Counts are from the last save.</p>
			</section>

			<section class="block">
				<h2 class="dsp">Where the queue sits now</h2>
				<dl class="facts">
					<dt>Priority</dt><dd class="num">{bands.PRIORITY ?? 0}</dd>
					<dt>Nurture</dt><dd class="num">{bands.NURTURE ?? 0}</dd>
					<dt>Watch</dt><dd class="num">{bands.WATCH ?? 0}</dd>
					<dt>Insufficient evidence</dt><dd class="num">{bands.THIN ?? 0}</dd>
					<dt>Rejected</dt><dd class="num">{bands.DISQUALIFIED ?? 0}</dd>
				</dl>
				<p class="sub dim">
					The bands move on save. The theme cap is re-derived each time, so one kind of evidence can reach the
					top band and never pass it.
				</p>
			</section>
			</aside>
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
		max-width: 62ch;
	}

	.row {
		display: flex;
		gap: 6px;
	}

	.panes {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		display: grid;
		grid-template-columns: minmax(0, 1fr) clamp(290px, 26%, 380px);
		align-content: stretch;
	}

	.form {
		display: flex;
		flex-direction: column;
	}

	.aside {
		border-left: 1px solid var(--ink);
		background: var(--paper);
		display: flex;
		flex-direction: column;
	}

	.block {
		padding: 16px 20px;
		border-bottom: 1px solid var(--rule);
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.form .block:last-child {
		border-bottom: 0;
	}

	.aside .block {
		padding: 16px;
	}

	.aside .block:last-child {
		border-bottom: 0;
	}

	@media (max-width: 1080px) {
		.panes {
			grid-template-columns: minmax(0, 1fr);
		}
		.aside {
			border-left: 0;
			border-top: 1px solid var(--ink);
		}
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
		grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
		gap: 10px;
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
		line-height: 1.4;
		color: var(--ink-2);
	}

	.check input {
		margin-top: 2px;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.facts {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 4px 10px;
		margin: 0;
		font-size: 12.5px;
	}

	.facts dt {
		color: var(--ink-3);
	}

	.facts dd {
		margin: 0;
		text-align: right;
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 18px 16px;
		font-size: 13px;
		color: var(--ink-3);
	}
</style>
