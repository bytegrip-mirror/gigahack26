import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const rawPath = resolve(here, '../../api/src/main/resources/corpus/raw.json');
const documentsPath = resolve(here, '../src/lib/mock/documents.ts');
const outPath = resolve(here, '../src/lib/mock/people.generated.ts');

const corpusKeyOf = {
	lufthansa: 'lufthansa-group',
	dhl: 'dhl-group',
	gsk: 'gsk',
	stellantis: 'stellantis',
	bnp: 'bnp-paribas',
	capgemini: 'capgemini',
	valmet: 'valmet',
	bpost: 'bpost',
	thyssenkrupp: 'thyssenkrupp'
};

const found = [
	['lufthansa', 'Thomas Rückert', 'CIO', 'Thomas Rückert, CIO of Lufthansa Group', 'lufthansa-group:bing:5ad5fc8b2f44', null],
	['lufthansa', 'Grazia Vittadini', 'Chief Technology Officer', 'Grazia Vittadini, Chief Technology Officer of Lufthansa Group', 'lufthansa-group:bing:89a0592d5c6e', null],
	['lufthansa', 'Tamur Goudarzi Pour', 'EVP Strategy', 'Lufthansa Group EVP strategy Tamur Goudarzi Pour', 'lufthansa-group:bing:cd82820eac45', null],
	['lufthansa', 'Thomas Wittmann', 'CEO, Lufthansa Systems', 'Thomas Wittmann – CEO, Lufthansa Systems', 'lufthansa-group:bing:5ad5fc8b2f44', null],

	['dhl', 'Katja Busch', 'Chief Commercial Officer and Head of DHL Customer Solutions & Innovation', 'Katja Busch, Chief Commercial Officer DHL and Head of DHL Customer Solutions & Innovation', 'dhl-group:site:bdb3433a4476', null],
	['dhl', 'Tim Robertson', 'EVP Accelerated Digitalization, DHL Global Forwarding', 'Tim Robertson, Executive Vice President Accelerated Digitalization, DHL Global Forwarding', 'dhl-group:site:b137931dbefc', null],
	['dhl', 'Thomas Ogilvie', 'Chief Human Resources Officer and Labor Director', 'Thomas Ogilvie, Chief Human Resources Officer and Labor Director at DHL Group', 'dhl-group:site:bdb3433a4476', null],
	['dhl', 'Klaus Dohrmann', 'VP Innovation and Trend Research, DHL Customer Solutions & Innovation', 'Klaus Dohrmann, Vice President of Innovation and Trend Research, DHL Customer Solutions & Innovation', 'dhl-group:site:bdb3433a4476', null],
	['dhl', 'Greg Nichols', 'SVP Global Customs, DHL Global Forwarding', 'Greg Nichols, Senior Vice President Global Customs at DHL Global Forwarding', 'dhl-group:site:401476c2e36b', null],
	['dhl', 'Hendrik Venter', 'Global CEO, DHL Supply Chain', 'Hendrik Venter, Global CEO, DHL Supply Chain', 'dhl-group:site:320a45fdb917', null],
	['dhl', 'Martyn Lawns', 'CEO, DHL Industrial Projects and SVP Growth for New Energy', 'Martyn Lawns, CEO of DHL Industrial Projects and Senior Vice President of Growth for New Energy at DHL Group', 'dhl-group:site:320a45fdb917', null],
	['dhl', 'Francisco Milián', 'Head of Inbound to Manufacturing, DHL Supply Chain EMEA', 'Francisco Milián, Head of Inbound to Manufacturing, DHL Supply Chain EMEA', 'dhl-group:site:320a45fdb917', null],
	['dhl', 'Christopher Ong', 'Managing Director Singapore, New Energy Board Member for Express', 'Christopher Ong, Managing Director Singapore and New Energy Board Member for Express', 'dhl-group:site:320a45fdb917', null],
	['dhl', 'Agustín Croche', 'CEO Latin America, DHL Supply Chain', 'Agustín Croche, CEO Latin America of DHL Supply Chain', 'dhl-group:site:767faad57af0', null],
	['dhl', 'Robinson Vasquez', 'CEO, DHL Supply Chain Colombia', 'Robinson Vasquez, CEO of DHL Supply Chain Colombia', 'dhl-group:site:767faad57af0', null],
	['dhl', 'Hennie Heymans', 'CEO, DHL Express Sub-Saharan Africa', 'Hennie Heymans, CEO, DHL Express Sub-Saharan Africa', 'dhl-group:site:0df6bd948077', null],
	['dhl', 'Jim Monkmeyer', 'Global Head of LLP & Supply Chain Orchestration, DHL Supply Chain', 'Jim Monkmeyer, currently President, Transportation, DHL Supply Chain North America, has been appointed Global Head of LLP & Supply Chain Orchestration, DHL Supply Chain, effective immediately', 'dhl-group:site:21a1353054b8', null],

	['gsk', 'Luke Miels', 'CEO', 'recently appointed CEO, Luke Miels', 'gsk:nws-4', null],
	['gsk', 'Tony Wood', 'Chief Scientific Officer', "GSK's CSO, Tony Wood", 'gsk:nws-4', null],
	['gsk', 'Michael Elmore', 'SVP and Chief Information Security Officer', 'Michael Elmore, SVP and Chief Information Security Officer, GSK', 'gsk:nws-6', null],
	['gsk', 'Hesham Abdullah', 'SVP, Global Head of Oncology R&D', 'Hesham Abdullah, Senior Vice President, Global Head Oncology, R&D, GSK', 'gsk:site:4d9f99dacf39', null],
	['gsk', 'Sanjay Gurunathan', 'Head of Vaccines and Infectious Diseases R&D', 'Sanjay Gurunathan, GSK Head of Vaccines and Infectious Diseases Research and Development', 'gsk:site:4b8001979c0b', null],
	['gsk', 'Vasudevan Lakshmanan', 'Country HR Head, Malaysia', 'GSK has appointed Vasudevan Lakshmanan as Country HR Head for Malaysia', 'gsk:nws-3', null],

	['stellantis', 'Antonio Filosa', 'CEO', 'CEO Antonio Filosa', 'stellantis:bing:16541d9d5580', '2025-06-23'],
	['stellantis', 'Emanuele Cappellano', 'COO Enlarged Europe', 'Emanuele Cappellano, Chief Operating Officer Enlarged Europe', 'stellantis:site:262e64fbe178', null],
	['stellantis', 'Ned Curic', 'Chief Engineering and Technology Officer', 'Ned Curic, Chief Engineering and Technology Officer', 'stellantis:site:262e64fbe178', null],
	['stellantis', 'Ralph Gilles', 'Head of Stellantis Design', 'Ralph Gilles, Head of Stellantis Design', 'stellantis:site:262e64fbe178', null],
	['stellantis', 'Gilles Vidal', 'Head of Design for Enlarged Europe', 'Gilles Vidal, Head of Design for Enlarged Europe', 'stellantis:site:262e64fbe178', null],
	['stellantis', 'Stephanie Hartgrove', 'Chief Communications Officer', 'Stephanie Hartgrove has been appointed Chief Communications Officer', 'stellantis:site:17bb98b88680', null],
	['stellantis', 'Clara Ingen-Housz', 'Head of Public Policy & Regulatory Strategy, International Trade', 'Clara Ingen-Housz is appointed Head of Public Policy & Regulatory Strategy, International Trade', 'stellantis:site:17bb98b88680', null],
	['stellantis', 'Herlander Zola', 'South America COO', 'Herlander Zola – South America COO', 'stellantis:site:c4410600d0f6', null],
	['stellantis', 'Grégoire Olivier', 'Asia Pacific COO', 'Grégoire Olivier – Asia Pacific COO', 'stellantis:site:c4410600d0f6', null],
	['stellantis', 'Jon Nelson', 'CEO Financial Services', 'Jon Nelson, CEO Financial Services', 'stellantis:site:c4410600d0f6', null],
	['stellantis', 'John Elkann', 'Chairman', 'John Elkann, Chairman', 'stellantis:site:c4410600d0f6', null],
	['stellantis', 'Mike Powers', 'Director of Sales Operations, Chrysler', 'Mike Powers, Director of Sales Operations for Chrysler', 'stellantis:site:4cd365dc1019', null],

	['bnp', 'Marc Camus', 'Group Chief Information Officer', 'BNP Group Chief Information Officer Marc Camus', 'bnp-paribas:bing:ca11989a10bb', null],
	['bnp', 'Charles Holive', 'Chief AI Officer, Corporate and Institutional Banking', 'Charles Holive, BNP’s chief AI officer for corporate and institutional banking', 'bnp-paribas:bing:751a90bbc982', null],

	['capgemini', 'Aiman Ezzat', 'CEO', 'Aiman Ezzat, CEO of Capgemini', 'capgemini:bing:7b626ac12816', null],
	['capgemini', 'Cyril Garcia', 'Head of Global Sustainability Services and Corporate Responsibility', 'Cyril Garcia, Head of Global Sustainability Services and Corporate Responsibility at Capgemini', 'capgemini:bing:7b626ac12816', null],
	['capgemini', 'Kartik Ramakrishnan', 'CEO Financial Services, Group Executive Board member', 'Kartik Ramakrishnan, CEO - Financial Services and Member of the Group Executive Board, Capgemini', 'capgemini:bing:f3caa4fdf2c4', null],
	['capgemini', 'Sanjay Chalke', 'CEO India', 'Sanjay Chalke, CEO - India, Capgemini', 'capgemini:bing:f3caa4fdf2c4', null],
	['capgemini', 'Jeroen Hölscher', 'Global Head of Payment Services', 'Jeroen Hölscher , Global Head of Payment Services at Capgemini', 'capgemini:bing:d4aa2434cb06', null],

	['valmet', 'Thomas Hinnerskov', 'President and CEO', 'President and CEO Thomas Hinnerskov', 'valmet:bing:de2761b39c6b', null],
	['valmet', 'Katri Hokkanen', 'CFO', 'CFO Katri Hokkanen', 'valmet:bing:de2761b39c6b', null],
	['valmet', 'Arto Huuskonen', 'Vice President, IT Transformation', 'Arto Huuskonen, Vice President, IT Transformation at Valmet', 'elisa:bing:d9b91158aea5', null],
	['valmet', 'Pekka Rouhiainen', 'VP, Investor Relations', 'Pekka Rouhiainen, VP, Investor Relations, Valmet', 'valmet:bing:de2761b39c6b', null],
	['valmet', 'Markus Veikkola', 'Sales Director, China and Asia Pacific, Packaging and Paper Machines', 'Markus Veikkola , Sales Director, China and Asia Pacific, Packaging and Paper Machines, Valmet', 'valmet:bing:330f362d6fb5', null],

	['bpost', 'Chris Peeters', 'CEO of bpostgroup', 'Chris Peeters, CEO of bpostgroup', 'bpost:bing:993d326cf572', null],
	['bpost', 'Laurens Himpe', 'Chief Automated Parcel Officer', 'Laurens Himpe, Chief Automated Parcel Officer at bpost', 'bpost:bing:993d326cf572', null],

	['thyssenkrupp', 'Miguel López', 'CEO', 'CEO Miguel López', 'thyssenkrupp:bing:732ae7d1c99f', null]
];

const raw = JSON.parse(readFileSync(rawPath, 'utf8'));
const byKey = new Map(raw.evidence.map((e) => [e.key, e]));

const mockSource = readFileSync(documentsPath, 'utf8');
const mockIdByHash = new Map();
for (const m of mockSource.matchAll(/"id": "([^"]+)"[\s\S]*?"hash": "([^"]+)"/g)) mockIdByHash.set(m[2], m[1]);

const slug = (s) =>
	s
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, '-')
		.replace(/^-|-$/g, '');

const failures = [];
const unmockedDocuments = new Set();
const capturedDates = [];

const people = found.map(([companyId, name, role, quote, docKey, since]) => {
	const doc = byKey.get(docKey);
	if (!doc) {
		failures.push(`${name}: document ${docKey} not in raw.json`);
		return null;
	}
	if (!doc.text.includes(quote)) failures.push(`${name}: quote not in ${docKey}: ${quote}`);
	if (!quote.includes(name.split(' ').at(-1))) failures.push(`${name}: quote does not name the person`);
	if (since !== null && !/^\d{4}-\d{2}-\d{2}$/.test(since)) failures.push(`${name}: since is not an ISO date`);

	const corpusKey = corpusKeyOf[companyId];
	const naming = raw.evidence.filter(
		(e) => (e.companyKey === corpusKey || e.key === docKey) && e.text.includes(name)
	);
	const published = naming.map((e) => e.publishedAt).filter(Boolean).sort();
	const lastSeen = published.at(-1) ?? naming.map((e) => e.observedAt).sort().at(-1);
	if (!published.length) capturedDates.push(name);

	const documentId = mockIdByHash.get(doc.contentHash) ?? doc.key;
	if (documentId === doc.key) unmockedDocuments.add(doc.key);

	return {
		id: `p-${companyId}-${slug(name.split(' ').at(-1))}`,
		companyId,
		name,
		role,
		since,
		quote,
		documentId,
		sourceName: doc.publisher,
		sourceUrl: doc.sourceUrl,
		capturedAt: doc.observedAt,
		lastSeen
	};
});

const ids = people.filter(Boolean).map((p) => p.id);
for (const id of ids.filter((id, i) => ids.indexOf(id) !== i)) failures.push(`duplicate id ${id}`);

if (failures.length) {
	console.error(`extract-people: ${failures.length} failure(s)`);
	for (const f of failures) console.error(`  ${f}`);
	process.exit(1);
}

const header = `export type Person = {
	id: string;
	companyId: string;
	name: string;
	role: string;
	since: string | null;
	quote: string;
	documentId: string;
	sourceName: string;
	sourceUrl: string;
	capturedAt: string;
	lastSeen: string;
};
`;
writeFileSync(outPath, `${header}\nexport const people: Person[] = ${JSON.stringify(people, null, '\t')};\n`);

const perCompany = Object.fromEntries(Object.keys(corpusKeyOf).map((c) => [c, people.filter((p) => p.companyId === c).length]));
console.log(`wrote ${people.length} people to ${outPath}`);
console.log('per company', perCompany);
console.log('documents not in the mock', [...unmockedDocuments].sort());
console.log('lastSeen from capture date', capturedDates);
