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
			name: 'spawn',
			root: 'packages/spawn'
		},
		{
			name: 'spawn-node',
			root: 'packages/spawn-node'
		}
	],
	{
		format: ["esm", "cjs"],
		exports: true,
	}
)
