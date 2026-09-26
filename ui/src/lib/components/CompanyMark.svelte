<script lang="ts">
	import logos from '$lib/logos.json';

	let {
		name,
		domain,
		size = 'sm'
	}: { name: string; domain: string | null; size?: 'sm' | 'md' | 'lg' } = $props();

	const files = logos as Record<string, string>;

	const file = $derived(domain ? files[domain.toLowerCase()] : undefined);
	const initial = $derived((name.trim()[0] ?? '?').toUpperCase());
</script>

{#if file}
	<img class="co-mark" data-size={size} src={`/logos/${file}`} alt="" loading="lazy" decoding="async" />
{:else}
	<span class="co-mark co-mark-blank" data-size={size} aria-hidden="true">{initial}</span>
{/if}

<style>
	.co-mark {
		display: block;
		flex: none;
		object-fit: contain;
		background: var(--color-surface);
		border: 1px solid var(--color-rule);
		border-radius: var(--radius);
	}

	.co-mark[data-size='sm'] {
		width: 20px;
		height: 20px;
	}

	.co-mark[data-size='md'] {
		width: 26px;
		height: 26px;
	}

	.co-mark[data-size='lg'] {
		width: 34px;
		height: 34px;
	}

	.co-mark-blank {
		display: flex;
		align-items: center;
		justify-content: center;
		background: var(--color-sunken);
		border-color: transparent;
		color: var(--color-ink-3);
		font-family: var(--font-display);
		font-weight: 700;
		line-height: 1;
	}

	.co-mark-blank[data-size='sm'] {
		font-size: var(--text-micro);
	}

	.co-mark-blank[data-size='md'] {
		font-size: var(--text-base);
	}

	.co-mark-blank[data-size='lg'] {
		font-size: var(--text-lg);
	}
</style>
