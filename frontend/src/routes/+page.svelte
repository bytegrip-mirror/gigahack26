<script lang="ts">
	import { onMount } from 'svelte';
	import PitchCard from '$lib/components/PitchCard.svelte';
	import {
		ApiError,
		apiBase,
		createSubmission,
		listSubmissions,
		parseTags,
		type Submission
	} from '$lib/api';

	let submissions = $state<Submission[]>([]);
	let loading = $state(true);
	let loadError = $state<string | null>(null);
	let submitError = $state<string | null>(null);
	let submitting = $state(false);
	let now = $state(Date.now());

	let author = $state('');
	let title = $state('');
	let body = $state('');
	let tags = $state('');

	const ready = $derived(author.trim().length > 0 && title.trim().length > 0);

	onMount(() => {
		load();
		const timer = setInterval(() => (now = Date.now()), 30000);
		return () => clearInterval(timer);
	});

	async function load() {
		loading = true;
		loadError = null;
		try {
			submissions = await listSubmissions();
			now = Date.now();
		} catch (error) {
			loadError =
				error instanceof ApiError ? error.message : "Couldn't load the wall.";
		} finally {
			loading = false;
		}
	}

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		submitting = true;
		submitError = null;
		try {
			const created = await createSubmission({
				author: author.trim(),
				title: title.trim(),
				body: body.trim(),
				tags: parseTags(tags)
			});
			submissions = [created, ...submissions];
			now = Date.now();
			title = '';
			body = '';
			tags = '';
		} catch (error) {
			submitError =
				error instanceof ApiError
					? `${error.message} Your draft is still in the form.`
					: 'The pitch did not post. Your draft is still in the form.';
		} finally {
			submitting = false;
		}
	}
</script>

<section class="composer">
	<div class="composer-head">
		<h1>Post what you're building</h1>
		<p>It lands on the wall below, for everyone at the venue.</p>
	</div>

	<form onsubmit={submit}>
		<div class="row">
			<div class="field name">
				<label for="author">Your name</label>
				<input
					id="author"
					bind:value={author}
					maxlength="80"
					autocomplete="name"
					placeholder="Ada"
					required
				/>
			</div>

			<div class="field grow">
				<label for="title">What you're building</label>
				<input
					id="title"
					bind:value={title}
					maxlength="160"
					placeholder="Torch relay for the demo stage"
					required
				/>
			</div>
		</div>

		<div class="field">
			<label for="body">Detail <span class="optional">optional</span></label>
			<textarea
				id="body"
				bind:value={body}
				rows="3"
				maxlength="4000"
				placeholder="What it does, what you still need, how far along you are."
			></textarea>
		</div>

		<div class="row">
			<div class="field grow">
				<label for="tags">Tags <span class="optional">optional, comma separated</span></label>
				<input id="tags" bind:value={tags} placeholder="ml, hardware, mobile" />
			</div>

			<button class="primary" type="submit" disabled={submitting || !ready}>
				{submitting ? 'Posting' : 'Post pitch'}
			</button>
		</div>

		{#if submitError}
			<div class="banner" role="alert">
				<p class="banner-title">That pitch didn't post.</p>
				<p class="banner-hint">{submitError}</p>
			</div>
		{/if}
	</form>
</section>

<section class="wall" aria-live="polite">
	<div class="wall-head">
		<h2>
			<span class="mono count">{submissions.length}</span>
			{submissions.length === 1 ? 'pitch' : 'pitches'}
		</h2>
		<button class="outline" onclick={load} disabled={loading}>Refresh</button>
	</div>

	{#if loading}
		<div class="grid" role="status">
			<span class="sr-only">Loading the wall</span>
			{#each [0, 1, 2] as placeholder (placeholder)}
				<div class="skeleton"></div>
			{/each}
		</div>
	{:else if loadError}
		<div class="banner" role="alert">
			<div>
				<p class="banner-title">Couldn't load the wall.</p>
				<p class="banner-hint">{loadError} The API answers at {apiBase}.</p>
			</div>
			<button class="outline" onclick={load}>Retry</button>
		</div>
	{:else if submissions.length === 0}
		<div class="empty">
			<p class="empty-title">Nothing on the wall yet</p>
			<p class="empty-hint">
				Post the first pitch above. The wall stays empty until someone starts.
			</p>
		</div>
	{:else}
		<div class="grid">
			{#each submissions as submission (submission.id)}
				<PitchCard {submission} {now} />
			{/each}
		</div>
	{/if}
</section>

<style>
	.composer {
		padding: 1.5rem;
		background: var(--card);
		border: 1px solid var(--border);
		border-radius: 0.875rem;
		box-shadow: var(--shadow-xs);
	}

	.composer-head {
		margin-bottom: 1.25rem;
	}

	h1 {
		font-size: 1.5rem;
		font-weight: 600;
		letter-spacing: -0.02em;
	}

	.composer-head p {
		margin-top: 0.25rem;
		font-size: 0.875rem;
		color: var(--muted-foreground);
	}

	form {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	.row {
		display: flex;
		align-items: flex-end;
		gap: 1rem;
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: 0.375rem;
		min-width: 0;
	}

	.field.name {
		flex: 0 0 14rem;
	}

	.field.grow {
		flex: 1 1 auto;
	}

	label {
		font-size: 0.8125rem;
		font-weight: 500;
	}

	.optional {
		font-weight: 400;
		color: var(--muted-foreground);
	}

	input,
	textarea {
		width: 100%;
		padding: 0 0.75rem;
		background: var(--background);
		border: 1px solid var(--input);
		border-radius: 0.5rem;
		font-size: 0.875rem;
	}

	input {
		height: 2.25rem;
	}

	textarea {
		max-width: 46rem;
		padding: 0.5rem 0.75rem;
		line-height: 1.5;
		resize: vertical;
	}

	input::placeholder,
	textarea::placeholder {
		color: var(--fg-soft);
	}

	input:focus-visible,
	textarea:focus-visible {
		outline: none;
		border-color: var(--ring);
		box-shadow: 0 0 0 3px color-mix(in oklab, var(--ring) 30%, transparent);
	}

	.primary {
		flex: none;
		height: 2.25rem;
		padding: 0 1rem;
		background: var(--primary);
		color: var(--primary-foreground);
		border: 0;
		border-radius: 0.5rem;
		box-shadow: inset 0 0 0 1px var(--primary-rim);
		font-size: 0.875rem;
		font-weight: 500;
		cursor: pointer;
		transition: background-color 150ms ease-out;
	}

	.primary:hover:not(:disabled) {
		background: var(--primary-hover);
	}

	.primary:disabled {
		opacity: 0.5;
		box-shadow: none;
		cursor: not-allowed;
	}

	.outline {
		flex: none;
		height: 2rem;
		padding: 0 0.75rem;
		background: var(--background);
		border: 1px solid var(--input);
		border-radius: 0.5rem;
		box-shadow: var(--shadow-xs);
		font-size: 0.8125rem;
		cursor: pointer;
		transition: background-color 150ms ease-out;
	}

	.outline:hover:not(:disabled) {
		background: var(--accent);
	}

	.outline:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.banner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		padding: 0.75rem 1rem;
		background: var(--destructive-subtle);
		border: 1px solid var(--destructive-border);
		border-radius: 0.5rem;
	}

	.banner-title {
		font-size: 0.875rem;
		font-weight: 500;
	}

	.banner-hint {
		margin-top: 0.125rem;
		font-size: 0.8125rem;
		color: var(--muted-foreground);
	}

	.wall {
		margin-top: 2.5rem;
	}

	.wall-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		margin-bottom: 1rem;
	}

	h2 {
		display: flex;
		align-items: baseline;
		gap: 0.5rem;
		font-size: 1rem;
		font-weight: 500;
		color: var(--muted-foreground);
	}

	.count {
		font-size: 1.25rem;
		font-weight: 500;
		color: var(--foreground);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
		gap: 0.75rem;
		align-items: start;
	}

	.skeleton {
		height: 10rem;
		background: var(--muted);
		border-radius: var(--radius);
		animation: breathe 1.6s ease-in-out infinite;
	}

	@keyframes breathe {
		50% {
			opacity: 0.55;
		}
	}

	.empty {
		padding: 3rem 1.5rem;
		text-align: center;
		border: 1px dashed var(--input);
		border-radius: 0.875rem;
	}

	.empty-title {
		font-size: 1rem;
		font-weight: 500;
	}

	.empty-hint {
		margin-top: 0.25rem;
		font-size: 0.875rem;
		color: var(--muted-foreground);
	}

	@media (max-width: 48rem) {
		.row {
			flex-direction: column;
			align-items: stretch;
		}

		.field.name {
			flex: none;
		}

		.primary {
			height: 2.5rem;
		}
	}
</style>
