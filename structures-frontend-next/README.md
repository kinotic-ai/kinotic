# Structures Frontend Next

A modern Vue 3 + TypeScript frontend application for the Structures framework, providing a rich web interface for data management and administration.

## Overview

Structures Frontend Next is the next-generation web interface for the Structures framework, built with:
- **Vue 3**: Modern reactive framework with Composition API
- **TypeScript**: Type-safe development experience
- **Vite**: Fast build tool and development server
- **Tailwind CSS**: Utility-first CSS framework
- **Vue Router**: Client-side routing
- **Pinia**: State management

## Features

- **Modern UI**: Clean, responsive design with Tailwind CSS
- **Type Safety**: Full TypeScript support throughout the application
- **Authentication**: OIDC integration with multiple providers (Okta, Keycloak, Microsoft, Google, GitHub)
- **Multi-tenant Support**: Tenant management and isolation
- **Data Management**: CRUD operations for structures and schemas
- **GraphQL Integration**: Native GraphQL client with type generation
- **Responsive Design**: Mobile-first approach with responsive components
- **Theme Support**: Customizable UI themes and components

## Quick Start

### 1. Install dependencies

```bash
npm install
# or
pnpm install
```

### 2. Configure authentication

Copy and edit the configuration file:
```bash
cp public/app-config.json.example public/app-config.json
```

Edit `public/app-config.json` with your OIDC provider settings.

### 3. Start development server

```bash
npm run dev
# or
pnpm dev
```

The application will be available at http://localhost:5173

### 4. Build for production

```bash
npm run build
# or
pnpm build
```

## Configuration

### Runtime Configuration

The application supports runtime configuration through JSON files. See [CONFIGURATION.md](CONFIGURATION.md) for detailed configuration options.

### OIDC Providers

Supported authentication providers:
- **Okta**: Enterprise SSO
- **Keycloak**: Open-source identity management
- **Microsoft Entra ID**: Azure Active Directory
- **Google**: Google Workspace
- **GitHub**: GitHub OAuth
- **Microsoft Social**: Personal Microsoft accounts
- **Apple**: Apple ID
- **Custom**: Any OIDC-compliant provider

### Basic Authentication

For development and testing, basic username/password authentication is available and configurable.

## Project Structure

```
src/
├── components/          # Reusable Vue components
│   ├── modals/         # Modal dialogs
│   └── ...             # Other component categories
├── layouts/             # Page layout components
├── pages/               # Route-based page components
├── plugins/             # Vue plugins and extensions
├── states/              # Pinia state management
├── theme/               # UI theme configuration
├── types/               # TypeScript type definitions
├── util/                # Utility functions
└── main.ts             # Application entry point
```

## Development

### Prerequisites

- Node.js 18+ 
- npm or pnpm
- Modern web browser

### Development Commands

```bash
# Install dependencies
pnpm install

# Start development server
pnpm dev

# Start development server with kind cluster config
pnpm dev:kind

# Build for production
pnpm build

# Build for kind cluster deployment
pnpm build:kind

# Preview production build
pnpm preview

# Preview kind cluster build
pnpm preview:kind

# Run type checking
pnpm type-check

# Run linting
pnpm lint
```

### Code Style

- Use TypeScript for all new code
- Follow Vue 3 Composition API patterns
- Use Tailwind CSS for styling
- Implement responsive design principles
- Write unit tests for components

## Testing

### Unit Tests

```bash
# Run unit tests
pnpm test

# Run tests in watch mode
pnpm test:watch

# Generate coverage report
pnpm test:coverage
```

### E2E Tests

```bash
# Run end-to-end tests
pnpm test:e2e

# Run E2E tests in UI mode
pnpm test:e2e:ui
```

## Deployment

### Static Hosting

The application builds to static files that can be hosted on any web server:

1. Build the application: `pnpm build`
2. Deploy the `dist/` folder to your web server
3. Ensure the configuration file is accessible at `/app-config.json`

### Docker Deployment

```dockerfile
FROM nginx:alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Environment Variables

The application supports environment-specific configuration through Vite's environment variable system.

#### Continuum WebSocket Connection

Configure the WebSocket connection to the Structures server using these environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_CONTINUUM_HOST` | WebSocket server hostname | `127.0.0.1` (auto-detected from window.location if deployed) |
| `VITE_CONTINUUM_PORT` | WebSocket server port | `58503` |
| `VITE_CONTINUUM_USE_SSL` | Use secure WebSocket (wss://) | `false` (auto-detected from window.location if deployed) |

#### Environment Files

The application uses multiple `.env` files for different deployment scenarios:

- **`.env`**: Base configuration for production/deployed builds
- **`.env.development`**: Development overrides (used with `npm run dev`)
- **`.env.kind`**: Kind cluster overrides (used with `npm run dev:kind`)
- **`.env.local`**: Local overrides (gitignored, highest precedence)

**Example: Connect to Kind cluster NodePort directly**

Create a `.env.local` file:
```bash
VITE_CONTINUUM_HOST=127.0.0.1
VITE_CONTINUUM_PORT=30503  # Direct NodePort access
VITE_CONTINUUM_USE_SSL=false
```

Or use the provided mode:
```bash
npm run dev:kind
```

#### Build-Time Variables

For build-time configuration, you can use environment variables:

```bash
VITE_API_BASE_URL=https://api.example.com
VITE_APP_TITLE=Structures Admin
```

## Architecture

### State Management

- **Pinia**: Centralized state management
- **Composables**: Reusable logic with Vue Composition API
- **Local Storage**: Persistent user preferences

### Routing

- **Vue Router**: Client-side routing with lazy loading
- **Route Guards**: Authentication and authorization checks
- **Dynamic Routes**: Tenant-specific routing

### API Integration

- **GraphQL**: Primary data interface
- **REST**: Fallback and file operations
- **WebSocket**: Real-time updates (if configured)

## Contributing

1. Follow the existing code conventions
2. Use TypeScript for all new code
3. Write tests for new functionality
4. Update documentation as needed
5. Ensure responsive design principles

## Troubleshooting

### Common Issues

1. **Authentication Errors**
   - Check OIDC provider configuration
   - Verify redirect URIs match
   - Check browser console for errors

2. **Build Errors**
   - Clear node_modules and reinstall
   - Check TypeScript configuration
   - Verify all dependencies are compatible

3. **Runtime Errors**
   - Check browser console for errors
   - Verify API endpoints are accessible
   - Check network connectivity

### Debug Mode

Enable debug logging in the configuration:

```json
{
  "debug": true
}
```

## Related Documentation

- [Configuration Guide](CONFIGURATION.md) - Detailed configuration options
- [Structures Server](../structures-server/README.md) - Backend server documentation
- [Structures Core](../structures-core/README.md) - Core library documentation

## License

This application is part of the Structures framework and follows the same licensing terms.
