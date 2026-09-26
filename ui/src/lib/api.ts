export const apiBase = __API_URL__.replace(/\/$/, '');

export type WeightTier = 'HIGH' | 'MEDIUM' | 'LOW';
export type Polarity = 'POSITIVE' | 'NEGATIVE' | 'DISQUALIFYING';
export type DecayClass = 'EVENT' | 'HIRING' | 'STRUCTURAL';
export type Band = 'PRIORITY' | 'NURTURE' | 'WATCH' | 'THIN' | 'DISQUALIFIED';
export type Verdict = 'BUY' | 'COUNTER' | 'BUILD' | 'NONE';
export type SourceKind = 'JOB_POSTING' | 'NEWS' | 'NEWSROOM' | 'STRATEGY' | 'REGISTRY' | 'FILING';

export type Chip = {
	evidenceId: string;
	questionKey: string;
	questionTitle: string;
	question: string;
	title: string | null;
	theme: string;
	tier: WeightTier;
	polarity: Polarity;
	contribution: number;
	quote: string | null;
	spanStart: number;
	spanEnd: number;
	sourceUrl: string;
	publisher: string;
};

export type BoardRow = {
	id: string;
	rank: number;
	name: string;
	domain: string | null;
	country: string | null;
	hqCity: string | null;
	industry: string | null;
	sizeBand: string | null;
	competitor: boolean;
	probability: number;
	band: Band;
	bandLabel: string;
	bandShort: string;
	gateReason: string | null;
	nats: number;
	signalCount: number;
	themeCount: number;
	evidenceCount: number;
	scannedDocuments: number;
	silentDocuments: number;
	newestSignalAt: string | null;
	newestSignalAgeDays: number | null;
	spark: number[];
	strongest: Chip | null;
	chips: Chip[];
	themes: string[];
	profiles: string[];
	engine: string | null;
	leadStatus: string | null;
	leadStatusLabel: string | null;
	lastContactedAt: string | null;
	contactCount: number;
	urgency: string;
	whyNow: string | null;
};

export type LedgerRow = {
	evidenceId: string;
	questionId: string;
	questionKey: string;
	questionTitle: string;
	questionText: string;
	theme: string;
	tier: WeightTier;
	polarity: Polarity;
	verdict: Verdict;
	confidence: number;
	spanStart: number;
	spanEnd: number;
	spanText: string | null;
	rationale: string | null;
	engine: string;
	spanVerified: boolean;
	sourceUrl: string;
	publisher: string;
	sourceKind: SourceKind;
	title: string | null;
	anchor: string;
	anchorKind: string;
	ageDays: number;
	halfLifeDays: number;
	tierNats: number;
	authority: number;
	decay: number;
	shrink: number;
	rawNats: number;
	groupRank: number;
	discount: number;
	contributionNats: number;
	themeCapped: boolean;
	blocking: boolean;
};

export type EvidenceView = {
	id: string;
	title: string | null;
	sourceUrl: string;
	publisher: string;
	sourceKind: SourceKind;
	authority: number;
	publishedAt: string | null;
	observedAt: string;
	anchorKind: string;
	charLength: number;
	contentHash: string;
	signalCount: number;
	verdict: Verdict | null;
	contribution: number | null;
	questionText: string | null;
};

export type CompanyDetail = {
	id: string;
	name: string;
	domain: string | null;
	country: string | null;
	hqCity: string | null;
	industry: string | null;
	sizeBand: string | null;
	employees: number | null;
	sizeConfidence: number | null;
	caemCode: string | null;
	competitor: boolean;
	aliases: string[];
	belowFloor: {
		evidenceId: string;
		questionKey: string;
		questionTitle: string;
		polarity: string;
		confidence: number;
		spanStart: number;
		spanEnd: number;
		spanText: string;
		rationale: string | null;
	}[];
	coverageNotes: string | null;
	serviceLine: string;
	probability: number;
	logit: number;
	nats: number;
	band: Band;
	bandLabel: string;
	gateReason: string | null;
	gateLine: string;
	baseRate: number;
	baseLogit: number;
	themeCap: number;
	priorityThreshold: number;
	nurtureThreshold: number;
	watchThreshold: number;
	signalCount: number;
	themeCount: number;
	scannedDocuments: number;
	silentDocuments: number;
	ledger: LedgerRow[];
	counterEvidence: LedgerRow[];
	themes: { name: string; total: number; capped: boolean; rows: number }[];
	profiles: string[];
	contacts: {
		id: string;
		name: string;
		role: string;
		email: string | null;
		linkedinUrl: string | null;
		status: string;
		statusLabel: string;
		contactCount: number;
		lastContactedAt: string | null;
		sourceUrl: string | null;
	}[];
	weakestLink: string | null;
	priorityHorizonDays: number | null;
	nurtureHorizonDays: number | null;
	sensitivity: Record<string, number>;
	evidence: EvidenceView[];
	computedAt: string;
	engine: string;
	whyNow: WhyNow;
	lead: LeadView | null;
	crmConfigured: boolean;
};

export type WhyNow = {
	headline: string;
	narrative: string;
	urgency: 'ACT_NOW' | 'THIS_WEEK' | 'MONITOR' | 'NONE';
	urgencyLabel: string;
	triggers: LedgerRow[];
	counters: LedgerRow[];
	daysInBand: number | null;
};

export type LeadView = {
	id: string;
	companyId: string;
	company: string;
	domain: string | null;
	serviceLine: string;
	status: string;
	statusLabel: string;
	channel: string | null;
	lastContactedAt: string | null;
	contactCount: number;
	note: string | null;
	outreachSubject: string | null;
	probability: number | null;
	band: string | null;
	crmSyncedAt: string | null;
	updatedAt: string;
};

export type LeadTally = {
	NEW: number;
	CONTACTED: number;
	REPLIED: number;
	QUALIFIED: number;
	REJECTED: number;
	TOTAL: number;
	crmConfigured: boolean;
};

export type EvidenceDetail = {
	id: string;
	key: string;
	company: string | null;
	companyId: string;
	title: string | null;
	sourceUrl: string;
	publisher: string;
	sourceKind: SourceKind;
	authority: number;
	publishedAt: string | null;
	observedAt: string;
	anchorKind: string;
	charLength: number;
	contentHash: string;
	captureId: string | null;
	text: string | null;
	signals: LedgerRow[];
};

export type Policy = {
	id: string;
	key: string;
	title: string;
	serviceLine: string;
	text: string;
	instructions: string;
	weightTier: WeightTier;
	nats: number;
	polarity: Polarity;
	theme: string;
	decayClass: DecayClass;
	halfLifeDays: number;
	cues: string[];
	counterCues: string[];
	active: boolean;
	signalCount: number;
	createdAt: string;
	updatedAt: string;
};

export type PolicyInput = {
	title?: string;
	serviceLine: string;
	text: string;
	instructions: string;
	weightTier: WeightTier;
	polarity: Polarity;
	theme: string;
	decayClass: DecayClass;
	cues?: string[];
	counterCues?: string[];
	active: boolean;
};

export type PreviewMatch = {
	companyId: string;
	company: string;
	evidence: number;
	signals: number;
	topConfidence: number;
	quote: string | null;
};

export type Preview = {
	serviceLine: string;
	matchedCompanies: number;
	matchedEvidence: number;
	scannedEvidence: number;
	tookMs: number;
	engine: string;
	matches: PreviewMatch[];
};

export type Move = {
	companyId: string;
	company: string;
	beforeRank: number;
	afterRank: number;
	delta: number;
	beforeProbability: number | null;
	afterProbability: number;
	band: Band;
	direction: 'up' | 'down' | 'flat';
};

export type Rescore = {
	serviceLine: string;
	tookMs: number;
	companies: number;
	documentsScanned: number;
	signalsProduced: number;
	engine: string;
	question: string | null;
	movedRows: number;
	changedTop: number;
	keptPrevious?: boolean;
	moves: Move[];
	board: BoardRow[];
	bands: string[];
};

export type Meta = {
	serviceLines: string[];
	defaultServiceLine: string;
	serviceLine: string;
	engine: string;
	requestedEngine: string;
	languageModelAvailable: boolean;
	localEngine: string;
	degraded: boolean;
	bands: Record<string, number>;
	stages: Record<string, number>;
	regions: RegionNode[];
	nextRefreshAt: string | null;
	lastRefreshAt: string | null;
	refreshIntervalMs: number;
	captures: number;
	scoring: {
		baseRate: number;
		baseLogit: number;
		priorityThreshold: number;
		nurtureThreshold: number;
		watchThreshold: number;
		themeCap: number;
		rankDiscount: number;
		icpMinEmployees: number;
	};
};

export type Status = {
	service: string;
	postgres: string;
	mongo: string;
	checkedAt: string;
	companies: number;
	evidence: number;
	signals: number;
	policyQuestions: number;
	captures: number;
	engine: string;
	languageModelAvailable: boolean;
	degraded: boolean;
};

export type Outreach = {
	companyId: string;
	company: string;
	channel: string;
	subject: string;
	body: string;
	citations: {
		marker: string;
		question: string;
		quote: string;
		sourceUrl: string;
		publisher: string;
	}[];
	persona: string;
	valueProposition: string;
	recipientId: string | null;
	recipientName: string | null;
	recipientEmail: string | null;
	engine: string;
};

export class ApiError extends Error {
	readonly status: number | undefined;

	constructor(message: string, status?: number) {
		super(message);
		this.name = 'ApiError';
		this.status = status;
	}
}

async function describeFailure(response: Response): Promise<string> {
	const fallback = `The API answered ${response.status}.`;
	try {
		const body = await response.json();
		return body.detail ?? body.message ?? body.title ?? fallback;
	} catch {
		return fallback;
	}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
	const headers: Record<string, string> = { accept: 'application/json' };
	if (init?.body) {
		headers['content-type'] = 'application/json';
	}

	let response: Response;
	try {
		response = await fetch(`${apiBase}${path}`, { ...init, headers });
	} catch {
		throw new ApiError("Couldn't reach the API.");
	}

	if (!response.ok) {
		throw new ApiError(await describeFailure(response), response.status);
	}
	if (response.status === 204) {
		return undefined as T;
	}
	return (await response.json()) as T;
}

function query(params: Record<string, string | number | boolean | null | undefined>): string {
	const search = new URLSearchParams();
	for (const [key, value] of Object.entries(params)) {
		if (value !== null && value !== undefined && value !== '') {
			search.set(key, String(value));
		}
	}
	const text = search.toString();
	return text ? `?${text}` : '';
}

export function fetchMeta(serviceLine?: string): Promise<Meta> {
	return request<Meta>(`/api/meta${query({ serviceLine })}`);
}

export function fetchStatus(): Promise<Status> {
	return request<Status>('/api/status');
}

export function fetchBoard(options: {
	serviceLine: string;
	filter?: string;
	sort?: string;
	q?: string;
	stage?: string;
	region?: string;
}): Promise<BoardRow[]> {
	return request<BoardRow[]>(
		`/api/companies${query({
			serviceLine: options.serviceLine,
			filter: options.filter ?? 'all',
			sort: options.sort ?? 'score',
			q: options.q,
			stage: options.stage,
			region: options.region
		})}`
	);
}

export function fetchCompany(id: string, serviceLine: string): Promise<CompanyDetail> {
	return request<CompanyDetail>(`/api/companies/${id}${query({ serviceLine })}`);
}

export function fetchEvidence(id: string): Promise<EvidenceDetail> {
	return request<EvidenceDetail>(`/api/evidence/${id}`);
}

export function fetchThemes(): Promise<string[]> {
	return request<string[]>('/api/policies/themes');
}

export type DecayOption = { name: DecayClass; halfLifeDays: number };

export function fetchDecayClasses(): Promise<DecayOption[]> {
	return request<DecayOption[]>('/api/policies/decay-classes');
}

export type PolicySet = PolicyInput & { key?: string };

export function exportPolicies(serviceLine?: string): Promise<PolicySet[]> {
	return request<PolicySet[]>(`/api/policies/export${query({ serviceLine })}`);
}

export function importPolicies(set: PolicySet[]): Promise<{ created: number; updated: number; skipped: number }> {
	return request('/api/policies/import', { method: 'POST', body: JSON.stringify(set) });
}

export function deriveCues(text: string, instructions = ''): Promise<string[]> {
	return request<string[]>(`/api/policies/derive${query({ text, instructions })}`);
}

export function fetchPolicies(serviceLine?: string): Promise<Policy[]> {
	return request<Policy[]>(`/api/policies${query({ serviceLine })}`);
}

export function createPolicy(input: PolicyInput): Promise<Policy> {
	return request<Policy>('/api/policies', { method: 'POST', body: JSON.stringify(input) });
}

export function updatePolicy(id: string, input: PolicyInput): Promise<Policy> {
	return request<Policy>(`/api/policies/${id}`, { method: 'PATCH', body: JSON.stringify(input) });
}

export function deletePolicy(id: string): Promise<void> {
	return request<void>(`/api/policies/${id}`, { method: 'DELETE' });
}

export function previewPolicy(input: {
	serviceLine: string;
	text: string;
	instructions?: string;
	tier?: WeightTier;
	polarity?: Polarity;
	theme?: string;
}): Promise<Preview> {
	return request<Preview>(
		`/api/policies/preview${query({
			serviceLine: input.serviceLine,
			text: input.text,
			instructions: input.instructions,
			tier: input.tier,
			polarity: input.polarity,
			theme: input.theme
		})}`
	);
}

export type AskFacet = {
	kind: 'country' | 'industry' | 'sizeBand' | 'minEmployees' | 'maxEmployees' | 'competitor';
	value: string;
	label: string;
	matches: number;
};

export type AskReading = {
	kind: 'FILTER' | 'SIGNAL' | 'FILTER_AND_SIGNAL';
	reading: string;
	question: string;
	facets: AskFacet[];
};

export function readAsk(question: string): Promise<AskReading> {
	return request<AskReading>(`/api/ask/read${query({ q: question })}`);
}

export type AskOutcome = AskReading & {
	serviceLine: string;
	matchedCompanies?: number;
	companyIds?: string[];
	movedRows?: number;
	changedTop?: number;
	tookMs?: number;
	documentsScanned?: number;
	signalsProduced?: number;
	answeredFromOverlay?: number;
	questionKey?: string;
	keptPrevious?: boolean;
	board?: BoardRow[];
	moves?: Move[];
};

export function ask(input: {
	serviceLine: string;
	question: string;
	engine?: Engine;
	tier?: WeightTier;
	polarity?: Polarity;
}): Promise<AskOutcome> {
	return request<AskOutcome>('/api/ask', { method: 'POST', body: JSON.stringify(input) });
}

export type Bookmark = {
	companyId: string;
	company: string;
	domain: string | null;
	country: string | null;
	serviceLine: string;
	note: string | null;
	createdAt: string;
};

export function fetchBookmarks(): Promise<Bookmark[]> {
	return request<Bookmark[]>('/api/bookmarks');
}

export function addBookmark(companyId: string, serviceLine: string, note?: string): Promise<Bookmark> {
	return request<Bookmark>(`/api/bookmarks/${companyId}`, {
		method: 'POST',
		body: JSON.stringify({ serviceLine, note: note ?? null })
	});
}

export function removeBookmark(companyId: string, serviceLine: string): Promise<void> {
	return request<void>(`/api/bookmarks/${companyId}${query({ serviceLine })}`, { method: 'DELETE' });
}

export type Engine = 'local' | 'deepseek';

export function rescore(input: {
	serviceLine: string;
	questionId?: string | null;
	question?: string | null;
	engine?: Engine | null;
	tier?: WeightTier | null;
	polarity?: Polarity | null;
	theme?: string | null;
	instructions?: string | null;
}): Promise<Rescore> {
	return request<Rescore>('/api/rescore', { method: 'POST', body: JSON.stringify(input) });
}

export function sendFeedback(input: {
	companyId: string;
	questionId?: string | null;
	repVerdict: 'ACCEPT' | 'REJECT';
	note?: string;
}): Promise<unknown> {
	return request('/api/feedback', { method: 'POST', body: JSON.stringify(input) });
}

export function fetchOutreach(companyId: string, serviceLine: string, channel: string): Promise<Outreach> {
	return request<Outreach>(
		`/api/outreach/${companyId}${query({ serviceLine, channel })}`
	);
}

export function rewriteOutreach(input: {
	companyId: string;
	serviceLine: string;
	channel: string;
	instruction: string;
}): Promise<Outreach> {
	const { companyId, ...body } = input;
	return request<Outreach>(`/api/outreach/${companyId}`, {
		method: 'POST',
		body: JSON.stringify(body)
	});
}

export function fetchLeads(serviceLine: string): Promise<LeadView[]> {
	return request<LeadView[]>(`/api/leads${query({ serviceLine })}`);
}

export function fetchLeadTally(serviceLine: string): Promise<LeadTally> {
	return request<LeadTally>(`/api/leads/tally${query({ serviceLine })}`);
}

export function recordLead(input: {
	companyId: string;
	serviceLine: string;
	status?: string | null;
	channel?: string | null;
	note?: string | null;
	outreachSubject?: string | null;
}): Promise<LeadView> {
	const { companyId, ...body } = input;
	return request<LeadView>(`/api/leads/${companyId}`, { method: 'POST', body: JSON.stringify(body) });
}

export function pushToHubspot(
	companyId: string,
	serviceLine: string
): Promise<{ pushed: boolean; queued?: boolean; reason?: string; detail?: string }> {
	return request(`/api/leads/${companyId}/hubspot`, {
		method: 'POST',
		body: JSON.stringify({ serviceLine })
	});
}

export type RegionNode = {
	key: string;
	name: string;
	children: { key: string; name: string; children: RegionNode[] }[];
};

export type RegionCountry = {
	code: string;
	regionKey: string;
	regionName: string;
	continentKey: string;
};

export function fetchRegions(): Promise<RegionNode[]> {
	return request<RegionNode[]>('/api/regions');
}

export function fetchRegionCountries(): Promise<RegionCountry[]> {
	return request<RegionCountry[]>('/api/regions/countries');
}

export type ServiceLine = {
	id: string;
	key: string;
	name: string;
	description: string | null;
	valueProposition: string | null;
	sortOrder: number;
	active: boolean;
	questionCount: number;
	createdAt: string;
};

export type ServiceLineInput = {
	name: string;
	description: string | null;
	valueProposition: string | null;
	sortOrder: number;
	active: boolean;
};

export function fetchServiceLines(includeInactive = true): Promise<ServiceLine[]> {
	return request<ServiceLine[]>(`/api/service-lines${query({ includeInactive })}`);
}

export function createServiceLine(input: ServiceLineInput): Promise<ServiceLine> {
	return request<ServiceLine>('/api/service-lines', { method: 'POST', body: JSON.stringify(input) });
}

export function updateServiceLine(id: string, input: ServiceLineInput): Promise<ServiceLine> {
	return request<ServiceLine>(`/api/service-lines/${id}`, { method: 'PATCH', body: JSON.stringify(input) });
}

export function deleteServiceLine(id: string): Promise<void> {
	return request<void>(`/api/service-lines/${id}`, { method: 'DELETE' });
}

export type Precision = {
	decisions: number;
	accepted: number;
	rejected: number;
	queuePrecision: number | null;
};

export type Backtest = {
	asOfDaysAgo: number;
	asOf: string;
	companies: number;
	withEvidence: number;
	called: number;
	control: number;
	calledConfirmed: number;
	controlConfirmed: number;
	calledRate: number | null;
	controlRate: number | null;
	lift: number | null;
	powered: boolean;
	rows: {
		company: string;
		band: string;
		probability: number;
		before: number;
		after: number;
		confirmed: boolean;
	}[];
};

export function fetchBacktest(serviceLine: string, daysAgo = 90): Promise<Backtest> {
	return request<Backtest>(`/api/backtest${query({ serviceLine, daysAgo })}`);
}

export type Telemetry = {
	checkedAt: string;
	signalsByEngine: Record<string, number>;
	coverage: {
		companies: number;
		withDocuments: number;
		withoutDocuments: number;
		documents: number;
		documentsPerCompany: number;
	};
	freshness: { newestCaptureAt: string | null; newestCaptureAgeHours: number | null };
	classificationCache: { entries: number; reused: number; byEngine: Record<string, number> };
	signalsBySource: { documents: number; questions: number; classificationPairs: number };
	overlay: {
		present: boolean;
		engine?: string;
		generatedAt?: string;
		pairsSeen?: number;
		strikeRate?: number;
		quotesChecked?: number;
		quotesStruck?: number;
		sampling?: {
			cueLivePairs: number;
			cueLiveSignals: number;
			cueLiveYield: number;
			cueSilentSampled: number;
			cueSilentSignals: number;
			cueSilentYield: number;
			sampleRate: number;
		};
	};
};

export function fetchTelemetry(): Promise<Telemetry> {
	return request<Telemetry>('/api/telemetry');
}

export type IcpProfile = {
	id: string;
	name: string;
	color: string | null;
	countries: string[];
	regions: string[];
	industries: string[];
	minEmployees: number;
	sizeBands: string[];
	excludeCompetitors: boolean;
	untouchedOnly: boolean;
	active: boolean;
	sortOrder: number;
	companies: number;
};

export type IcpProfileInput = {
	name: string;
	color: string | null;
	countries: string;
	regions: string;
	industries: string;
	minEmployees: number;
	sizeBands: string;
	excludeCompetitors: boolean;
	untouchedOnly: boolean;
	active: boolean;
};

export type IcpMatch = { companies: number; sample: string[] };

export function previewIcpProfile(
	input: Omit<IcpProfileInput, 'name' | 'color' | 'active'>
): Promise<IcpMatch> {
	return request<IcpMatch>(
		`/api/icp-profiles/preview${query({
			countries: input.countries,
			regions: input.regions,
			industries: input.industries,
			minEmployees: input.minEmployees,
			sizeBands: input.sizeBands,
			excludeCompetitors: input.excludeCompetitors,
			untouchedOnly: input.untouchedOnly
		})}`
	);
}

export function fetchIcpProfiles(): Promise<IcpProfile[]> {
	return request<IcpProfile[]>('/api/icp-profiles');
}

export function createIcpProfile(input: IcpProfileInput): Promise<IcpProfile> {
	return request<IcpProfile>('/api/icp-profiles', { method: 'POST', body: JSON.stringify(input) });
}

export function updateIcpProfile(id: string, input: IcpProfileInput): Promise<IcpProfile> {
	return request<IcpProfile>(`/api/icp-profiles/${id}`, { method: 'PUT', body: JSON.stringify(input) });
}

export function deleteIcpProfile(id: string): Promise<void> {
	return request<void>(`/api/icp-profiles/${id}`, { method: 'DELETE' });
}

export function markCompetitor(companyId: string, competitor: boolean): Promise<{ id: string; name: string; competitor: boolean }> {
	return request(`/api/companies/${companyId}/competitor`, {
		method: 'POST',
		body: JSON.stringify({ competitor })
	});
}

export type PersonSignal = {
	id: string;
	kind: string;
	strength: string;
	text: string;
	sourceUrl: string | null;
	publisher: string | null;
	publishedAt: string | null;
};

export type PersonRow = {
	id: string;
	companyId: string;
	company: string;
	domain: string | null;
	name: string;
	role: string;
	band: string;
	probability: number;
	serviceLine: string;
	sourceUrl: string;
	publisher: string;
	publishedAt: string | null;
	email: string | null;
	linkedinUrl: string | null;
	headline: string | null;
	enrichedAt: string | null;
	status: string;
	statusLabel: string;
	contactCount: number;
	lastContactedAt: string | null;
	outreachSubject: string | null;
	profiles: string[];
	signals: PersonSignal[];
};

export function movePerson(id: string, status: string, subject?: string): Promise<PersonRow> {
	return request<PersonRow>(`/api/people/${id}/stage`, {
		method: 'POST',
		body: JSON.stringify({ status, subject })
	});
}

export function setPersonEmail(id: string, email: string): Promise<PersonRow> {
	return request<PersonRow>(`/api/people/${id}/email`, {
		method: 'POST',
		body: JSON.stringify({ email })
	});
}

export type EnrichmentProvider = { provider: string; available: boolean };

export function fetchEnrichmentProvider(): Promise<EnrichmentProvider> {
	return request<EnrichmentProvider>('/api/people/provider');
}

export function enrichPeople(serviceLine: string, limit = 25): Promise<{ enriched: number; provider: string }> {
	return request(`/api/people/enrich${query({ serviceLine, limit })}`, { method: 'POST' });
}

export function fetchPeople(serviceLine: string): Promise<PersonRow[]> {
	return request<PersonRow[]>(`/api/people${query({ serviceLine })}`);
}

export function extractPeople(reset = false): Promise<{ added: number; total: number }> {
	return request<{ added: number; total: number }>(`/api/people/extract${query({ reset })}`, { method: 'POST' });
}

export type Settings = {
	blacklist: string[];
	exclusions: { id: string; name: string }[];
	icpCountries: string[];
	icpIndustries: string[];
	icpMinEmployees: number;
	geographyGate: boolean;
	sizeGate: boolean;
	competitorGate: boolean;
	industryGate: boolean;
	confidenceShrink: number;
	baseRate: number;
	priorityThreshold: number;
	nurtureThreshold: number;
	watchThreshold: number;
	rankDiscount: number;
	themeCap: number;
	voiceTone: string;
	voiceFormality: string;
	voiceLength: string;
	voiceLanguage: string;
	voiceReader: string;
	voiceAsk: string;
	voiceSignOff: string;
	voiceSignature: string;
	voiceAvoid: string;
	voiceNotes: string;
	modelWritesDrafts: boolean;
	updatedAt: string;
	inScope: Record<string, number>;
	industriesPresent: string[];
	indexedCompanies: number;
};

export function fetchSettings(): Promise<Settings> {
	return request<Settings>('/api/settings');
}

export function saveSettings(
	input: Omit<
		Settings,
		| 'themeCap'
		| 'updatedAt'
		| 'inScope'
		| 'indexedCompanies'
		| 'modelWritesDrafts'
		| 'industriesPresent'
		| 'exclusions'
		| 'blacklist'
	> & { blacklist: string }
): Promise<Settings> {
	return request<Settings>('/api/settings', {
		method: 'PUT',
		body: JSON.stringify({
			...input,
			icpCountries: input.icpCountries.join(','),
			icpIndustries: input.icpIndustries.join(',')
		})
	});
}

export function fetchPrecision(): Promise<{
	decisions: number;
	accepted: number;
	rejected: number;
	queuePrecision: number | null;
}> {
	return request('/api/feedback/precision');
}
