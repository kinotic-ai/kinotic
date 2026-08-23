import { defineWorkspace } from 'bunup'

// https://bunup.dev/docs/guide/workspaces

export default defineWorkspace(
	[
		{
			// Library (".") plus the Node adapter ("./node") that installs a header-capable WebSocket.
			name: 'core',
			root: 'packages/core',
			config: { entry: ['src/index.ts', 'src/node/index.ts'] }
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
			name: 'project-sync',
			root: 'packages/project-sync'
		},
		{
			// Library only: core engine (".") + node fs adapter ("./node"), deps external.
			// The GraalJS iife (spawnGraalRendererMain) is built by the package's
			// build:graal-renderer script — bunup mis-tree-shakes zod v4 into a broken bundle.
			name: 'spawn',
			root: 'packages/spawn',
			config: { entry: ['src/index.ts', 'src/node/index.ts'] }
		},
		{
			name: 'vm-manager',
			root: 'packages/vm-manager'
		},
	],
	{
		format: ["esm", "cjs"],
		exports: true,
	}
)
