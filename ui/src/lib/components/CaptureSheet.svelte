<script lang="ts">
	import { apiBase, fetchEvidence, type EvidenceDetail } from '$lib/api';
	import { date, host, plural, tier, timestamp } from '$lib/format';
	import Icon from './Icon.svelte';

	let {
		evidenceId,
		focus,
		onclose
	}: { evidenceId: string; focus: { start: number; end: number } | null; onclose: () => void } = $props();

	let detail = $state<EvidenceDetail | null>(null);
	let error = $state<string | null>(null);
	let whole = $state(false);

	const CONTEXT = 320;

	$effect(() => {
		const id = evidenceId;
		let cancelled = false;
		detail = null;
		error = null;
		fetchEvidence(id)
			.then((result) => {
				if (!cancelled) detail = result;
			})
			.catch((cause) => {
				if (!cancelled) error = cause.message;
			});
		return () => {
			cancelled = true;
		};
	});

	$effect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key === 'Escape') onclose();
		};
		window.addEventListener('keydown', onKey);
		return () => window.removeEventListener('keydown', onKey);
	});

	let pane = $state<HTMLPreElement | null>(null);

	$effect(() => {
		const node = pane;
		const highlight = capture.span;
		if (!node || !highlight) return;
		const mark = node.querySelector('mark.span');
		if (mark) {
			mark.scrollIntoView({ block: 'center' });
		}
	});

	const capture = $derived.by(() => {
		const text = detail?.text ?? '';
		if (!detail || !text) return { before: '', span: '', after: '', truncated: false, whole: true };
		if (whole) return { before: '', span: '', after: text, truncated: false, whole: true };
		if (!focus) {
			const end = Math.min(text.length, CONTEXT * 2);
			return {
				before: text.slice(0, end),
				span: '',
				after: '',
				truncated: end < text.length,
				whole: end >= text.length
			};
		}
		const start = Math.max(0, focus.start - CONTEXT);
		const end = Math.min(text.length, focus.end + CONTEXT);
		return {
			before: text.slice(start, focus.start),
			span: text.slice(focus.start, focus.end),
			after: text.slice(focus.end, end),
			truncated: end < text.length || start > 0,
			whole: start === 0 && end === text.length
		};
	});
</script>

<div
	class="scrim"
	role="presentation"
	onclick={(event) => {
		if (event.target === event.currentTarget) onclose();
	}}
>
	<div class="sheet" role="dialog" aria-modal="true" aria-label="The document">
		<header>
			<div class="head-main">
				<p class="label">The document</p>
				<h2 class="dsp">{detail?.title ?? 'Reading'}</h2>
			</div>
			<button class="icon-btn" onclick={onclose} aria-label="Close the capture" title="Close">
				<Icon name="close" size={16} />
			</button>
		</header>

		<div class="body scroll">
			{#if error}
				<p class="note warn">{error}</p>
			{:else if !detail}
				<p class="note"><span class="spinner"></span> Loading</p>
			{:else}
				<dl class="meta">
					<dt>Source</dt>
					<dd>
						<a href={detail.sourceUrl} target="_blank" rel="noreferrer noopener">{host(detail.sourceUrl)}</a>
					</dd>
					<dt>Publisher</dt>
					<dd>{detail.publisher}</dd>
					<dt>Published</dt>
					<dd>{date(detail.publishedAt)}</dd>
					<dt>Captured</dt>
					<dd class="num">{timestamp(detail.observedAt)}</dd>
					<dt>Length</dt>
					<dd class="num">{plural(detail.charLength, 'char')}</dd>
				</dl>

				{#if detail.signals.length > 0}
					<section>
						<h3 class="label">Signals in this document</h3>
						<ul class="signals">
							{#each detail.signals as signal (signal.questionId + String(signal.spanStart))}
								<li>
									<div class="sig-head">
										<span class="tag tag-wash">{tier(signal.tier)}</span>
										<span class="key" title={signal.questionKey}>{signal.questionTitle}</span>
										{#if signal.blocking}<span class="tag tag-oxblood">disqualifying</span>{/if}
									</div>
									<p class="sig-q">{signal.questionText}</p>
									{#if signal.rationale}<p class="dim sub">{signal.rationale}</p>{/if}
								</li>
							{/each}
						</ul>
					</section>
				{/if}

				<section>
					<div class="cap-head">
						<h3 class="label">Capture</h3>
						<button class="btn btn-sm" onclick={() => (whole = !whole)}>
							{whole ? 'Passage only' : `All ${detail.charLength} chars`}
						</button>
					</div>
					<pre bind:this={pane}>{#if !capture.whole && !whole && capture.before}<span class="fade">…</span>{/if}{capture.before}{#if capture.span}<mark class="span">{capture.span}</mark>{/if}{capture.after}{#if capture.truncated}<span class="fade">…</span>{/if}</pre>
					<p class="sub dim">
						{#if capture.span}
							Characters {focus?.start}–{focus?.end} of the stored capture. The quote is a slice of what the
							crawler fetched, not text a model wrote.
						{:else}
							No signal cleared the floor for this document under the current policy. It is kept so you can see
							what was read and rejected.
						{/if}
					</p>
				</section>
			{/if}
		</div>

		<footer>
			<a class="btn btn-sm" href={`${apiBase}/api/evidence/${evidenceId}/capture`} target="_blank" rel="noreferrer noopener">
				The full document
			</a>
			{#if detail}
				<a class="btn btn-sm" href={detail.sourceUrl} target="_blank" rel="noreferrer noopener">Open source</a>
			{/if}
			<button class="btn btn-ink btn-sm" onclick={onclose}>Close</button>
		</footer>
	</div>
</div>

<style>
	.scrim {
		position: fixed;
		inset: 0;
		background: rgb(18 16 13 / 0.34);
		display: flex;
		justify-content: flex-end;
		z-index: 150;
	}

	.sheet {
		width: clamp(520px, 62%, 940px);
		max-width: 100%;
		background: var(--color-surface);
		border-left: 1px solid var(--color-ink);
		display: flex;
		flex-direction: column;
		min-height: 0;
		animation: enter-right var(--ease-enter) both;
	}


	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--spacing-3);
		padding: var(--spacing-3);
		border-bottom: 1px solid var(--color-ink);
	}

	h2 {
		font-size: var(--text-xl);
		line-height: 1.25;
		margin-top: 2px;
	}

	.body {
		flex: 1;
		min-height: 0;
		padding: var(--spacing-4);
		overflow-y: auto;
	}

	footer {
		display: flex;
		justify-content: flex-end;
		gap: var(--spacing-2);
		padding: var(--spacing-2) var(--spacing-3);
		border-top: 1px solid var(--color-rule);
		background: var(--color-paper);
	}

	.note {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		font-size: var(--text-md);
		color: var(--color-ink-3);
	}

	.note.warn {
		color: var(--color-oxblood);
	}

	.meta {
		display: grid;
		grid-template-columns: 96px 1fr;
		gap: var(--spacing-1) var(--spacing-3);
		margin: 0 0 var(--spacing-4);
		font-size: var(--text-base);
	}

	.meta dt {
		color: var(--color-ink-3);
	}

	.meta dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	section + section {
		margin-top: var(--spacing-4);
		padding-top: var(--spacing-3);
		border-top: 1px solid var(--color-rule);
	}

	section h3 {
		margin-bottom: var(--spacing-2);
	}

	.signals {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-3);
	}

	.signals li {
		padding-left: var(--spacing-3);
		border-left: 2px solid var(--color-orange-line);
	}

	.sig-head {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		flex-wrap: wrap;
	}

	.sig-q {
		margin-top: var(--spacing-1);
		font-size: var(--text-base);
		line-height: 1.4;
		color: var(--color-ink-2);
	}

	.sub {
		font-size: var(--text-sm);
		line-height: 1.45;
		margin-top: 3px;
	}

	.cap-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: var(--spacing-2);
	}

	pre {
		margin: 0;
		padding: var(--spacing-3);
		background: var(--color-paper);
		border: 1px solid var(--color-rule);
		font-size: var(--text-base);
		line-height: 1.7;
		white-space: pre-wrap;
		word-break: break-word;
		max-height: 42vh;
		overflow: auto;
	}

	.fade {
		color: var(--color-ink-4);
	}</style>
