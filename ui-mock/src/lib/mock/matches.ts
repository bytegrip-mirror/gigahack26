import type { Match } from './types';

export const matches: Match[] = [
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-jobs', targetId: 't-ia-admin', why: 'Cutting 4,000 admin roles through digitalization is the pain automation removes.', confidence: 0.95 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-ai', targetId: 't-ia-cost', why: 'The company ties its efficiency targets to AI and digitalization.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-ai', targetId: 't-ia-ai', why: 'AI is part of the current plan, not a future idea.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-cargo', targetId: 't-ia-invest', why: '600 million euros committed, with automation named in the same sentence.', confidence: 0.8 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-profit', targetId: 't-ia-profit', why: 'Profitable and pushing for more.', confidence: 0.75 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-itreorg', targetId: 't-ia-reorg', why: 'IT and AI are being reorganised in 2026.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-itreorg', targetId: 't-ia-owner', why: 'The EVP strategy speaks for the reorganisation by name.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-itreorg', targetId: 't-ia-team', why: '8,000 IT staff is a strong internal team.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-digital', targetId: 't-ia-team', why: 'Digital units are being merged to grow internal expertise.', confidence: 0.8 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-infosys', targetId: 't-ia-partners', why: 'A long-term deal with Infosys shows the group buys outside help.', confidence: 0.8 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-amadeus', targetId: 't-ia-partners', why: 'Another outside technology provider, chosen in 2026.', confidence: 0.7 },
	{ icpId: 'ia', companyId: 'lufthansa', eventId: 'e-lh-cio', targetId: 't-ia-owner', why: 'The CIO links AI to operations in his own words.', confidence: 0.85 },

	{ icpId: 'ia', companyId: 'gsk', eventId: 'e-gsk-cost', targetId: 't-ia-cost', why: 'A £1.9bn savings target by 2029.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'gsk', eventId: 'e-gsk-simplify', targetId: 't-ia-admin', why: 'A simpler organisation means fewer manual steps in support functions.', confidence: 0.75 },
	{ icpId: 'ia', companyId: 'gsk', eventId: 'e-gsk-q2', targetId: 't-ia-profit', why: 'Revenue beat forecasts.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'gsk', eventId: 'e-gsk-rd', targetId: 't-ia-invest', why: '£400m committed this year.', confidence: 0.8 },
	{ icpId: 'ia', companyId: 'gsk', eventId: 'e-gsk-ceo', targetId: 't-ia-exec', why: 'A new CEO with a new plan.', confidence: 0.95 },
	{ icpId: 'ia', companyId: 'gsk', eventId: 'e-gsk-ceo', targetId: 't-ia-owner', why: 'The CEO owns the savings plan by name.', confidence: 0.9 },

	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-accio', targetId: 't-ia-usecase', why: 'Freight quotes and booking are being automated.', confidence: 0.8 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-accio', targetId: 't-ia-partners', why: 'Alibaba.com is an outside partner for AI agents.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-tradenav', targetId: 't-ia-team', why: 'DHL builds its own AI tools.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-q2', targetId: 't-ia-profit', why: 'Profit up 30 percent in one quarter.', confidence: 0.95 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-forecast', targetId: 't-ia-profit', why: 'The forecast went up.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-radar', targetId: 't-ia-ai', why: 'AI that acts on its own is a stated priority now.', confidence: 0.7 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-suite', targetId: 't-ia-ai', why: 'More automation capabilities are planned.', confidence: 0.75 },
	{ icpId: 'ia', companyId: 'dhl', eventId: 'e-dhl-robertson', targetId: 't-ia-owner', why: 'An EVP for digitalization speaks for the AI work by name.', confidence: 0.9 },

	{ icpId: 'ia', companyId: 'stellantis', eventId: 'e-stla-cost', targetId: 't-ia-cost', why: 'Billions in savings is the top priority.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'stellantis', eventId: 'e-stla-hiring', targetId: 't-ia-invest', why: 'Hiring 2,000 engineers this year.', confidence: 0.7 },
	{ icpId: 'ia', companyId: 'stellantis', eventId: 'e-stla-margin', targetId: 't-ia-squeeze', why: 'A low single-digit margin leaves little room for new spend.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'stellantis', eventId: 'e-stla-freeze', targetId: 't-ia-squeeze', why: 'White-collar hiring is paused.', confidence: 0.9 },
	{ icpId: 'ia', companyId: 'stellantis', eventId: 'e-stla-plan', targetId: 't-ia-reorg', why: 'The turnaround runs now.', confidence: 0.8 },
	{ icpId: 'ia', companyId: 'stellantis', eventId: 'e-stla-plan', targetId: 't-ia-owner', why: 'The CEO owns the turnaround plan by name.', confidence: 0.8 },

	{ icpId: 'ia', companyId: 'bnp', eventId: 'e-bnp-usecases', targetId: 't-ia-usecase', why: '1,000 AI use cases means many processes are being automated.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'bnp', eventId: 'e-bnp-usecases', targetId: 't-ia-ai', why: 'The target date is 2026.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'bnp', eventId: 'e-bnp-ibm', targetId: 't-ia-invest', why: 'A 10-year cloud renewal shows large, long IT budgets.', confidence: 0.75 },
	{ icpId: 'ia', companyId: 'bnp', eventId: 'e-bnp-mistral', targetId: 't-ia-lock', why: 'Generative AI work is contracted to Mistral AI.', confidence: 0.9, term: { basis: 'Extended in May 2026 for three more years', endsAt: '2029-05' } },
	{ icpId: 'ia', companyId: 'bnp', eventId: 'e-bnp-mistral', targetId: 't-ia-partners', why: 'The bank buys AI from outside providers.', confidence: 0.85 },
	{ icpId: 'ia', companyId: 'bnp', eventId: 'e-bnp-google', targetId: 't-ia-lock', why: 'Google Cloud supplies AI models and agents.', confidence: 0.9, term: { basis: 'Five-year partnership announced in September 2026', endsAt: '2031-09' } },

	{ icpId: 'ia', companyId: 'capgemini', eventId: 'e-cap-ai', targetId: 't-ia-comp', why: 'Capgemini sells AI and IT services. It competes with Orange for the same buyers.', confidence: 0.95 },

	{ icpId: 'cyber', companyId: 'valmet', eventId: 'e-val-elisa', targetId: 't-cy-buys', why: 'Valmet pays for managed security.', confidence: 0.9 },
	{ icpId: 'cyber', companyId: 'valmet', eventId: 'e-val-elisa', targetId: 't-cy-lock', why: 'Elisa runs the SOC from April 2026.', confidence: 0.9, term: { basis: 'Multi-year, effective 1 April 2026. Term not public.' } },
	{ icpId: 'cyber', companyId: 'valmet', eventId: 'e-val-elisa', targetId: 't-cy-outside', why: 'Valmet buys security from an outside provider.', confidence: 0.9 },
	{ icpId: 'cyber', companyId: 'valmet', eventId: 'e-val-guidance', targetId: 't-cy-profit', why: 'Profit is guided flat or up.', confidence: 0.85 },
	{ icpId: 'cyber', companyId: 'valmet', eventId: 'e-val-huuskonen', targetId: 't-cy-owner', why: 'The VP IT Transformation speaks for the security deal by name.', confidence: 0.9 },

	{ icpId: 'cyber', companyId: 'bpost', eventId: 'e-bp-breach', targetId: 't-cy-incident', why: "A real leak, through a supplier's platform.", confidence: 0.95 },
	{ icpId: 'cyber', companyId: 'bpost', eventId: 'e-bp-breach', targetId: 't-cy-recent', why: 'December 2025 is inside the 12-month window.', confidence: 0.95 },
	{ icpId: 'cyber', companyId: 'bpost', eventId: 'e-bp-bbox', targetId: 't-cy-invest', why: 'New hubs are opening, so money goes into new sites and systems.', confidence: 0.65 },
	{ icpId: 'cyber', companyId: 'bpost', eventId: 'e-bp-peeters', targetId: 't-cy-owner', why: 'The CEO speaks for the new sites by name.', confidence: 0.7 },

	{ icpId: 'cyber', companyId: 'thyssenkrupp', eventId: 'e-tk-ransom', targetId: 't-cy-incident', why: 'A ransomware group listed its naval unit.', confidence: 0.9 },
	{ icpId: 'cyber', companyId: 'thyssenkrupp', eventId: 'e-tk-ransom', targetId: 't-cy-recent', why: 'July 2026, two months ago.', confidence: 0.9 },
	{ icpId: 'cyber', companyId: 'thyssenkrupp', eventId: 'e-tk-loss', targetId: 't-cy-squeeze', why: 'The Nucera unit expects a loss of up to €105 million this year.', confidence: 0.9 },
	{ icpId: 'cyber', companyId: 'thyssenkrupp', eventId: 'e-tk-freeze', targetId: 't-cy-squeeze', why: 'Hiring is frozen to save money.', confidence: 0.85 }
];
