<script lang="ts">
	import { fetchEvidence, type EvidenceDetail, type LedgerRow } from '$lib/api';
	import { date, timestamp, nats, odds, confidence, tier, sourceKind, host, plural } from '$lib/format';
	import Modal from './Modal.svelte';

	let {
		evidenceId,
		focus,
		onclose
	}: { evidenceId: string; focus: { start: number; end: number } | null; onclose: () => void } = $props();

	let detail = $state<EvidenceDetail | null>(null);
	let error = $state<string | null>(null);
	let showFullCapture = $state(false);

	const CONTEXT = 900;

	$effect(() => {
		let cancelled = false;
		detail = null;
		error = null;
		fetchEvidence(evidenceId)
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

	type Capture = {
		before: string;
		span: string;
		after: string;
		start: number;
		end: number;
		whole: boolean;
		truncatedEnd: boolean;
	};

	const EMPTY: Capture = {
		before: '',
		span: '',
		after: '',
		start: 0,
		end: 0,
		whole: true,
		truncatedEnd: false
	};

	const capture = $derived.by((): Capture => {
		const text = detail?.text ?? '';
		if (!detail || !text) return EMPTY;
		if (showFullCapture) {
			return { before: '', span: '', after: text, start: 0, end: text.length, whole: true, truncatedEnd: false };
		}
		if (!focus) {
			const end = Math.min(text.length, CONTEXT * 2);
			return {
				before: text.slice(0, end),
				span: '',
				after: '',
				start: 0,
				end,
				whole: end >= text.length,
				truncatedEnd: end < text.length
			};
		}
		const start = Math.max(0, focus.start - CONTEXT);
		const end = Math.min(text.length, focus.end + CONTEXT);
		return {
			before: text.slice(start, focus.start),
			span: text.slice(focus.start, focus.end),
			after: text.slice(focus.end, end),
			start,
			end,
			whole: start === 0 && end === text.length,
			truncatedEnd: end < text.length
		};
	});

	function copySpan() {
		if (navigator.clipboard && capture.span) {
			navigator.clipboard.writeText(capture.span);
		}
	}
</script>

<Modal
	title={detail?.title ?? 'Evidence'}
	subtitle={detail ? `${detail.publisher} · ${sourceKind(detail.sourceKind)} · ${plural(detail.charLength, 'character')}` : 'Loading the stored capture…'}
	wide
	{onclose}
>
	{#if error}
		<p class="card" style="padding:16px; border-color: var(--danger-line); color: var(--danger)">{error}</p>
	{:else if !detail}
		<div class="row gap-8"><span class="spinner"></span><span class="muted">Reading the capture…</span></div>
	{:else}
		<div class="meta">
			<dl>
				<dt>Source</dt>
				<dd>
					<a href={detail.sourceUrl} target="_blank" rel="noreferrer noopener">{host(detail.sourceUrl)} ↗</a>
				</dd>
				<dt>Authority</dt>
				<dd class="mono">×{detail.authority.toFixed(2)}</dd>
				<dt>Published</dt>
				<dd>{date(detail.publishedAt)} <span class="tiny muted">({detail.anchorKind === 'published' ? 'used for decay' : 'no date — fetch time used'})</span></dd>
				<dt>Captured</dt>
				<dd class="mono tiny">{timestamp(detail.observedAt)}</dd>
				<dt>Snapshot hash</dt>
				<dd class="mono tiny">{detail.contentHash ? detail.contentHash.slice(0, 26) : '—'}…</dd>
			</dl>
		</div>

		{#if detail.signals.length > 0}
			<section class="signals">
				<h3>Signals found in this document</h3>
				{#each detail.signals as signal (signal.questionId + String(signal.spanStart))}
					<div class="signal-row">
						<div class="row gap-6" style="flex-wrap: wrap">
							<span class="chip chip-orange">{tier(signal.tier)}</span>
							<span class="chip">{signal.questionKey}</span>
							{#if signal.blocking}
								<span class="chip chip-danger">disqualifying</span>
							{/if}
							<span class="tiny mono muted">
								confidence {confidence(signal.confidence)} → shrink {signal.shrink >= 0 ? '+' : ''}{signal.shrink.toFixed(2)} ·
								chars {signal.spanStart}–{signal.spanEnd} of {detail.charLength}
							</span>
						</div>
						<p class="question">{signal.questionText}</p>
						{#if signal.rationale}<p class="tiny muted">{signal.rationale}</p>{/if}
					</div>
				{/each}
			</section>
		{/if}

		<section class="capture">
			<div class="row" style="justify-content: space-between; margin-bottom: 8px">
				<h3>Stored capture</h3>
				<div class="row gap-8">
					{#if capture.span}
						<button class="btn tiny" onclick={copySpan}>Copy the quoted passage</button>
					{/if}
					<button class="btn tiny" onclick={() => (showFullCapture = !showFullCapture)}>
						{showFullCapture ? 'Show the passage only' : `Show all ${detail.charLength} characters`}
					</button>
				</div>
			</div>

			<pre class="text scroll-thin">{#if capture.start > 0}<span class="fade">…</span>{/if}{capture.before}{#if capture.span}<mark class="span">{capture.span}</mark>{/if}{capture.after}{#if capture.truncatedEnd}<span class="fade">…</span>{/if}</pre>

			<p class="tiny muted" style="margin-top: 8px">
				{#if capture.span}
					The highlighted passage is a byte range of this capture — characters {focus?.start}–{focus?.end}.
					Nothing on this card is generated: the quote is a slice of a document the crawler stored, and its
					hash is shown above so you can tell whether the live page still matches.
				{:else}
					No signal was extracted from this document for the current policy. It is kept so you can see what
					was read and rejected.
				{/if}
			</p>
		</section>
	{/if}
</Modal>

{#if detail}
	<div class="actions">
		<a class="btn" href={detail.sourceUrl} target="_blank" rel="noreferrer noopener">Open source ↗</a>
	</div>
{/if}

<style>
	.meta dl {
		display: grid;
		grid-template-columns: 130px 1fr;
		gap: 4px 14px;
		margin: 0 0 18px;
		font-size: 13px;
	}

	.meta dt {
		color: var(--ink-3);
	}

	.meta dd {
		margin: 0;
	}

	section + section {
		margin-top: 20px;
		padding-top: 18px;
		border-top: 1px solid var(--line);
	}

	.signals h3,
	.capture h3 {
		margin-bottom: 10px;
	}

	.signal-row {
		border-left: 2px solid var(--orange-200);
		padding-left: 12px;
		margin-bottom: 12px;
	}

	.question {
		font-size: 13.5px;
		margin-top: 5px;
		color: var(--ink-2);
	}

	.text {
		background: var(--surface-sunken);
		border: 1px solid var(--line);
		border-radius: var(--radius);
		padding: 14px;
		font-family: var(--font-mono);
		font-size: 12.5px;
		line-height: 1.7;
		white-space: pre-wrap;
		word-break: break-word;
		max-height: 44vh;
		overflow: auto;
		margin: 0;
	}

	.fade {
		color: var(--ink-4);
	}

	.actions {
		margin-top: 10px;
		display: flex;
		justify-content: flex-end;
	}
</style>
