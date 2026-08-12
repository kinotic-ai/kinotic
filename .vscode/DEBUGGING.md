# Debugging Guide for the Kinotic Frontend

Covers `kinotic-frontend/apps/portal` (the dashboard) and `kinotic-frontend/apps/system`
(the operator console). Both are Vite + Vue 3 apps in the same pnpm workspace.

## Prerequisites

Install these VS Code extensions:
- Vue.volar (Vue Language Features)
- ms-vscode.js-debug (bundled with VS Code)

## Debugging Steps

### 1. Start the Development Server

From the workspace root:

```bash
cd kinotic-frontend
pnpm install
pnpm dev          # portal on http://localhost:5173
pnpm dev:system   # system console
```

Both talk to kinotic-server at `localhost:58503` by default — see
`kinotic-frontend/apps/portal/ENV_SETUP.md` for how `VITE_KINOTIC_HOST`/`PORT`/`USE_SSL`
resolve per command.

### 2. Choose Your Debugging Method

#### Option A: Launch Chrome from VS Code
1. Go to Run and Debug (Ctrl+Shift+D / Cmd+Shift+D)
2. Pick the Chrome launch configuration and start it
3. VS Code opens a Chrome instance pointed at http://localhost:5173 with source maps on

#### Option B: Attach to an existing Chrome
1. Start Chrome with remote debugging:
   ```bash
   # On macOS
   /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222

   # On Windows
   "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222
   ```
2. Navigate to http://localhost:5173
3. Attach from VS Code

### 3. Setting Breakpoints
- Set breakpoints in Vue components (`.vue`) and TypeScript files (`.ts`)
- Breakpoints work in both the template and script sections
- `vite.config.ts` sets `build.sourcemap: true`, so production builds are debuggable too

### 4. Troubleshooting

#### Breakpoints Not Working?
- Check the Chrome DevTools Sources panel actually shows your source files
- Verify the `webRoot` in `launch.json` points at the app you are running

#### Chrome Debugging Issues?
- Close all Chrome instances before launching the debugger
- Check that port 9222 is not in use
- Verify the URL in `launch.json` matches your dev server port (5173)

## Known issue: `.vscode/launch.json` is stale

The committed configurations still reference the pre-rename layout and will not run as-is:

| In `launch.json` | Actual |
|---|---|
| `cwd`/`webRoot` `${workspaceFolder}/structures-frontend-next` | `${workspaceFolder}/kinotic-frontend/apps/portal` |
| `mainClass` `org.kinotic.structuresserver.StructuresServerApplication` | `org.kinotic.server.KinoticServerApplication` |
| `projectName` `structures-server` | `kinotic-server` |
| `-cp ${workspaceFolder}/structures-core/src/main/resources` | `kinotic-core/src/main/resources` |

Fix those paths before using the Run and Debug panel.
