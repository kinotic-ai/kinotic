import { defineWorkspace } from 'bunup'

// https://bunup.dev/docs/guide/workspaces

export default defineWorkspace(
	[
		{
			name: 'core',
			root: 'packages/core'
		},
		{
			name: 'idl',
			root: 'packages/idl'
		},
		{
			name: 'os-api',
			root: 'packages/os-api'
		},
		{
			name: 'persistence',
			root: 'packages/persistence'
		},
		{
			name: 'vm-manager',
			root: 'packages/vm-manager',
			config: [
				{
					name   : "vm-manager-linux-64",
					entry  : "src/index.ts",
					compile: {
						target : "bun-linux-x64",
						outfile: "vm-manager-linux-64",
					},
				},
				// {
				// 	name   : "vm-manager-macos",
				// 	entry  : "src/index.ts",
				// 	compile: {
				// 		target : "bun-darwin-arm64",
				// 		outfile: "vm-manager-macos",
				// 	},
				// }
			]
		}
	],
	{
		format: ["esm", "cjs"],
		exports: true,
	}
)
