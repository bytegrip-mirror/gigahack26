import type { Service } from './types';

export const services: Service[] = [
	{
		id: 'ia',
		name: 'Intelligent Automation',
		description:
			'We find the manual, repeated work in business processes and automate it with RPA, AI agents and process mining. We design, build and run the automations with the client team.',
		outcome: 'Lower operating cost, faster cycle times and fewer errors in back-office work.'
	},
	{
		id: 'cyber',
		name: 'Cybersecurity services',
		description:
			'We assess security risk, run managed detection and response, test systems and plants, and help companies meet rules such as NIS2 and DORA.',
		outcome: 'Fewer and shorter incidents, and audits passed on time.'
	},
	{
		id: 'cloud',
		name: 'Cloud and infrastructure modernisation',
		description:
			'We move applications and data centers to the cloud, and modernise old systems so that they cost less to run and are easier to change.',
		outcome: 'Lower infrastructure cost and faster releases.'
	},
	{
		id: 'data',
		name: 'Data and AI',
		description:
			'We build data platforms and put AI models into daily work, for example forecasting, document search and customer service.',
		outcome: 'Decisions made on data the business trusts, and AI that runs in production.'
	}
];
