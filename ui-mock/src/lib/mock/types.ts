export type EventType =
	| 'COST_PROGRAM'
	| 'RESTRUCTURING'
	| 'EXECUTIVE_CHANGE'
	| 'HIRING_SURGE'
	| 'HIRING_FREEZE'
	| 'VENDOR_CONTRACT'
	| 'PARTNERSHIP'
	| 'TECH_ADOPTION'
	| 'IN_HOUSE_BUILD'
	| 'FINANCIAL_RESULTS'
	| 'M_AND_A'
	| 'COMPLIANCE'
	| 'SECURITY_INCIDENT'
	| 'STRATEGY'
	| 'EXECUTIVE_STATEMENT';

export type SourceKind = 'news' | 'jobs' | 'report' | 'website';

export type Company = {
	id: string;
	name: string;
	domain: string;
	country: string;
	city: string;
	industry: string;
	size: string;
	competitor: boolean;
};

export type Watcher = {
	companyId: string;
	sources: SourceKind[];
	lastChecked: string;
	nextCheck: string;
	status: 'ok' | 'slow' | 'failing';
};

export type Document = {
	id: string;
	companyId: string;
	title: string;
	publisher: string;
	url: string;
	kind: SourceKind;
	publishedAt: string | null;
	capturedAt: string;
	hash: string;
	text: string;
};

export type CompanyEvent = {
	id: string;
	companyId: string;
	type: EventType;
	date: string;
	summary: string;
	documentId: string;
	quote: string;
};

export type Dimension = 'NEED' | 'BUDGET' | 'TIMING' | 'ACCESS' | 'OPENNESS';

export type Direction = 1 | -1;

export type Tier = 3 | 2 | 1;

export type Trigger = {
	id: string;
	text: string;
	dimension: Dimension;
	direction: Direction;
	tier: Tier;
	flag: boolean;
	eventTypes: EventType[];
	rubric?: Rubric;
	fade?: Fade;
	halfLifeMonths: number;
};

export type Fade = 'EVENT' | 'PROGRAM' | 'STRUCTURAL';

export type Rubric = {
	counts: string;
	doesNotCount: string;
	countsExample: string;
	failsExample: string;
};

export type Persona = {
	id: string;
	role: string;
	why: string;
};

export type Service = {
	id: string;
	name: string;
	description: string;
	outcome: string;
};

export type EvidenceRule = {
	id: string;
	name: string;
	rule: string;
	pass: string;
	fail: string;
};

export type ScaleStep = {
	id: string;
	level: string;
	meaning: string;
};

export type UniversalRules = {
	positive: Trigger[];
	negative: Trigger[];
	evidence: EvidenceRule[];
	scale: ScaleStep[];
	lit: Record<Dimension, number>;
	halfLife: Record<Fade, number>;
};

export type Icp = {
	id: string;
	name: string;
	serviceLine: string;
	regions: string[];
	industries: string[];
	sizes: string[];
	triggers: Trigger[];
	serviceId?: string;
	complexity?: string;
	personas?: Persona[];
	targetRoles: string[];
};

export type Term = {
	basis: string;
	endsAt?: string;
};

export type Match = {
	icpId: string;
	companyId: string;
	eventId: string;
	targetId: string;
	why: string;
	confidence: number;
	term?: Term;
};

export type { Person } from './people.generated';
