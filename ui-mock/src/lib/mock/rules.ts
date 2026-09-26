import type { Dimension, Fade, Rubric, Tier, Trigger, UniversalRules } from './types';

export const FADES: Fade[] = ['EVENT', 'PROGRAM', 'STRUCTURAL'];

export const FADE_LABEL: Record<Fade, string> = { EVENT: 'Event', PROGRAM: 'Program', STRUCTURAL: 'Structural' };

export const FADE_HINT: Record<Fade, string> = {
	EVENT: 'A one-time fact, such as a hire or an incident',
	PROGRAM: 'A plan that runs for a year or more',
	STRUCTURAL: 'How the company is built, such as an in-house team'
};

export const WEIGHT_LABEL: Record<Tier, string> = { 3: 'High', 2: 'Medium', 1: 'Low' };

export const DIMENSION_ORDER: Dimension[] = ['NEED', 'BUDGET', 'TIMING', 'ACCESS', 'OPENNESS'];

export const HALF_LIFE: Record<Fade, number> = { EVENT: 3, PROGRAM: 12, STRUCTURAL: 24 };

export type TriggerSeed = Omit<Trigger, 'halfLifeMonths'>;

export function withHalfLife(list: TriggerSeed[], halfLife: Record<Fade, number> = HALF_LIFE): Trigger[] {
	return list.map((trigger) => ({ ...trigger, halfLifeMonths: halfLife[trigger.fade ?? 'PROGRAM'] }));
}

function rubric(counts: string, doesNotCount: string, countsExample: string, failsExample: string): Rubric {
	return { counts, doesNotCount, countsExample, failsExample };
}

export const rules: UniversalRules = {
	positive: withHalfLife([
		{
			id: 'u-exec',
			text: 'A new C-level leader in a buying role',
			dimension: 'TIMING',
			direction: 1,
			tier: 3,
			flag: false,
			eventTypes: ['EXECUTIVE_CHANGE'],
			fade: 'EVENT',
			rubric: rubric(
				'A named CEO, COO, CIO, CFO, CDO or CISO who started in the last 12 months.',
				'A board member, a rumour, or a change below C-level.',
				'The board named a new Chief Information Officer, who joins on 1 January.',
				'The company is reported to be searching for a new CIO.'
			)
		},
		{
			id: 'u-reg',
			text: 'A regulatory deadline in scope for the service',
			dimension: 'TIMING',
			direction: 1,
			tier: 2,
			flag: false,
			eventTypes: ['COMPLIANCE'],
			fade: 'EVENT',
			rubric: rubric(
				'A dated rule or audit that applies to the company and that the service helps to meet.',
				'A rule with no date, or one that does not apply to the company.',
				'The bank must finish its first DORA resilience test by June next year.',
				'Regulators are looking at AI.'
			)
		},
		{
			id: 'u-funding',
			text: 'A funding round or strong results',
			dimension: 'BUDGET',
			direction: 1,
			tier: 2,
			flag: false,
			eventTypes: ['FINANCIAL_RESULTS'],
			fade: 'EVENT',
			rubric: rubric(
				'New capital raised, or reported profit well above the prior year.',
				'Revenue growth with falling profit.',
				'The company raised 80 million euros to expand into three new markets.',
				'Sales grew, but the company made a loss.'
			)
		},
		{
			id: 'u-mna',
			text: 'Integrates an acquisition or a merger',
			dimension: 'NEED',
			direction: 1,
			tier: 2,
			flag: false,
			eventTypes: ['M_AND_A'],
			fade: 'PROGRAM',
			rubric: rubric(
				'A closed deal with an integration of systems, processes or teams that runs now.',
				'A deal that was only announced, or one that failed.',
				'After the merger closed in March, the two finance and IT organisations will be combined by 2027.',
				'The company is in early talks about a possible deal.'
			)
		}
	]),
	negative: withHalfLife([
		{
			id: 'u-insolvency',
			text: 'Insolvency, or restructuring under creditor protection',
			dimension: 'BUDGET',
			direction: -1,
			tier: 3,
			flag: true,
			eventTypes: ['RESTRUCTURING'],
			fade: 'PROGRAM',
			rubric: rubric(
				'A court filing for insolvency, or a restructuring run under creditor protection.',
				'A voluntary cost program.',
				'The company filed for creditor protection and a court appointed an administrator.',
				'The company started a voluntary savings program.'
			)
		},
		{
			id: 'u-freeze',
			text: 'A hiring freeze',
			dimension: 'BUDGET',
			direction: -1,
			tier: 2,
			flag: false,
			eventTypes: ['HIRING_FREEZE'],
			fade: 'EVENT',
			rubric: rubric(
				'The company pauses hiring for office or IT roles.',
				'A freeze in one plant or one country only.',
				'The group froze all hiring in administration and IT until further notice.',
				'The Lyon plant will not replace two retiring staff.'
			)
		},
		{
			id: 'u-warning',
			text: 'A profit warning',
			dimension: 'BUDGET',
			direction: -1,
			tier: 2,
			flag: false,
			eventTypes: ['FINANCIAL_RESULTS'],
			fade: 'EVENT',
			rubric: rubric(
				'The company cuts its own profit guidance.',
				'An analyst downgrade.',
				'The company cut its full-year operating profit forecast by a third.',
				'An analyst lowered the price target.'
			)
		},
		{
			id: 'u-rival-deal',
			text: 'Just signed a multi-year deal with a competitor',
			dimension: 'OPENNESS',
			direction: -1,
			tier: 3,
			flag: false,
			eventTypes: ['VENDOR_CONTRACT'],
			fade: 'PROGRAM',
			rubric: rubric(
				'A contract with a competing provider for the same service, with a term. It counts until the end date.',
				'A contract for a different service.',
				'The group signed a six-year managed services contract with a global IT provider.',
				'The group signed a new lease for its headquarters.'
			)
		},
		{
			id: 'u-competitor',
			text: 'Is itself a competitor',
			dimension: 'OPENNESS',
			direction: -1,
			tier: 3,
			flag: true,
			eventTypes: ['STRATEGY'],
			fade: 'STRUCTURAL',
			rubric: rubric(
				'The company sells the same kind of IT services to other companies.',
				'The company sells software or hardware only.',
				'We are a global leader in consulting, technology and digital transformation services.',
				'We make industrial pumps with smart sensors.'
			)
		}
	]),
	evidence: [
		{
			id: 'ev-quote',
			name: 'Exact quote from a stored source',
			rule: 'The sentence must appear word for word in a document that we fetched and stored. A summary or a paraphrase does not count.',
			pass: 'A sentence copied from the annual report, with a link to the stored copy.',
			fail: 'A model summary that says the company "seems to focus on efficiency".'
		},
		{
			id: 'ev-actor',
			name: 'The company is the actor',
			rule: 'The company itself must do the thing. A supplier, a customer or the market does not count.',
			pass: 'The group will automate invoice processing in all European units.',
			fail: 'Its main supplier will automate invoice processing.'
		},
		{
			id: 'ev-specific',
			name: 'Specific',
			rule: 'The sentence must name an action and a concrete detail, such as a number, a date, a name or a program.',
			pass: 'The company will reduce 1,500 administrative roles by 2028.',
			fail: 'The company wants to become more efficient.'
		},
		{
			id: 'ev-dated',
			name: 'Dated',
			rule: 'We must know when it happened, from the sentence or from the document date. Without a date, it cannot fade.',
			pass: 'A press release published on 12 March 2026.',
			fail: 'An undated page on the company website.'
		}
	],
	scale: [
		{ id: 'sc-away', level: 'Below 0', meaning: 'Moving away. Negative signals outweigh the positive ones.' },
		{ id: 'sc-faint', level: '0.5', meaning: 'Weak. One medium signal, some months old.' },
		{ id: 'sc-some', level: '1.0', meaning: 'Some. One fresh strong signal, or two older ones.' },
		{ id: 'sc-lit', level: '1.5', meaning: 'Strong. The dimension is lit.' }
	],
	lit: { NEED: 1.5, BUDGET: 1.5, TIMING: 1.5, ACCESS: 1.5, OPENNESS: 1.5 },
	halfLife: { ...HALF_LIFE }
};
