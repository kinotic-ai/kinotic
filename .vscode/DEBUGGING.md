# Debugging Guide for the Kinotic Frontend

Covers `kinotic-frontend/apps/portal` (the dashboard) and `kinotic-frontend/apps/system`
(the system console). Both are Vite + Vue 3 apps in the same pnpm workspace.

## Prerequisites

The servers run in docker compose: start them with the **Compose: Up** task, or `docker compose up -d`
in `deployment/docker-compose`.

Install these VS Code extensions:
- Vue.volar (Vue Language Features)
- ms-vscode.js-debug (bundled with VS Code)

## Debugging Steps

### 1. Start the Development Server

Run **Portal: Dev Server** or **System Console: Dev Server** from Run and Debug, or from the workspace
root:

```bash
cd kinotic-frontend
pnpm install
pnpm dev          # portal on http://localhost:5173
pnpm dev:system   # system console on http://localhost:5174
```

The portal talks to the management server at `localhost:58503` and the console to the system server
at `localhost:58504` by default — see
`kinotic-frontend/apps/portal/ENV_SETUP.md` for how `VITE_KINOTIC_HOST`/`PORT`/`USE_SSL`
resolve per command.

### 2. Choose Your Debugging Method

#### Option A: Launch Chrome from VS Code
1. Go to Run and Debug (Ctrl+Shift+D / Cmd+Shift+D)
2. Start **Portal: Chrome** or **System Console: Chrome** once its dev server is up
3. VS Code opens a Chrome instance pointed at http://localhost:5173 (or 5174) with source maps on

#### Option B: Attach to an existing Chrome
1. Start Chrome with remote debugging:
   ```bash
   # On macOS
   /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222

   # On Windows
   "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222
   ```
2. Navigate to http://localhost:5173
3. Start **Chrome: Attach** from Run and Debug; for the system console, point its `webRoot` at
   `kinotic-frontend/apps/system` first

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
- Verify the URL in `launch.json` matches your dev server port (5173 for the portal, 5174 for the console)
