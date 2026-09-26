import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { parse } from 'svelte/compiler';
import { __unstable__loadDesignSystem } from '@tailwindcss/node';

const root = process.cwd();
const src = join(root, 'src');
const system = join('src', 'lib', 'ui') + sep;

const markers = new Set(['group', 'peer']);

const layout = new RegExp(
	'^(' +
		[
			'block', 'inline-block', 'inline', 'flex', 'inline-flex', 'grid', 'inline-grid', 'contents', 'hidden',
			'flex-(row|col|wrap|nowrap|1|auto|none|initial|row-reverse|col-reverse)', 'grow(-0)?', 'shrink(-0)?',
			'basis-\\S+', 'order-\\S+',
			'self-\\S+', 'justify-self-\\S+', 'place-self-\\S+',
			'col-\\S+', 'row-\\S+',
			'w-\\S+', 'min-w-\\S+', 'max-w-\\S+', 'h-\\S+', 'min-h-\\S+', 'max-h-\\S+', 'size-\\S+',
			'static', 'relative', 'absolute', 'fixed', 'sticky',
			'inset-\\S+', 'top-\\S+', 'right-\\S+', 'bottom-\\S+', 'left-\\S+',
			'overflow-\\S+', 'truncate', 'z-[a-z]+', 'sr-only', 'not-sr-only'
		].join('|') +
		')$'
);

const colorModifier =
	/^(bg|text|border(-[trblxy])?|outline|ring|fill|stroke|divide|decoration|accent|caret|placeholder|from|via|to)-[a-z0-9-]+\/\S+$/;

const geometry = new Set([
	'width', 'height', 'min-width', 'max-width', 'min-height', 'max-height',
	'left', 'top', 'right', 'bottom', 'inset', 'transform', 'translate',
	'grid-template-columns', 'grid-template-rows', 'grid-column', 'grid-row', 'flex-basis'
]);

const controls = new Set(['input', 'select', 'textarea']);

const passthrough = new Set(['file', 'hidden']);

const colorLiteral = /#[0-9a-fA-F]{3,8}\b|\b(rgba?|hsla?|oklch|oklab)\(/;

const design = await __unstable__loadDesignSystem(readFileSync(join(src, 'app.css'), 'utf8'), { base: src });

function files(dir) {
	return readdirSync(dir).flatMap((name) => {
		const path = join(dir, name);
		if (statSync(path).isDirectory()) return files(path);
		return path.endsWith('.svelte') ? [path] : [];
	});
}

function base(token) {
	const parts = token.split(':');
	return parts[parts.length - 1].replace(/^!/, '');
}

function lineOf(code, index) {
	return code.slice(0, index).split('\n').length;
}

function collectStrings(node, found, loose) {
	if (!node || typeof node !== 'object') return;
	switch (node.type) {
		case 'Literal':
			if (typeof node.value === 'string') found.push({ text: node.value, at: node.start });
			return;
		case 'TemplateLiteral':
			node.quasis.forEach((quasi, index) => {
				const text = quasi.value.cooked ?? '';
				const touchesBefore = index > 0 && /^\S/.test(text);
				const touchesAfter = index < node.quasis.length - 1 && /\S$/.test(text);
				if (touchesBefore || touchesAfter) loose.push({ reason: 'a class name built from a template', at: quasi.start });
				found.push({ text, at: quasi.start });
			});
			node.expressions.forEach((expression) => collectStrings(expression, found, loose));
			return;
		case 'ConditionalExpression':
			collectStrings(node.consequent, found, loose);
			collectStrings(node.alternate, found, loose);
			return;
		case 'LogicalExpression':
			collectStrings(node.right, found, loose);
			if (node.operator !== '&&') collectStrings(node.left, found, loose);
			return;
		case 'ArrayExpression':
			node.elements.forEach((element) => collectStrings(element, found, loose));
			return;
		case 'ObjectExpression':
			node.properties.forEach((property) => {
				if (property.type !== 'Property') return;
				if (property.key.type === 'Literal') found.push({ text: String(property.key.value), at: property.key.start });
				else if (property.key.type === 'Identifier' && !property.computed)
					found.push({ text: property.key.name, at: property.key.start });
			});
			return;
		default:
			loose.push({ reason: `a class computed from ${node.type}`, at: node.start });
	}
}

function classStrings(attribute) {
	const found = [];
	const loose = [];
	if (attribute.value === true) return { found, loose };
	const parts = Array.isArray(attribute.value) ? attribute.value : [attribute.value];
	for (const part of parts) {
		if (part.type === 'Text') found.push({ text: part.data, at: part.start });
		else if (part.type === 'ExpressionTag') collectStrings(part.expression, found, loose);
	}
	return { found, loose };
}

function walk(node, visit) {
	if (!node || typeof node !== 'object') return;
	if (Array.isArray(node)) return node.forEach((child) => walk(child, visit));
	if (typeof node.type === 'string') visit(node);
	for (const key of Object.keys(node)) {
		if (key === 'parent' || key === 'metadata') continue;
		const value = node[key];
		if (value && typeof value === 'object') walk(value, visit);
	}
}

const problems = [];
const report = (file, line, message) => problems.push(`${file}:${line}  ${message}`);

for (const path of files(src)) {
	const file = relative(root, path);
	const inSystem = file.startsWith(system);
	const code = readFileSync(path, 'utf8');
	const ast = parse(code, { modern: true });

	if (ast.css && !inSystem) report(file, lineOf(code, ast.css.start), '<style> is only allowed in src/lib/ui');

	for (const script of [ast.instance, ast.module].filter(Boolean)) {
		walk(script.content, (node) => {
			if (node.type === 'ImportDeclaration' && node.source.value === 'bits-ui' && !inSystem)
				report(file, lineOf(code, node.start), 'import bits-ui only inside src/lib/ui, then use the wrapper');
		});
	}

	const markup = code.replace(/<script[\s\S]*?<\/script>/g, (block) => ' '.repeat(block.length));
	const colorMatch = !inSystem && markup.match(colorLiteral);
	if (colorMatch) report(file, lineOf(code, colorMatch.index), `raw colour ${colorMatch[0]}; use a colour token`);

	const candidates = [];

	walk(ast.fragment, (node) => {
		const attributes = node.attributes;
		if (!Array.isArray(attributes)) return;
		const isComponent = node.type === 'Component' || node.type === 'SvelteComponent';
		if (!inSystem && node.type === 'RegularElement' && controls.has(node.name)) {
			const type = attributes.find((attribute) => attribute.type === 'Attribute' && attribute.name === 'type');
			const kind = type && Array.isArray(type.value) && type.value[0]?.type === 'Text' ? type.value[0].data : 'text';
			if (!(node.name === 'input' && passthrough.has(kind)))
				report(file, lineOf(code, node.start), `raw <${node.name}>; use the Input, Textarea, Select or Checkbox primitive`);
		}

		for (const attribute of attributes) {
			const line = lineOf(code, attribute.start);

			if (attribute.type === 'Attribute' && attribute.name === 'style')
				report(file, line, 'style="…" is not allowed; use utilities, or style:<geometry> for computed layout');

			if (attribute.type === 'StyleDirective') {
				const custom = attribute.name.startsWith('--');
				if (!geometry.has(attribute.name) && !(custom && inSystem))
					report(file, line, `style:${attribute.name} is not a layout property`);
				const parts = Array.isArray(attribute.value) ? attribute.value : [];
				if (!inSystem && parts.length > 0 && parts.every((part) => part.type === 'Text'))
					report(file, line, `style:${attribute.name} has a fixed value; use a token utility, style: is only for computed values`);
			}

			const tokens = [];
			if (attribute.type === 'ClassDirective') tokens.push({ token: attribute.name, at: attribute.start });
			if (attribute.type === 'Attribute' && (attribute.name === 'class' || attribute.name === 'className')) {
				const { found, loose } = classStrings(attribute);
				if (!inSystem) loose.forEach((item) => report(file, lineOf(code, item.at), `${item.reason}; write class names literally`));
				for (const { text, at } of found)
					for (const token of text.split(/\s+/).filter(Boolean)) tokens.push({ token, at });
			}

			for (const { token, at } of tokens) {
				const where = lineOf(code, at);
				const name = base(token);
				const plumbing = inSystem && token.replace(/data-\[[a-z-]+=[a-z-]+\]:|\(--bits-[a-z-]+\)/g, '');
				if (/[[\]()]/.test(plumbing === false ? token : plumbing)) {
					report(file, where, `"${token}" is an arbitrary value; use a token`);
					continue;
				}
				if (/^-?z-\d/.test(name)) report(file, where, `"${token}" uses a raw z-index; use z-sticky, z-raised, z-overlay, z-menu, z-sheet or z-toast`);
				if (colorModifier.test(name)) report(file, where, `"${token}" changes a colour's opacity; use a colour token`);
				if (isComponent && !inSystem && !layout.test(name))
					report(file, where, `"${token}" styles a component from outside; components take only layout classes, use a variant prop`);
				if (!markers.has(name)) candidates.push({ token, where });
			}
		}
	});

	if (candidates.length) {
		const css = design.candidatesToCss(candidates.map((candidate) => candidate.token));
		candidates.forEach((candidate, index) => {
			if (!css[index]) report(file, candidate.where, `"${candidate.token}" is not in the design system`);
		});
	}
}

if (problems.length) {
	console.error(problems.join('\n'));
	console.error(`\n${problems.length} design-system violation(s). See docs/ui-architecture.md.`);
	process.exit(1);
}
console.log('design-lint: 0 violations');
