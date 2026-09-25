<script lang="ts">
	import { apiBase, fetchEvidence, type EvidenceDetail } from '$lib/api';
	import { confidence, date, host, plural, sourceKind, tier, timestamp } from '$lib/format';

	let {
		evidenceId,
		focus,
		onclose
	}: { evidenceId: string; focus: { start: number; end: number } | null; onclose: () => void } = $props();

	let detail = $state<EvidenceDetail | null>(null);
	let error = $state<string | null>(null);
	let whole = $state(false);

	const CONTEXT = 900;

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
	<div class="sheet" role="dialog" aria-modal="true" aria-label="Stored capture">
		<header>
			<div class="head-main">
				<p class="label">Stored capture</p>
				<h2 class="dsp">{detail?.title ?? 'Reading'}</h2>
			</div>
			<button class="btn btn-quiet btn-sm" onclick={onclose} aria-label="Close">✕</button>
		</header>

		<div class="body scroll">
			{#if error}
				<p class="note warn">{error}</p>
			{:else if !detail}
				<p class="note"><span class="spinner"></span> Loading</p>
			{:else}
				<dl class="meta">
					<dt>Source</dt>
					<dd><a href={detail.sourceUrl} target="_blank" rel="noreferrer noopener">{host(detail.sourceUrl)}</a></dd>
					<dt>Authority</dt>
					<dd class="num">×{detail.authority.toFixed(2)}</dd>
					<dt>Published</dt>
					<dd>
						{date(detail.publishedAt)}
						<span class="dim">· {detail.anchorKind === 'published' ? 'drives decay' : 'undated, fetch time used'}</span>
					</dd>
					<dt>Captured</dt>
					<dd class="num">{timestamp(detail.observedAt)}</dd>
					<dt>Hash</dt>
					<dd class="num dim">{detail.contentHash ? detail.contentHash.slice(0, 30) : '—'}</dd>
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
										<span class="mono qkey">{signal.questionKey}</span>
										{#if signal.blocking}<span class="tag tag-oxblood">disqualifying</span>{/if}
										<span class="num dim">c {confidence(signal.confidence)} · chars {signal.spanStart}–{signal.spanEnd}</span>
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
					<pre>{#if !capture.whole && !whole && capture.before}<span class="fade">…</span>{/if}{capture.before}{#if capture.span}<mark class="span">{capture.span}</mark>{/if}{capture.after}{#if capture.truncated}<span class="fade">…</span>{/if}</pre>
					<p class="sub dim">
						{#if capture.span}
							Highlighted text is a byte range of this capture — characters {focus?.start}–{focus?.end}. The
							quote is a slice of what the crawler stored, not text a model wrote.
						{:else}
							No signal cleared the floor for this document under the current policy. It is kept so you can
							see what was read and rejected.
						{/if}
					</p>
				</section>
			{/if}
		</div>

		<footer>
			<a class="btn btn-sm" href={`${apiBase}/api/evidence/${evidenceId}/capture`} target="_blank" rel="noreferrer noopener">
				Raw capture
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
		background: var(--surface);
		border-left: 1px solid var(--ink);
		display: flex;
		flex-direction: column;
		min-height: 0;
		animation: slide 160ms cubic-bezier(0.2, 0, 0.2, 1);
	}

	@keyframes slide {
		from {
			transform: translateX(14px);
			opacity: 0.6;
		}
	}

	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 12px;
		padding: 12px 15px;
		border-bottom: 1px solid var(--ink);
	}

	h2 {
		font-size: 18px;
		line-height: 1.25;
		margin-top: 2px;
	}

	.body {
		flex: 1;
		min-height: 0;
		padding: 14px 15px 20px;
		overflow-y: auto;
	}

	footer {
		display: flex;
		justify-content: flex-end;
		gap: 6px;
		padding: 10px 15px;
		border-top: 1px solid var(--rule);
		background: var(--paper);
	}

	.note {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 13px;
		color: var(--ink-3);
	}

	.note.warn {
		color: var(--oxblood);
	}

	.meta {
		display: grid;
		grid-template-columns: 96px 1fr;
		gap: 4px 12px;
		margin: 0 0 16px;
		font-size: 12.5px;
	}

	.meta dt {
		color: var(--ink-3);
	}

	.meta dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	section + section {
		margin-top: 16px;
		padding-top: 14px;
		border-top: 1px solid var(--rule);
	}

	section h3 {
		margin-bottom: 8px;
	}

	.signals {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.signals li {
		padding-left: 10px;
		border-left: 2px solid var(--orange-line);
	}

	.sig-head {
		display: flex;
		align-items: center;
		gap: 7px;
		flex-wrap: wrap;
	}

	.qkey {
		font-size: 11px;
		color: var(--orange-ink);
	}

	.sig-head .num {
		font-size: 10.5px;
	}

	.sig-q {
		margin-top: 4px;
		font-size: 12.5px;
		line-height: 1.4;
		color: var(--ink-2);
	}

	.sub {
		font-size: 11.5px;
		line-height: 1.45;
		margin-top: 3px;
	}

	.cap-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 8px;
	}

	pre {
		margin: 0;
		padding: 12px;
		background: var(--paper);
		border: 1px solid var(--rule);
		font-family: var(--font-mono);
		font-size: 12px;
		line-height: 1.7;
		white-space: pre-wrap;
		word-break: break-word;
		max-height: 42vh;
		overflow: auto;
	}

	.fade {
		color: var(--ink-4);
	}
</style>
