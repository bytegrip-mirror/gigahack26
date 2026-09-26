<script lang="ts">
	import Panel from '$lib/ui/Panel.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Fields from '$lib/ui/Fields.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Button from '$lib/ui/Button.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import SignalList from './SignalList.svelte';
	import { icpStore, toggle } from '$lib/icps.svelte';
	import { INDUSTRIES, REGIONS, REGION_OF, SIZES, companies } from '$lib/mock/companies';
	import { matches } from '$lib/mock/matches';
	import { DIMENSION_HINT, DIMENSION_LABEL, evaluate, inScope, signed } from '$lib/mock/engine';
	import { DIMENSION_ORDER, WEIGHT_LABEL } from '$lib/mock/rules';
	import type { Dimension, Icp } from '$lib/mock/types';

	type Tab = 'fit' | 'buyers' | 'signals' | 'preview';

	let { icp, rulesHref }: { icp: Icp; rulesHref: string } = $props();

	let tab = $state<Tab>('fit');
	let dimension = $state<Dimension>('NEED');

	const countriesOf = Object.entries(REGION_OF).reduce<Record<string, string[]>>((acc, [country, region]) => {
		(acc[region] ??= []).push(country);
		return acc;
	}, {});

	const serviceOptions = $derived(icpStore.services.map((service) => ({ value: service.id, label: service.name })));
	const allEurope = $derived(REGIONS.every((region) => icp.regions.includes(region)));
	const someEurope = $derived(!allEurope && REGIONS.some((region) => icp.regions.includes(region)));

	const hits = $derived.by(() => {
		const events = new Map<string, Set<string>>();
		for (const match of matches) {
			if (match.icpId !== icp.id) continue;
			events.set(match.targetId, (events.get(match.targetId) ?? new Set()).add(match.eventId));
		}
		return new Map([...events].map(([id, set]) => [id, set.size]));
	});

	const fitting = $derived(companies.filter((company) => inScope(icp, company)));
	const ranked = $derived(fitting.map((company) => evaluate(icp, company, icpStore.rules)).sort((a, b) => b.lit - a.lit || b.total - a.total));
	const silent = $derived(icp.triggers.filter((trigger) => !hits.get(trigger.id)));
	const blockers = $derived(icp.triggers.filter((trigger) => trigger.flag).length);

	const dimensionOptions = $derived(
		DIMENSION_ORDER.map((value) => ({
			value,
			label: DIMENSION_LABEL[value],
			count: icp.triggers.filter((trigger) => trigger.dimension === value).length
		}))
	);

	const tabs = $derived([
		{ value: 'fit' as Tab, label: 'Who fits' },
		{ value: 'buyers' as Tab, label: 'Who buys', count: icp.personas?.length ?? 0 },
		{ value: 'signals' as Tab, label: 'Signals', count: icp.triggers.length },
		{ value: 'preview' as Tab, label: 'Preview', count: fitting.length }
	]);

	function pickService(id: string) {
		const service = icpStore.service(id);
		if (!service) return;
		icp.serviceId = service.id;
		icp.serviceLine = service.name;
	}

	function toggleEurope() {
		icp.regions = allEurope ? [] : [...REGIONS];
	}

</script>

<Tabs bind:value={tab} {tabs} label="ICP sections" class="min-h-0 flex-1">
	{#snippet children(current)}
		<div class="scroll h-full">
			{#if current === 'fit'}
				<Panel title="Basics" heading="label">
					<Fields>
						<Field label="ICP name" for="icp-name"><Input id="icp-name" bind:value={icp.name} /></Field>
						<Field label="Service">
							<Select label="Service" options={serviceOptions} value={icp.serviceId ?? ''} placeholder="Choose a service" onchange={pickService} />
						</Field>
					</Fields>
				</Panel>
				<Panel title="Who fits" heading="label">
					<p class="type-sub">An empty group means any value.</p>
					<div class="grid grid-cols-3 gap-4 max-lg:grid-cols-1">
						<fieldset class="flex flex-col gap-1">
							<legend class="type-label mb-1">Region</legend>
							<Checkbox checked={allEurope} indeterminate={someEurope} onchange={toggleEurope}>Europe, all regions</Checkbox>
							<div class="flex flex-col gap-1 pl-5">
								{#each REGIONS as region (region)}
									<Checkbox checked={icp.regions.includes(region)} onchange={() => (icp.regions = toggle(icp.regions, region))}>
										{region}
										<span class="block text-sm text-ink-4">{(countriesOf[region] ?? []).join(', ')}</span>
									</Checkbox>
								{/each}
							</div>
						</fieldset>
						<fieldset class="flex flex-col gap-1">
							<legend class="type-label mb-1">Industry</legend>
							{#each INDUSTRIES as industry (industry)}
								<Checkbox checked={icp.industries.includes(industry)} onchange={() => (icp.industries = toggle(icp.industries, industry))}>{industry}</Checkbox>
							{/each}
						</fieldset>
						<fieldset class="flex flex-col gap-1">
							<legend class="type-label mb-1">Employees</legend>
							{#each SIZES as size (size)}
								<Checkbox checked={icp.sizes.includes(size)} onchange={() => (icp.sizes = toggle(icp.sizes, size))}>{size}</Checkbox>
							{/each}
						</fieldset>
					</div>
				</Panel>
				<Panel title="Operational complexity" heading="label">
					<Textarea
						rows={3}
						aria-label="Operational complexity"
						placeholder="Optional. What makes the work hard enough to need us, for example many countries or many manual steps."
						bind:value={icp.complexity}
					/>
				</Panel>
			{:else if current === 'buyers'}
				<Panel title="Who buys" heading="label">
					{#snippet tools()}<Button size="sm" icon="plus" onclick={() => icpStore.addPersona(icp)}>Add role</Button>{/snippet}
					<p class="type-sub">Outreach goes to people in these roles. The People view uses this list to pick contacts.</p>
					{#each icp.personas ?? [] as persona, index (persona.id)}
						<div class="flex items-start gap-2 border-t border-rule pt-2">
							<div class="flex min-w-0 flex-1 flex-col gap-1">
								<Input size="sm" aria-label={`Role ${index + 1}`} placeholder="Role title, for example Chief Operating Officer" bind:value={persona.role} oninput={() => icpStore.syncRoles(icp)} />
								<Textarea rows={2} aria-label={`Why role ${index + 1} cares`} placeholder="Why this role cares" bind:value={persona.why} />
							</div>
							<IconButton icon="trash" size="sm" tone="danger" label="Delete role" onclick={() => icpStore.removePersona(icp, persona.id)} />
						</div>
					{:else}
						<p class="type-sub">No role yet. Add the people who sign or champion this service.</p>
					{/each}
				</Panel>
			{:else if current === 'signals'}
				<Panel>
					<Segmented label="Dimension" size="sm" fill options={dimensionOptions} bind:value={dimension} />
					<p class="type-sub">
						{DIMENSION_LABEL[dimension]}: {DIMENSION_HINT[dimension]}. Every ICP also gets the
						<a href={rulesHref}>universal rules</a>.
					</p>
				</Panel>
				<Panel>
					{#key dimension}
						<SignalList
							list={icp.triggers}
							{dimension}
							direction={1}
							title="Positive"
							empty="No positive signal here. No company can move up on this dimension."
							dimensionLabel={DIMENSION_LABEL}
							{hits}
						/>
					{/key}
				</Panel>
				<Panel>
					{#key dimension}
						<SignalList
							list={icp.triggers}
							{dimension}
							direction={-1}
							title="Negative"
							empty="No negative signal here."
							dimensionLabel={DIMENSION_LABEL}
							{hits}
						/>
					{/key}
				</Panel>
			{:else}
				<Panel title="Against the demo data" heading="label">
					<div class="flex flex-wrap gap-y-3">
						<Metric value={fitting.length} label="Companies fit" hint={`of ${companies.length} watched`} />
						<Metric value={icp.triggers.length - silent.length} label="Signals with events" hint={`of ${icp.triggers.length}`} />
						<Metric value={blockers} label="Blockers" />
					</div>
				</Panel>
				<Panel title="Companies that fit the filters" heading="label">
					<ol class="flex flex-col">
						{#each ranked as lead, index (lead.id)}
							<li class="flex items-baseline gap-2 border-b border-rule py-1 text-base last:border-b-0">
								<span class="num w-row flex-none text-right text-ink-3">{index + 1}</span>
								<span class="min-w-0 flex-1 truncate">{lead.company.name}</span>
								{#if lead.flags.length > 0}<Tag tone="oxblood" flag>Blocked</Tag>{/if}
								<span class="num flex-none text-ink-3">{lead.lit} of 5 lit</span>
								<span class="num w-row flex-none text-right font-medium">{signed(lead.total)}</span>
							</li>
						{:else}
							<li class="type-sub">No watched company fits these filters.</li>
						{/each}
					</ol>
				</Panel>
				<Panel title="Signals with no matching event" heading="label">
					<p class="type-sub">These signals never fired on the demo data. Check the rubric, or keep them for companies not yet watched.</p>
					<ul class="flex flex-col">
						{#each silent as trigger (trigger.id)}
							<li class="flex items-baseline gap-2 border-b border-rule py-1 text-base last:border-b-0">
								<span class="w-1/5 flex-none type-label">{DIMENSION_LABEL[trigger.dimension]}</span>
								<span class="min-w-0 flex-1 truncate">{trigger.text || 'Unnamed signal'}</span>
								<Tag tone="bare">{WEIGHT_LABEL[trigger.tier]}</Tag>
							</li>
						{:else}
							<li class="type-sub">Every signal has at least one matching event.</li>
						{/each}
					</ul>
				</Panel>
			{/if}
		</div>
	{/snippet}
</Tabs>
