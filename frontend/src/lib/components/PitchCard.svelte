<script lang="ts">
	import { isFresh, relativeTime, type Submission } from '$lib/api';

	let { submission, now }: { submission: Submission; now: number } = $props();

	const fresh = $derived(isFresh(submission.createdAt, now));
</script>

<article class="pitch" class:fresh>
	<div class="head">
		<h3>{submission.title}</h3>
		{#if fresh}
			<span class="fresh-tag mono">just posted</span>
		{/if}
	</div>

	{#if submission.body}
		<p class="body">{submission.body}</p>
	{/if}

	{#if submission.tags.length > 0}
		<ul class="tags">
			{#each submission.tags as tag (tag)}
				<li>{tag}</li>
			{/each}
		</ul>
	{/if}

	<footer>
		<span class="author">{submission.author}</span>
		<span class="time mono">{relativeTime(submission.createdAt, now)}</span>
	</footer>
</article>

<style>
	.pitch {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		padding: 1.25rem;
		background: var(--card);
		border: 1px solid var(--border);
		border-radius: var(--radius);
		box-shadow: var(--shadow-xs);
		transition: border-color 150ms ease-out, background-color 150ms ease-out;
	}

	.pitch:hover {
		border-color: color-mix(in oklab, var(--primary) 30%, var(--border));
		background: color-mix(in oklab, var(--accent) 40%, var(--card));
	}

	.pitch.fresh::before {
		content: '';
		position: absolute;
		left: -1px;
		top: -1px;
		bottom: -1px;
		width: 3px;
		background: var(--primary);
		border-radius: var(--radius) 0 0 var(--radius);
	}

	.head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 0.75rem;
	}

	h3 {
		font-size: 1.125rem;
		font-weight: 600;
		line-height: 1.3;
		letter-spacing: -0.01em;
	}

	.fresh-tag {
		flex: none;
		padding: 0.125rem 0.5rem;
		font-size: 0.75rem;
		color: var(--lime-ink);
		background: var(--accent-strong);
		border-radius: 0.375rem;
	}

	.body {
		font-size: 0.875rem;
		color: var(--muted-foreground);
		max-width: 68ch;
	}

	.tags {
		display: flex;
		flex-wrap: wrap;
		gap: 0.375rem;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.tags li {
		padding: 0.125rem 0.5rem;
		font-size: 0.75rem;
		font-weight: 500;
		color: var(--muted-foreground);
		background: var(--muted);
		border-radius: 0.375rem;
	}

	footer {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 0.75rem;
		margin-top: auto;
		padding-top: 0.75rem;
		border-top: 1px solid var(--border);
		font-size: 0.8125rem;
	}

	.author {
		font-weight: 500;
	}

	.time {
		font-size: 0.75rem;
		color: var(--muted-foreground);
	}
</style>
