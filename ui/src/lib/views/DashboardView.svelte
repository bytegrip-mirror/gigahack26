<script lang="ts">
	import NotificationBell from '$lib/components/NotificationBell.svelte';
	import { fetchIcpProfiles, fetchPeople, fetchTelemetry, type Telemetry } from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { session } from '$lib/session.svelte';
	import { shortNumber } from '$lib/format';
	import Icon from '$lib/components/Icon.svelte';
	import Metric from '$lib/components/Metric.svelte';

	/** The desk is set up for one person; this is where it says so. */
	const USER = 'Cristian';

	let profiles = $state(0);
	let people = $state(0);
	let telemetry = $state<Telemetry | null>(null);

	const status = $derived(workspace.status);
	const bands = $derived(workspace.meta?.bands ?? {});
	const priorities = $derived(bands.PRIORITY ?? 0);
	const hot = $derived((bands.PRIORITY ?? 0) + (bands.NURTURE ?? 0));

	const coverage = $derived(telemetry?.coverage ?? null);
	const freshness = $derived(telemetry?.freshness ?? null);

	function age(hours: number | null | undefined): string {
		if (hours === null || hours === undefined) return '—';
		if (hours < 24) return `${Math.max(1, Math.round(hours))}h ago`;
		return `${Math.round(hours / 24)}d ago`;
	}

	$effect(() => {
		const line = session.serviceLine;
		fetchIcpProfiles()
			.then((list) => (profiles = list.length))
			.catch(() => (profiles = 0));
		fetchTelemetry()
			.then((result) => (telemetry = result))
			.catch(() => (telemetry = null));
		if (!line) return;
		let cancelled = false;
		fetchPeople(line)
			.then((list) => {
				if (!cancelled) people = list.length;
			})
			.catch(() => {
				if (!cancelled) people = 0;
			});
		return () => {
			cancelled = true;
		};
	});
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Welcome, {USER}</h1>
			<p class="sub">
				{session.serviceLine} · {session.region === 'world' ? 'worldwide' : session.region} · every figure below is
				read from what the desk holds, not from a summary
			</p>
		</div>
		<div class="topbar-tools">
			<a class="btn btn-primary" href="/companies">
				<Icon name="board" size={15} />
				Open the board
			</a>
			<NotificationBell />
		</div>
	</header>

	<div class="body scroll">
		<section class="panel">
			<div class="panel-head"><h2 class="dsp">What the desk holds</h2></div>
			<div class="tiles">
				<Metric
					value={shortNumber(status?.companies ?? 0)}
					label="Companies watched"
					tone="hot"
					title="Every company in the corpus, scored against this service line"
				/>
				<Metric
					value={shortNumber(status?.captures ?? 0)}
					label="Documents read"
					title="Stored pages, each re-hashable to the byte"
				/>
				<Metric
					value={shortNumber(status?.signals ?? 0)}
					label="Signals"
					title="A document that answered a question"
				/>
				<Metric value={shortNumber(profiles)} label="ICP profiles" title="Named books of business" />
			</div>
		</section>

		<section class="panel">
			<div class="panel-head"><h2 class="dsp">Where the queue stands</h2></div>
			<div class="tiles">
				<Metric value={shortNumber(priorities)} label="Priority" tone="hot" title="Above the Priority line" />
				<Metric value={shortNumber(hot)} label="Hot" title="Priority and Nurture together" />
				<Metric value={shortNumber(people)} label="People" title="Named contacts in this service line" />
				<Metric
					value={shortNumber(status?.policyQuestions ?? 0)}
					label="Signal questions"
					title="The questions every document is read against"
				/>
			</div>
		</section>

		<section class="panel">
			<div class="panel-head"><h2 class="dsp">How much of it is read</h2></div>
			<div class="tiles">
				<Metric
					value={coverage ? `${Math.round((coverage.withDocuments / Math.max(1, coverage.companies)) * 100)}%` : '—'}
					label="Companies with a document"
					title={coverage ? `${coverage.withoutDocuments} companies have nothing stored` : ''}
				/>
				<Metric
					value={coverage ? coverage.documentsPerCompany.toFixed(1) : '—'}
					label="Documents each"
				/>
				<Metric value={age(freshness?.newestCaptureAgeHours)} label="Newest capture" />
				<Metric
					value={status?.engine ?? '—'}
					label="Reading with"
					title={status?.languageModelAvailable ? 'The model is reachable' : 'The model is not configured, so reads run on the local rubric'}
				/>
			</div>
		</section>

		<section class="panel">
			<div class="panel-head"><h2 class="dsp">Where to go</h2></div>
			<div class="links">
				<a class="tile-link" href="/companies">
					<Icon name="board" size={16} />
					<span>
						<span class="link-name">Companies</span>
						<span class="sub dim">The queue, with the arithmetic behind every score</span>
					</span>
					<Icon name="chevronRight" size={14} />
				</a>
				<a class="tile-link" href="/people">
					<Icon name="building" size={16} />
					<span>
						<span class="link-name">People</span>
						<span class="sub dim">Who to ring, and what they have said lately</span>
					</span>
					<Icon name="chevronRight" size={14} />
				</a>
				<a class="tile-link" href="/icp">
					<Icon name="studio" size={16} />
					<span>
						<span class="link-name">ICP Studio</span>
						<span class="sub dim">The books of business, and what they hold</span>
					</span>
					<Icon name="chevronRight" size={14} />
				</a>
			</div>
		</section>

		{#if status && (!status.postgres || status.postgres !== 'up' || status.mongo !== 'up')}
			<p class="note warn">
				<Icon name="alert" size={14} />
				<span>A store is down — postgres {status.postgres}, mongo {status.mongo}. Figures may be stale.</span>
			</p>
		{/if}
	</div>
</div>

<style>
	.topbar .sub {
		color: var(--color-ink-3);
	}

	.body {
		flex: 1;
		min-height: 0;
		padding: var(--spacing-4);
		display: flex;
		flex-direction: column;
		gap: var(--spacing-5);
	}

	.panel {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-3);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
		gap: var(--spacing-3);
	}

	.tiles :global(.metric) {
		margin: 0;
		padding: var(--spacing-3);
		border: 1px solid var(--color-rule);
		background: var(--color-surface);
	}

	.links {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: var(--spacing-3);
	}

	.tile-link {
		display: flex;
		align-items: center;
		gap: var(--spacing-3);
		padding: var(--spacing-3) var(--spacing-4);
		border: 1px solid var(--color-rule);
		background: var(--color-surface);
		color: var(--color-ink-2);
	}

	.tile-link:hover {
		border-color: var(--color-ink);
		color: var(--color-ink);
	}

	.tile-link span {
		flex: 1;
		min-width: 0;
	}

	.link-name {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-lg);
	}

	.note {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}
</style>
