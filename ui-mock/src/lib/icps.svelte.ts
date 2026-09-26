import { icps as seedIcps } from '$lib/mock/icps';
import { services as seedServices } from '$lib/mock/services';
import { rules as seedRules } from '$lib/mock/rules';
import type { Dimension, Direction, Fade, Icp, Service, Trigger, UniversalRules } from '$lib/mock/types';

function blankRubric() {
	return { counts: '', doesNotCount: '', countsExample: '', failsExample: '' };
}

function normalise(list: Icp[]): Icp[] {
	for (const icp of list) {
		icp.personas ??= [];
		icp.complexity ??= '';
		icp.targetRoles ??= icp.personas.map((persona) => persona.role);
		for (const trigger of icp.triggers) {
			trigger.rubric ??= blankRubric();
			trigger.fade ??= 'PROGRAM';
			trigger.halfLifeMonths ??= seedRules.halfLife[trigger.fade];
		}
	}
	return list;
}

const baseline = JSON.stringify(normalise(structuredClone(seedIcps)));

class IcpStore {
	items = $state<Icp[]>(normalise(structuredClone(seedIcps)));
	services = $state<Service[]>(structuredClone(seedServices));
	rules = $state<UniversalRules>(structuredClone(seedRules));
	#next = 1;

	get edited(): boolean {
		return (
			JSON.stringify(this.items) !== baseline ||
			JSON.stringify(this.services) !== JSON.stringify(seedServices) ||
			JSON.stringify(this.rules) !== JSON.stringify(seedRules)
		);
	}

	find(id: string | null): Icp | undefined {
		return this.items.find((icp) => icp.id === id);
	}

	service(id: string | null | undefined): Service | undefined {
		return this.services.find((service) => service.id === id);
	}

	icpsOf(serviceId: string): Icp[] {
		return this.items.filter((icp) => icp.serviceId === serviceId);
	}

	reset() {
		this.items = normalise(structuredClone(seedIcps));
		this.services = structuredClone(seedServices);
		this.rules = structuredClone(seedRules);
	}

	create(serviceId?: string): string {
		const id = `new-${this.#next++}`;
		const service = this.service(serviceId);
		this.items.push({
			id,
			name: 'New ICP',
			serviceLine: service?.name ?? 'New service line',
			serviceId: service?.id,
			regions: [],
			industries: [],
			sizes: [],
			triggers: [],
			complexity: '',
			personas: [],
			targetRoles: []
		});
		return id;
	}

	remove(id: string) {
		this.items = this.items.filter((icp) => icp.id !== id);
	}

	createService(): string {
		const id = `service-${this.#next++}`;
		this.services.push({ id, name: 'New service', description: '', outcome: '' });
		return id;
	}

	renameService(service: Service, name: string) {
		service.name = name;
		for (const icp of this.icpsOf(service.id)) icp.serviceLine = name;
	}

	removeService(id: string) {
		this.services = this.services.filter((service) => service.id !== id);
	}

	addSignal(list: Trigger[], dimension: Dimension, direction: Direction): string {
		const id = `t-new-${this.#next++}`;
		const fade: Fade = 'EVENT';
		list.push({ id, text: '', dimension, direction, tier: 2, flag: false, eventTypes: [], fade, halfLifeMonths: this.rules.halfLife[fade], rubric: blankRubric() });
		return id;
	}

	setFade(trigger: Trigger, fade: Fade) {
		trigger.fade = fade;
		trigger.halfLifeMonths = this.rules.halfLife[fade];
	}

	setHalfLife(fade: Fade, months: number) {
		this.rules.halfLife[fade] = months;
		const all = [...this.items.flatMap((icp) => icp.triggers), ...this.rules.positive, ...this.rules.negative];
		for (const trigger of all) if (trigger.fade === fade) trigger.halfLifeMonths = months;
	}

	syncRoles(icp: Icp) {
		icp.targetRoles = (icp.personas ?? []).map((persona) => persona.role.trim()).filter(Boolean);
	}

	addPersona(icp: Icp) {
		icp.personas = [...(icp.personas ?? []), { id: `p-new-${this.#next++}`, role: '', why: '' }];
	}

	removePersona(icp: Icp, id: string) {
		icp.personas = (icp.personas ?? []).filter((persona) => persona.id !== id);
		this.syncRoles(icp);
	}

	addEvidenceRule() {
		this.rules.evidence.push({ id: `ev-new-${this.#next++}`, name: '', rule: '', pass: '', fail: '' });
	}
}

export const icpStore = new IcpStore();

export function toggle<T>(list: T[], value: T): T[] {
	return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}
