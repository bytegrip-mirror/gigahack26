import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

const apiUrl = process.env.PUBLIC_API_URL ?? 'http://localhost:8080';

export default defineConfig({
	define: {
		__API_URL__: JSON.stringify(apiUrl)
	},
	plugins: [
		sveltekit({
			compilerOptions: {
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},

			adapter: adapter({ fallback: 'index.html' })
		})
	]
});
