import type { Company, EventType, SourceKind, Watcher } from './types';

export const companies: Company[] = [
	{ id: 'lufthansa', name: 'Lufthansa Group', domain: 'lufthansa.com', country: 'DE', city: 'Cologne', industry: 'Airlines', size: '10001+', competitor: false },
	{ id: 'dhl', name: 'DHL Group', domain: 'dhl.com', country: 'DE', city: 'Bonn', industry: 'Logistics', size: '10001+', competitor: false },
	{ id: 'gsk', name: 'GSK', domain: 'gsk.com', country: 'GB', city: 'London', industry: 'Pharmaceuticals', size: '10001+', competitor: false },
	{ id: 'stellantis', name: 'Stellantis', domain: 'stellantis.com', country: 'NL', city: 'Hoofddorp', industry: 'Automotive', size: '10001+', competitor: false },
	{ id: 'bnp', name: 'BNP Paribas', domain: 'bnpparibas.com', country: 'FR', city: 'Paris', industry: 'Banking', size: '10001+', competitor: false },
	{ id: 'capgemini', name: 'Capgemini', domain: 'capgemini.com', country: 'FR', city: 'Paris', industry: 'IT Services', size: '10001+', competitor: true },
	{ id: 'valmet', name: 'Valmet', domain: 'valmet.com', country: 'FI', city: 'Espoo', industry: 'Industrial Machinery', size: '10001+', competitor: false },
	{ id: 'bpost', name: 'bpost', domain: 'bpost.be', country: 'BE', city: 'Brussels', industry: 'Postal', size: '10001+', competitor: false },
	{ id: 'thyssenkrupp', name: 'thyssenkrupp', domain: 'thyssenkrupp.com', country: 'DE', city: 'Essen', industry: 'Industrial Conglomerate', size: '10001+', competitor: false }
];

export const watchers: Watcher[] = [
	{ companyId: 'lufthansa', sources: ['news', 'jobs', 'report', 'website'], lastChecked: '2026-09-26T06:40:00Z', nextCheck: '2026-09-27T06:40:00Z', status: 'ok' },
	{ companyId: 'dhl', sources: ['news', 'jobs', 'report', 'website'], lastChecked: '2026-09-26T07:20:00Z', nextCheck: '2026-09-27T07:20:00Z', status: 'ok' },
	{ companyId: 'gsk', sources: ['news', 'report', 'website'], lastChecked: '2026-09-26T07:27:00Z', nextCheck: '2026-09-27T07:27:00Z', status: 'ok' },
	{ companyId: 'stellantis', sources: ['news', 'jobs'], lastChecked: '2026-09-25T18:31:00Z', nextCheck: '2026-09-26T18:31:00Z', status: 'ok' },
	{ companyId: 'bnp', sources: ['news', 'report'], lastChecked: '2026-09-25T18:31:00Z', nextCheck: '2026-09-26T18:31:00Z', status: 'ok' },
	{ companyId: 'capgemini', sources: ['news'], lastChecked: '2026-09-25T18:31:00Z', nextCheck: '2026-10-02T18:31:00Z', status: 'ok' },
	{ companyId: 'valmet', sources: ['news', 'report', 'website'], lastChecked: '2026-09-25T18:31:00Z', nextCheck: '2026-09-26T18:31:00Z', status: 'slow' },
	{ companyId: 'bpost', sources: ['news', 'website'], lastChecked: '2026-09-25T18:31:00Z', nextCheck: '2026-09-26T18:31:00Z', status: 'ok' },
	{ companyId: 'thyssenkrupp', sources: ['news', 'jobs', 'report'], lastChecked: '2026-09-24T02:10:00Z', nextCheck: '2026-09-25T02:10:00Z', status: 'failing' }
];

export const EVENT_LABEL: Record<EventType, string> = {
	COST_PROGRAM: 'Cost program',
	RESTRUCTURING: 'Restructuring',
	EXECUTIVE_CHANGE: 'Executive change',
	HIRING_SURGE: 'Hiring surge',
	HIRING_FREEZE: 'Hiring freeze',
	VENDOR_CONTRACT: 'Vendor contract signed',
	PARTNERSHIP: 'Partnership',
	TECH_ADOPTION: 'Technology adoption',
	IN_HOUSE_BUILD: 'In-house build',
	FINANCIAL_RESULTS: 'Financial results',
	M_AND_A: 'M&A',
	COMPLIANCE: 'Compliance event',
	SECURITY_INCIDENT: 'Security incident',
	STRATEGY: 'Strategy announcement',
	EXECUTIVE_STATEMENT: 'Executive statement'
};

export const EVENT_TYPES = Object.keys(EVENT_LABEL) as EventType[];

export const SOURCE_LABEL: Record<SourceKind, string> = {
	news: 'News',
	jobs: 'Job posts',
	report: 'Annual reports',
	website: 'Website'
};

export const SOURCES = Object.keys(SOURCE_LABEL) as SourceKind[];

export const REGION_OF: Record<string, string> = {
	DE: 'DACH',
	AT: 'DACH',
	CH: 'DACH',
	NL: 'Benelux',
	BE: 'Benelux',
	LU: 'Benelux',
	FR: 'France',
	GB: 'UK & Ireland',
	IE: 'UK & Ireland',
	FI: 'Nordics',
	SE: 'Nordics',
	DK: 'Nordics',
	NO: 'Nordics',
	IT: 'Southern Europe',
	ES: 'Southern Europe',
	PT: 'Southern Europe'
};

export const REGIONS = ['DACH', 'Benelux', 'France', 'Nordics', 'UK & Ireland', 'Southern Europe'];

export const SIZES = ['201-1000', '1001-5000', '5001-10000', '10001+'];

export const INDUSTRIES = [
	'Airlines',
	'Automotive',
	'Banking',
	'Industrial Conglomerate',
	'Industrial Machinery',
	'IT Services',
	'Logistics',
	'Pharmaceuticals',
	'Postal'
];
