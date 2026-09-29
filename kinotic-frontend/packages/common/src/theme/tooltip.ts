import type { TooltipDesignTokens } from '@primeuix/themes/types/tooltip';

 export default {
    root: {
        maxWidth: "12.5rem",
        gutter: "0.25rem",
        shadow: "{overlay.popover.shadow}",
        padding: "0.375rem 0.625rem",
        borderRadius: "{border.radius.lg}"
    },
    colorScheme: {
        light: {
            root: {
                background: "{surface.900}",
                color: "{surface.0}"
            }
        },
        dark: {
            root: {
                background: "{surface.700}",
                color: "{surface.0}"
            }
        }
    }
} satisfies TooltipDesignTokens;