export const apiBase = __API_URL__.replace(/\/$/, '');

export type WeightTier = 'HIGH' | 'MEDIUM' | 'LOW';
export type Polarity = 'POSITIVE' | 'NEGATIVE' | 'DISQUALIFYING';
export type DecayClass = 'EVENT' | 'HIRING' | 'STRUCTURAL';
export type Band = 'PRIORITY' | 'NURTURE' | 'WATCH' | 'THIN' | 'DISQUALIFIED';
export type Verdict = 'BUY' | 'COUNTER' | 'BUILD' | 'NONE';
export type SourceKind = 'JOB_POSTING' | 'NEWS' | 'REGISTRY' | 'FILING';

export type Chip = {
	evidenceId: string;
	questionKey: string;
	question: string;
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
	strongest: Chip | null;
	chips: Chip[];
	themes: string[];
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

export type Settings = {
	icpCountries: string[];
	icpIndustries: string[];
	icpMinEmployees: number;
	geographyGate: boolean;
	sizeGate: boolean;
	competitorGate: boolean;
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
	indexedCompanies: number;
};

export function fetchSettings(): Promise<Settings> {
	return request<Settings>('/api/settings');
}

export function saveSettings(
	input: Omit<
		Settings,
		'themeCap' | 'updatedAt' | 'inScope' | 'indexedCompanies' | 'modelWritesDrafts'
	>,
	serviceLine: string
): Promise<Settings> {
	return request<Settings>(`/api/settings${query({ serviceLine })}`, {
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
