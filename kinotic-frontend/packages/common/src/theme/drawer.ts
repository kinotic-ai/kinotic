import type { DrawerDesignTokens } from '@primeuix/themes/types/drawer';

 export default {
    root: {
        background: "{overlay.modal.background}",
        borderColor: "{overlay.modal.border.color}",
        color: "{overlay.modal.color}",
        shadow: "{overlay.modal.shadow}"
    },
    header: {
        padding: "1.25rem 1.5rem"
    },
    title: {
        fontSize: "1.125rem",
        fontWeight: "500"
    },
    content: {
        padding: "1.5rem"
    },
    footer: {
        padding: "0.75rem 1.5rem"
    }
} satisfies DrawerDesignTokens;