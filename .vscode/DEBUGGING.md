# Debugging Guide for Structures Frontend

## Prerequisites
Make sure you have the following VS Code extensions installed:
- Vue.volar (Vue Language Features)
- ms-vscode.vscode-js-debug
- ms-vscode.vscode-chrome-debug

## Debugging Steps

### 1. Start the Development Server
First, start your frontend development server:
```bash
cd structures-frontend-next
pnpm dev
```

### 2. Choose Your Debugging Method

#### Option A: Launch Chrome with Debugging (Recommended)
1. Go to Run and Debug panel (Ctrl+Shift+D / Cmd+Shift+D)
2. Select "Debug Vue.js in Chrome" from the dropdown
3. Click the green play button
4. This will:
   - Launch a new Chrome instance with debugging enabled
   - Open your app at http://localhost:5173
   - Enable source maps for proper debugging

#### Option B: Attach to Existing Chrome
1. Start Chrome with remote debugging:
   ```bash
   # On macOS
   /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222
   
   # On Windows
   "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222
   ```
2. Navigate to http://localhost:5173 in Chrome
3. In VS Code, select "Attach to Chrome" and start debugging

#### Option C: Use the Terminal Debugger
1. Select "Start Structures Frontend" from the debug configurations
2. This will start the dev server in the integrated terminal
3. Use "Launch Chrome Debugger for UI" to attach the debugger

### 3. Setting Breakpoints
- Set breakpoints in your Vue components (.vue files)
- Set breakpoints in your TypeScript files (.ts files)
- Breakpoints will work in both the template and script sections

### 4. Debugging Features
- **Step Over**: Execute the current line and move to the next
- **Step Into**: Go into function calls
- **Step Out**: Complete the current function and pause at the next line
- **Continue**: Resume execution until the next breakpoint
- **Variables Panel**: Inspect local variables and component state
- **Call Stack**: See the call hierarchy
- **Watch**: Monitor specific expressions

### 5. Troubleshooting

#### Breakpoints Not Working?
- Ensure source maps are enabled (they are in this config)
- Check that the Chrome DevTools Sources panel shows your source files
- Verify the webRoot path in launch.json matches your project structure

#### Can't See Variables?
- Make sure you're paused at a breakpoint
- Check the Variables panel in the Debug sidebar
- Ensure the scope is correct (local, closure, global)

#### Chrome Debugging Issues?
- Close all Chrome instances before launching the debugger
- Check that port 9222 is not in use
- Verify the URL in launch.json matches your dev server port

## Configuration Details

The debugging configuration includes:
- **Source Maps**: Enabled for proper file mapping
- **Path Overrides**: Configured for Vite/webpack source mapping
- **Chrome Flags**: Optimized for debugging
- **Port Configuration**: Set to 5173 (Vite default)
- **Web Root**: Points to your source directory

## Quick Start
1. `pnpm dev` (in terminal)
2. Select "Debug Vue.js in Chrome" in VS Code
3. Set breakpoints in your code
4. Interact with your app to trigger breakpoints
5. Debug away! 🐛
