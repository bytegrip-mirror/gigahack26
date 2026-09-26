<script lang="ts">
	import { goto } from '$app/navigation';
	import { fetchBoard, type BoardRow, type RegionNode } from '$lib/api';
	import { command } from '$lib/command.svelte';
	import { session } from '$lib/session.svelte';
	import { fit } from '$lib/format';
	import Icon from './Icon.svelte';
	import type { IconName } from './icons';

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
				hint: `Fit ${fit(hit.probability)} · ${hit.country ?? '—'}`,
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
					hint: 'showing every line',
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
				hint: session.allLines && line === session.serviceLine ? 'last used' : 'switch to this line',
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
				id: 'act-saved',
				label: 'Show only what you saved',
				hint: 'the board filtered to what you saved',
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
				hint: 'everything you have started on',
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
		if (event.key === 'Escape') {
			event.preventDefault();
			command.hide();
		} else if (event.key === 'ArrowDown') {
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

{#if command.open}
	<div
		class="scrim"
		role="presentation"
		onclick={(event) => {
			if (event.target === event.currentTarget) command.hide();
		}}
	>
		<div
			class="palette"
			role="dialog"
			aria-modal="true"
			aria-label="Search and commands"
			tabindex="-1"
			onkeydown={onKey}
		>
			<div class="field-row">
				<Icon name="search" size={16} />
				<input
					bind:this={input}
					class="field-input"
					placeholder="Search companies, or jump to a view, service line, region"
					bind:value={query}
					autocomplete="off"
					spellcheck="false"
				/>
				<button class="icon-btn icon-btn-sm" type="button" aria-label="Close search" onclick={() => command.hide()}>
					<Icon name="close" size={14} />
				</button>
			</div>

			<div class="results" bind:this={panel}>
				{#each grouped as [group, list] (group)}
					<p class="menu-label">{group}</p>
					{#each list as item (item.entry.id)}
						<button
							class="result"
							type="button"
							data-index={item.index}
							data-active={item.index === cursor ? 'true' : undefined}
							onclick={() => run(item.entry)}
							onmousemove={() => (cursor = item.index)}
						>
							<Icon name={item.entry.icon} size={15} />
							<span class="result-label">{item.entry.label}</span>
							{#if item.entry.hint}<span class="hint num">{item.entry.hint}</span>{/if}
						</button>
					{/each}
				{/each}
				{#if entries.length === 0}
					<p class="none">Nothing matches “{query}”.</p>
				{/if}
			</div>

			<div class="legend">
				<span><kbd>↑</kbd><kbd>↓</kbd> move</span>
				<span><kbd>↵</kbd> open</span>
				<span><kbd>esc</kbd> close</span>
			</div>
		</div>
	</div>
{/if}

<style>
	.scrim {
		position: fixed;
		inset: 0;
		background: rgb(0 0 0 / 0.34);
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 12vh var(--spacing-4) var(--spacing-4);
		z-index: var(--z-index-sheet);
	}

	.palette {
		animation: rise var(--ease-enter) both;
		width: min(620px, 100%);
		background: var(--color-surface);
		border: 1px solid var(--color-ink);
		display: flex;
		flex-direction: column;
		max-height: 70vh;
		min-height: 0;
	}

	.field-row {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: 0 var(--spacing-3);
		height: 46px;
		border-bottom: 1px solid var(--color-ink);
		color: var(--color-ink-3);
		flex: none;
	}

	.field-input {
		flex: 1;
		min-width: 0;
		border: 0;
		outline: none;
		background: transparent;
		font-size: var(--text-lg);
	}

	.field-input::placeholder {
		color: var(--color-ink-4);
	}

	.results {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: var(--spacing-1) 0;
	}

	.menu-label {
		padding: var(--spacing-2) var(--spacing-3) 3px;
		font-size: var(--text-micro);
		font-weight: 700;
		color: var(--color-ink-4);
	}

	.result {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		width: 100%;
		padding: 6px var(--spacing-3);
		text-align: left;
		font-size: var(--text-md);
		color: var(--color-ink);
	}

	.result :global(.icon) {
		color: var(--color-ink-3);
	}

	.result[data-active='true'] {
		background: var(--color-sunken);
	}

	.result-label {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.hint {
		font-size: var(--text-micro);
		color: var(--color-ink-4);
		flex: none;
	}

	.none {
		padding: var(--spacing-3);
		font-size: var(--text-base);
		color: var(--color-ink-3);
	}

	.legend {
		display: flex;
		gap: var(--spacing-4);
		padding: var(--spacing-2) var(--spacing-3);
		border-top: 1px solid var(--color-rule);
		background: var(--color-paper);
		font-size: var(--text-micro);
		color: var(--color-ink-3);
		flex: none;
	}

	kbd {
		display: inline-block;
		border: 1px solid var(--color-rule-strong);
		border-radius: var(--radius);
		padding: 0 4px;
		margin-right: 3px;
		font-family: var(--font-ui);
		font-size: var(--text-micro);
		background: var(--color-surface);
	}</style>
