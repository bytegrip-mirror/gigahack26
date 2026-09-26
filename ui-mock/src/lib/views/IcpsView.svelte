<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import IcpEditor from '$lib/components/studio/IcpEditor.svelte';
	import ServiceEditor from '$lib/components/studio/ServiceEditor.svelte';
	import RulesEditor from '$lib/components/studio/RulesEditor.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { companies } from '$lib/mock/companies';
	import { inScope } from '$lib/mock/engine';
	import { toasts } from '$lib/toast.svelte';

	const params = $derived(page.url.searchParams);
	const onRules = $derived(params.has('rules'));
	const service = $derived(onRules ? undefined : icpStore.service(params.get('service')));
	const icp = $derived(onRules || service ? undefined : icpStore.find(params.get('icp') ?? icpStore.items[0]?.id ?? null));
	const currentService = $derived(service ?? icpStore.service(icp?.serviceId) ?? icpStore.services[0]);
	const ruleCount = $derived(icpStore.rules.positive.length + icpStore.rules.negative.length);

	const secondary = $derived<MenuItem[]>([
		{ id: 'service', label: 'New service', icon: 'plus' },
		{ id: 'reset', label: 'Undo all studio edits', icon: 'refresh', disabled: !icpStore.edited },
		...(icp ? [{ id: 'delete-icp', label: 'Delete this ICP', icon: 'trash' as const, danger: true, separatorBefore: true }] : []),
		...(service
			? [
					{
						id: 'delete-service',
						label: 'Delete this service',
						icon: 'trash' as const,
						danger: true,
						separatorBefore: true,
						disabled: icpStore.icpsOf(service.id).length > 0,
						hint: icpStore.icpsOf(service.id).length > 0 ? 'Delete its ICPs first' : undefined
					}
				]
			: [])
	]);

	function href(key: 'icp' | 'service' | 'rules', id = ''): string {
		return `${page.url.pathname}?${key}=${encodeURIComponent(id)}`;
	}

	function go(key: 'icp' | 'service' | 'rules', id = '') {
		goto(href(key, id), { noScroll: true, keepFocus: true });
	}

	function createIcp(serviceId = currentService?.id) {
		go('icp', icpStore.create(serviceId));
	}

	function fits(id: string): number {
		const item = icpStore.find(id);
		return item ? companies.filter((company) => inScope(item, company)).length : 0;
	}

	function runSecondary(id: string) {
		if (id === 'service') go('service', icpStore.createService());
		if (id === 'reset') {
			icpStore.reset();
			toasts.push('The studio is back to the demo version', 'info', 3000);
		}
		if (id === 'delete-icp' && icp) {
			const owner = icp.serviceId;
			icpStore.remove(icp.id);
			if (owner && icpStore.service(owner)) go('service', owner);
			else go('rules');
			toasts.push('ICP deleted from this session', 'info', 3000);
		}
		if (id === 'delete-service' && service) {
			icpStore.removeService(service.id);
			go('rules');
			toasts.push('Service deleted from this session', 'info', 3000);
		}
	}
</script>

<div class="relative flex min-h-0 flex-1">
	<View class="min-w-0 flex-1">
		<Topbar title="ICP studio">
			{#snippet subtitle()}What we sell, who buys it, and which signals count.{/snippet}
			{#snippet tools()}
				<Button variant="primary" icon="plus" onclick={() => createIcp()} disabled={!currentService}>New ICP</Button>
				<Menu items={secondary} onselect={runSecondary} label="More studio actions" />
			{/snippet}
		</Topbar>
		<nav class="scroll relative min-h-0 flex-1 bg-surface" aria-label="Studio contents">
			<a
				href={href('rules')}
				aria-current={onRules ? 'page' : undefined}
				class={[
					'flex items-center gap-3 border-b border-l-2 border-b-rule px-4 py-3 text-ink transition-colors hover:bg-paper hover:no-underline',
					onRules ? 'border-l-orange bg-orange-wash' : 'border-l-transparent'
				]}
			>
				<span class="text-ink-3"><Icon name="shield" size="lg" /></span>
				<span class="min-w-0 flex-1">
					<span class="block font-display text-lg leading-snug font-medium">Universal rules</span>
					<span class="block truncate type-sub">Apply to every service and ICP</span>
				</span>
				<span class="num flex-none text-base text-ink-3">{ruleCount} signals</span>
			</a>

			<div class="flex min-h-ctl items-center justify-between gap-2 border-b border-rule px-4 pt-3 pb-1">
				<h2 class="type-label">Services and their ICPs</h2>
				<Button size="sm" variant="quiet" icon="plus" onclick={() => runSecondary('service')}>Service</Button>
			</div>

			<ul class="flex flex-col">
				{#each icpStore.services as item (item.id)}
					{@const owned = icpStore.icpsOf(item.id)}
					<li class="border-b border-rule">
						<a
							href={href('service', item.id)}
							aria-current={service?.id === item.id ? 'page' : undefined}
							class={[
								'flex items-baseline gap-2 border-l-2 px-4 py-2 text-ink transition-colors hover:bg-paper hover:no-underline',
								service?.id === item.id ? 'border-orange bg-orange-wash' : 'border-transparent'
							]}
						>
							<span class="min-w-0 flex-1 truncate font-display text-lg leading-snug font-medium">{item.name || 'Unnamed service'}</span>
							<span class="num flex-none text-sm text-ink-3">{owned.length} {owned.length === 1 ? 'ICP' : 'ICPs'}</span>
						</a>
						<ul class="flex flex-col pb-1">
							{#each owned as entry (entry.id)}
								<li>
									<a
										href={href('icp', entry.id)}
										aria-current={icp?.id === entry.id ? 'page' : undefined}
										class={[
											'flex items-baseline gap-2 border-l-2 py-1 pr-4 pl-6 text-base transition-colors hover:bg-paper hover:text-ink hover:no-underline',
											icp?.id === entry.id ? 'border-orange bg-orange-wash text-ink' : 'border-transparent text-ink-2'
										]}
									>
										<span class="min-w-0 flex-1 truncate">{entry.name || 'Unnamed ICP'}</span>
										<span class="num flex-none text-sm text-ink-3" title="Signal definitions">{entry.triggers.length} sig</span>
										<span class="num flex-none text-sm text-ink-3" title="Watched companies that fit the filters">{fits(entry.id)} fit</span>
									</a>
								</li>
							{:else}
								<li class="py-1 pr-4 pl-6 type-sub">No ICP yet.</li>
							{/each}
						</ul>
					</li>
				{/each}
			</ul>

			{#if icpStore.edited}
				<p class="flex items-center gap-2 px-4 py-3 text-sm text-ink-3">
					<Icon name="alert" size="sm" /> You changed the demo studio. Edits last for this browser tab.
				</p>
			{/if}
		</nav>
	</View>

	<aside class="flex min-h-0 w-3/5 flex-none flex-col border-l border-ink bg-surface" aria-label="Studio editor">
		{#if onRules}
			<header class="flex min-h-head flex-none flex-col justify-center border-b border-ink px-4 py-3">
				<h2 class="type-display truncate text-xl leading-tight">Universal rules</h2>
				<p class="type-sub mt-0.5">Signals, evidence rules and the scale that every ICP inherits.</p>
			</header>
			<RulesEditor />
		{:else if service}
			<header class="flex min-h-head flex-none flex-col justify-center border-b border-ink px-4 py-3">
				<h2 class="type-display truncate text-xl leading-tight">{service.name || 'Unnamed service'}</h2>
				<p class="type-sub mt-0.5">A service we sell. Its ICPs say who buys it.</p>
			</header>
			{#key service.id}
				<ServiceEditor {service} hrefFor={(id) => href('icp', id)} oncreate={() => createIcp(service?.id)} />
			{/key}
		{:else if icp}
			<header class="flex min-h-head flex-none flex-col justify-center border-b border-ink px-4 py-3">
				<h2 class="type-display truncate text-xl leading-tight">{icp.name || 'Unnamed ICP'}</h2>
				<p class="type-sub mt-0.5">
					{icp.serviceLine}. Edits apply at once and last for this browser tab.
				</p>
			</header>
			{#key icp.id}
				<IcpEditor {icp} rulesHref={href('rules')} />
			{/key}
		{:else}
			<Empty>
				<Icon name="target" size="xl" />
				<p>Nothing selected. Pick a service or an ICP on the left.</p>
			</Empty>
		{/if}
	</aside>
</div>
