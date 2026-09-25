<script lang="ts">
	import '@fontsource-variable/inter';
	import '@fontsource/ibm-plex-mono/400.css';
	import '@fontsource/ibm-plex-mono/500.css';
	import '../app.css';

	import { onMount } from 'svelte';
	import { fetchStatus, type ApiStatus } from '$lib/api';

	let { children } = $props();

	let status = $state<ApiStatus | null>(null);
	let checked = $state(false);

	onMount(() => {
		let cancelled = false;

		const load = async () => {
			try {
				const next = await fetchStatus();
				if (!cancelled) status = next;
			} catch {
				if (!cancelled) status = null;
			} finally {
				if (!cancelled) checked = true;
			}
		};

		load();
		const timer = setInterval(load, 30000);

		return () => {
			cancelled = true;
			clearInterval(timer);
		};
	});
</script>

<svelte:head>
	<title>Gigahack 26</title>
	<meta name="description" content="The pitch wall for Gigahack 26." />
</svelte:head>

<header class="topbar">
	<div class="topbar-inner">
		<div class="brand">
			<span class="mark" aria-hidden="true"></span>
			<span class="brand-name">Gigahack 26</span>
			<span class="brand-sub">Pistonpunk</span>
		</div>

		<div class="stores mono">
			{#if status}
				<span class="store">
					<span class="dot" class:up={status.postgres === 'up'}></span>
					Postgres {status.postgres}
				</span>
				<span class="store">
					<span class="dot" class:up={status.mongo === 'up'}></span>
					Mongo {status.mongo}
				</span>
			{:else if checked}
				<span class="store">
					<span class="dot down"></span>
					API unreachable
				</span>
			{:else}
				<span class="store">Checking stores</span>
			{/if}
		</div>
	</div>
</header>

<main>
	{@render children()}
</main>

<style>
	.topbar {
		background: var(--topbar);
		color: var(--topbar-foreground);
	}

	.topbar-inner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		height: 3.5rem;
		max-width: 68rem;
		margin: 0 auto;
		padding: 0 1.5rem;
	}

	.brand {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.mark {
		width: 10px;
		height: 10px;
		background: var(--primary);
		border-radius: 2px;
	}

	.brand-name {
		font-weight: 600;
		letter-spacing: -0.01em;
	}

	.brand-sub {
		font-size: 0.8125rem;
		color: color-mix(in oklab, var(--topbar-foreground) 55%, transparent);
	}

	.stores {
		display: flex;
		align-items: center;
		gap: 1rem;
		font-size: 0.75rem;
		color: color-mix(in oklab, var(--topbar-foreground) 70%, transparent);
	}

	.store {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
	}

	.dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: #fbbf24;
	}

	.dot.up {
		background: #6cc77f;
	}

	.dot.down {
		background: #f87171;
	}

	main {
		max-width: 68rem;
		margin: 0 auto;
		padding: 2rem 1.5rem 4rem;
	}

	@media (max-width: 40rem) {
		.brand-sub,
		.stores {
			display: none;
		}
	}
</style>
