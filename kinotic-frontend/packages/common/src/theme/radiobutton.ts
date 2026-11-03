import type { RadioButtonDesignTokens } from '@primeuix/themes/types/radiobutton';

 export default {
    root: {
        width: "1.25rem",
        height: "1.25rem",
        background: "{form.field.background}",
        disabledBackground: "{form.field.disabled.background}",
        filledBackground: "{form.field.filled.background}",
        borderColor: "{form.field.border.color}",
        hoverBorderColor: "{form.field.hover.border.color}",
        focusBorderColor: "{form.field.border.color}",
        checkedDisabledBorderColor: "{form.field.border.color}",
        invalidBorderColor: "{form.field.invalid.border.color}",
        shadow: "{form.field.shadow}",
        focusRing: {
            width: "{focus.ring.width}",
            style: "{focus.ring.style}",
            color: "{focus.ring.color}",
            offset: "{focus.ring.offset}",
            shadow: "{focus.ring.shadow}"
        },
        transitionDuration: "{form.field.transition.duration}",
        sm: {
            width: "1rem",
            height: "1rem"
        },
        lg: {
            width: "1.5rem",
            height: "1.5rem"
        }
    },
    icon: {
        size: "0.75rem",
        disabledColor: "{form.field.disabled.color}",
        sm: {
            size: "0.5rem"
        },
        lg: {
            size: "1rem"
        }
    },
    colorScheme: {
        light: {
            root: {
                // checked reads as near-black, like the primary buttons
                checkedBackground: "{surface.950}",
                checkedHoverBackground: "{surface.800}",
                checkedBorderColor: "{surface.950}",
                checkedHoverBorderColor: "{surface.800}",
                checkedFocusBorderColor: "{surface.950}"
            },
            icon: {
                checkedColor: "{surface.0}",
                checkedHoverColor: "{surface.0}"
            }
        },
        dark: {
            root: {
                // inverted in dark mode, as the primary buttons are
                checkedBackground: "{surface.0}",
                checkedHoverBackground: "{surface.200}",
                checkedBorderColor: "{surface.0}",
                checkedHoverBorderColor: "{surface.200}",
                checkedFocusBorderColor: "{surface.0}"
            },
            icon: {
                checkedColor: "{surface.900}",
                checkedHoverColor: "{surface.900}"
            }
        }
    }
} satisfies RadioButtonDesignTokens;