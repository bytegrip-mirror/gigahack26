<script lang="ts">
	import { goto } from '$app/navigation';
	import { fetchBoard, type BoardRow, type RegionNode } from '$lib/api';
	import { command } from '$lib/command.svelte';
	import { session } from '$lib/session.svelte';
	import { fit } from '$lib/format';
	import Dialog from '$lib/ui/Dialog.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Input from '$lib/ui/Input.svelte';
	import type { IconName } from '$lib/ui/icons';

	let {
		serviceLines = [],
		regions = []
	}: { serviceLines?: string[]; regions?: RegionNode[] } = $props();

	type Entry = {
		id: string;
		group: string;
		label: string;
		hint?: string;
		icon: IconName;
		run: () => void;
	};

	let query = $state('');
	let hits = $state<BoardRow[]>([]);
	let cursor = $state(0);
	let input = $state<HTMLInputElement | null>(null);
	let panel = $state<HTMLDivElement | null>(null);

	const flatRegions = $derived.by(() => {
		const out: { key: string; label: string }[] = [];
		for (const continent of regions) {
			out.push({ key: continent.key, label: continent.name });
			for (const child of continent.children) {
				out.push({ key: child.key, label: `${continent.name} › ${child.name}` });
			}
		}
		return out;
	});

	const entries = $derived.by(() => {
		const needle = query.trim().toLowerCase();
		const out: Entry[] = [];

		for (const hit of hits) {
			out.push({
				id: `co-${hit.id}`,
				group: 'Companies',
				label: hit.name,
				hint: `${fit(hit.probability)} · ${hit.country ?? '—'}`,
				icon: 'building',
				run: () => goto(`/?co=${hit.id}`)
			});
		}

		const views: { href: string; label: string; icon: IconName; keys: string }[] = [
			{ href: '/', label: 'Board', icon: 'board', keys: 'board companies queue' },
			{ href: '/studio', label: 'Signal Studio', icon: 'studio', keys: 'studio questions policies' },
			{ href: '/pipeline', label: 'Pipeline', icon: 'pipeline', keys: 'pipeline leads stages' },
			{ href: '/market', label: 'Market', icon: 'board', keys: 'market demand countries industries map' },
			{ href: '/settings', label: 'Settings', icon: 'settings', keys: 'settings profile voice scoring pipeline' }
		];
		for (const view of views) {
			if (needle && !`${view.label} ${view.keys}`.toLowerCase().includes(needle)) continue;
			out.push({
				id: `view-${view.href}`,
				group: 'Go to',
				label: view.label,
				icon: view.icon,
				run: () => goto(view.href)
			});
		}

		const allLines = 'all service lines';
		if (!needle || allLines.includes(needle)) {
			if (session.allLines) {
				out.push({
					id: 'line-all',
					group: 'Service lines',
					label: 'All service lines',
					hint: 'showing',
					icon: 'layers',
					run: () => session.setAll()
				});
			} else {
				out.push({
					id: 'line-all',
					group: 'Service lines',
					label: 'All service lines',
					hint: 'rank everything together',
					icon: 'layers',
					run: () => {
						session.setAll();
						goto('/');
					}
				});
			}
		}

		for (const line of serviceLines) {
			if (needle && !line.toLowerCase().includes(needle)) continue;
			if (!session.allLines && line === session.serviceLine) continue;
			out.push({
				id: `line-${line}`,
				group: 'Service lines',
				label: line,
				hint: session.allLines && line === session.serviceLine ? 'last used' : 'switch',
				icon: 'layers',
				run: () => {
					session.set(line);
					goto('/');
				}
			});
		}

		for (const region of flatRegions) {
			if (needle && !region.label.toLowerCase().includes(needle)) continue;
			if (region.key === session.region) continue;
			out.push({
				id: `region-${region.key}`,
				group: 'Regions',
				label: region.label,
				hint: 'filter the board',
				icon: 'target',
				run: () => {
					session.setRegion(region.key);
					goto('/');
				}
			});
		}

		const actions: { id: string; label: string; hint: string; icon: IconName; keys: string; run: () => void }[] = [
			{
				id: 'act-ask',
				label: 'Ask a question',
				hint: 'score the corpus against a new one',
				icon: 'studio',
				keys: 'ask question signal studio new',
				run: () => {
					sessionStorage.setItem('gigahack26.openAsk', '1');
					goto('/');
				}
			},
			{
				id: 'act-saved',
				label: 'Show only what you saved',
				hint: 'the board filtered to bookmarks',
				icon: 'star',
				keys: 'saved bookmarks starred shortlist',
				run: () => {
					sessionStorage.setItem('gigahack26.openFilter', 'saved');
					goto('/');
				}
			},
			{
				id: 'act-leads',
				label: 'Show only the ones being worked',
				hint: 'any stage past new',
				icon: 'pipeline',
				keys: 'leads worked contacted pipeline stage',
				run: () => {
					sessionStorage.setItem('gigahack26.openStage', 'worked');
					goto('/');
				}
			},
			{
				id: 'act-export',
				label: 'Export the board',
				hint: 'every visible row, with its top quote',
				icon: 'download',
				keys: 'export download csv board',
				run: () => {
					sessionStorage.setItem('gigahack26.openExport', '1');
					goto('/');
				}
			}
		];

		for (const action of actions) {
			if (needle && !`${action.label} ${action.keys}`.toLowerCase().includes(needle)) continue;
			out.push({
				id: action.id,
				group: 'Do',
				label: action.label,
				hint: action.hint,
				icon: action.icon,
				run: action.run
			});
		}

		if (!needle) {
			out.push({
				id: 'region-world',
				group: 'Regions',
				label: 'Worldwide',
				hint: 'clear the region filter',
				icon: 'target',
				run: () => {
					session.setRegion('world');
					goto('/');
				}
			});
		}

		return out;
	});

	const grouped = $derived.by(() => {
		const map = new Map<string, { entry: Entry; index: number }[]>();
		entries.forEach((entry, index) => {
			const list = map.get(entry.group) ?? [];
			list.push({ entry, index });
			map.set(entry.group, list);
		});
		return [...map.entries()];
	});

	$effect(() => {
		if (!command.open) return;
		query = '';
		hits = [];
		cursor = 0;
		requestAnimationFrame(() => input?.focus());
	});

	$effect(() => {
		const needle = query.trim();
		if (!command.open || needle.length < 2) {
			hits = [];
			return;
		}
		let cancelled = false;
		const timer = setTimeout(() => {
			fetchBoard({ serviceLine: session.serviceLine, filter: 'all', sort: 'score', q: needle })
				.then((rows) => {
					if (!cancelled) hits = rows.slice(0, 6);
				})
				.catch(() => {
					if (!cancelled) hits = [];
				});
		}, 180);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	$effect(() => {
		void entries.length;
		cursor = 0;
	});

	function run(entry: Entry | undefined) {
		if (!entry) return;
		command.hide();
		entry.run();
	}

	function move(step: number) {
		if (entries.length === 0) return;
		cursor = (cursor + step + entries.length) % entries.length;
		panel?.querySelector<HTMLElement>(`[data-index="${cursor}"]`)?.scrollIntoView({ block: 'nearest' });
	}

	function onKey(event: KeyboardEvent) {
		if (event.key === 'ArrowDown') {
			event.preventDefault();
			move(1);
		} else if (event.key === 'ArrowUp') {
			event.preventDefault();
			move(-1);
		} else if (event.key === 'Enter') {
			event.preventDefault();
			run(entries[cursor]);
		}
	}
</script>

<svelte:window
	onkeydown={(event) => {
		if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
			event.preventDefault();
			command.show();
		}
	}}
/>

<Dialog
	bind:open={command.open}
	kind="palette"
	label="Search and commands"
	closeLabel="Close search"
	onclose={() => command.hide()}
>
	{#snippet footer()}
		<span class="text-micro text-ink-3">
			<kbd class="mr-1 inline-block rounded-sm border border-rule-strong bg-surface px-1 font-ui text-micro">↑</kbd
			><kbd class="mr-1 inline-block rounded-sm border border-rule-strong bg-surface px-1 font-ui text-micro">↓</kbd
			>move</span
		>
		<span class="ml-4 text-micro text-ink-3">
			<kbd class="mr-1 inline-block rounded-sm border border-rule-strong bg-surface px-1 font-ui text-micro">↵</kbd>open</span
		>
		<span class="ml-4 text-micro text-ink-3">
			<kbd class="mr-1 inline-block rounded-sm border border-rule-strong bg-surface px-1 font-ui text-micro">esc</kbd>close</span
		>
	{/snippet}

	<div class="flex min-h-0 flex-1 flex-col" onkeydown={onKey}>
		<div class="flex h-bar flex-none items-center gap-2 border-b border-ink px-3">
			<Input
				bind:ref={input}
				bind:value={query}
				icon="search"
				class="min-w-0 flex-1"
				placeholder="Search companies, or jump to a view, service line, region"
				autocomplete="off"
				spellcheck="false"
			/>
			<IconButton icon="close" label="Close search" size="sm" onclick={() => command.hide()} />
		</div>

		<div class="scroll min-h-0 flex-1 py-1" bind:this={panel}>
			{#each grouped as [group, list] (group)}
				<p class="type-label px-3 pt-2 pb-0.5">{group}</p>
				{#each list as item (item.entry.id)}
					<button
						class={['flex w-full items-center gap-2 px-3 py-2 text-left text-md text-ink', item.index === cursor ? 'bg-sunken' : '']}
						type="button"
						data-index={item.index}
						onclick={() => run(item.entry)}
						onmousemove={() => (cursor = item.index)}
					>
						<span class="text-ink-3"><Icon name={item.entry.icon} size="md" /></span>
						<span class="min-w-0 flex-1 truncate">{item.entry.label}</span>
						{#if item.entry.hint}<span class="num flex-none text-micro text-ink-4">{item.entry.hint}</span>{/if}
					</button>
				{/each}
			{/each}
			{#if entries.length === 0}
				<p class="px-3 py-3 text-base text-ink-3">Nothing matches "{query}".</p>
			{/if}
		</div>
	</div>
</Dialog>
