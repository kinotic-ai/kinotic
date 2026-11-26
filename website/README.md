# Docus Default Starter

> A beautiful, minimal starter for creating documentation with Docus

This is the default Docus starter template that provides everything you need to build beautiful documentation sites with Markdown and Vue components.

## Kinotic Cloud beta call to action

The homepage hero, the homepage's closing section and the Quick Start's Cloud tab each render `app/components/home/CloudBetaLink.vue`, a button to `https://portal.kinotic.ai/signup`. A click records a `cloud_beta_cta_clicked` event in Google Analytics, with a `placement` parameter of `hero`, `cta` or `docs`, and in Microsoft Clarity, with the session tagged `cloud_beta_cta_placement`. The portal records the rest of the funnel, `cloud_signup_started` and `cloud_signup_completed`, in the same Google Analytics property and Clarity project (see `kinotic-frontend/apps/portal/ENV_SETUP.md`).

The visitor's cookie choice is saved in the `kinotic-consent` cookie on the site's domain, so the portal on its subdomain applies the same choice. To report on `placement` in Google Analytics, register it as an event-scoped custom dimension.

> [!TIP]
> If you're looking for i18n support, check out the [i18n starter](https://github.com/nuxt-themes/docus/tree/main/.starters/i18n).

## ✨ Features

- 🎨 **Beautiful Design** - Clean, modern documentation theme
- 📱 **Responsive** - Mobile-first responsive design  
- 🌙 **Dark Mode** - Built-in dark/light mode support
- 🔍 **Search** - Full-text search functionality
- 📝 **Markdown Enhanced** - Extended markdown with custom components
- 🎨 **Customizable** - Easy theming and brand customization
- ⚡ **Fast** - Optimized for performance with Nuxt 4
- 🔧 **TypeScript** - Full TypeScript support

## 🚀 Quick Start

```bash
# Install dependencies
npm install

# Start development server
npm run dev
```

Your documentation site will be running at `http://localhost:3000`

## 📁 Project Structure

```
my-docs/
├── content/              # Your markdown content
│   ├── index.md         # Homepage
│   ├── 1.getting-started/  # Getting started section
│   └── 2.essentials/    # Essential documentation
├── public/              # Static assets
└── package.json         # Dependencies and scripts
```

## ⚡ Built with

This starter comes pre-configured with:

- [Nuxt 4](https://nuxt.com) - The web framework
- [Nuxt Content](https://content.nuxt.com/) - File-based CMS
- [Nuxt UI](https://ui.nuxt.com) - UI components
- [Nuxt Image](https://image.nuxt.com/) - Optimized images
- [Tailwind CSS 4](https://tailwindcss.com/) - Utility-first CSS
- [Docus Layer](https://www.npmjs.com/package/docus) - Documentation theme

## 📖 Documentation

For detailed documentation on customizing your Docus project, visit the [Docus Documentation](https://docus.dev)

### 🤖 AI Assistant Skill

Get started quickly with Docus by adding specialized knowledge to your AI assistant (Cursor, Claude, etc.):

```bash
npx skills add nuxt-content/docus
```

This skill helps you create documentation faster by providing your AI assistant with best practices, MDC component usage, ready-to-use templates, writing guidelines, and configuration tips for Docus. Perfect for quickly scaffolding new documentation projects.

## 🚀 Deployment

Build for production:

```bash
npm run build
```

The built files will be in the `.output` directory, ready for deployment to any hosting provider that supports Node.js.

## 📄 License

[MIT License](https://opensource.org/licenses/MIT)
