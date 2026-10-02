import type { SelectDesignTokens } from '@primeuix/themes/types/select';

 export default {
    root: {
        background: "{form.field.background}",
        disabledBackground: "{form.field.disabled.background}",
        filledBackground: "{form.field.filled.background}",
        filledHoverBackground: "{form.field.filled.hover.background}",
        filledFocusBackground: "{form.field.filled.focus.background}",
        borderColor: "{form.field.border.color}",
        hoverBorderColor: "{form.field.hover.border.color}",
        focusBorderColor: "{form.field.focus.border.color}",
        invalidBorderColor: "{form.field.invalid.border.color}",
        color: "{form.field.color}",
        disabledColor: "{form.field.disabled.color}",
        placeholderColor: "{form.field.placeholder.color}",
        invalidPlaceholderColor: "{form.field.invalid.placeholder.color}",
        shadow: "{form.field.shadow}",
        paddingX: "{form.field.padding.x}",
        paddingY: "{form.field.padding.y}",
        borderRadius: "{form.field.border.radius}",
        focusRing: {
            width: "{form.field.focus.ring.width}",
            style: "{form.field.focus.ring.style}",
            color: "{form.field.focus.ring.color}",
            offset: "{form.field.focus.ring.offset}",
            shadow: "{form.field.focus.ring.shadow}"
        },
        transitionDuration: "{form.field.transition.duration}",
        sm: {
            fontSize: "{form.field.sm.font.size}",
            paddingX: "{form.field.sm.padding.x}",
            paddingY: "{form.field.sm.padding.y}"
        },
        lg: {
            fontSize: "{form.field.lg.font.size}",
            paddingX: "{form.field.lg.padding.x}",
            paddingY: "{form.field.lg.padding.y}"
        }
    },
    dropdown: {
        width: "2.5rem",
        color: "{form.field.icon.color}"
    },
    overlay: {
        background: "{overlay.select.background}",
        borderColor: "{overlay.select.border.color}",
        borderRadius: "{overlay.select.border.radius}",
        color: "{overlay.select.color}",
        shadow: "{overlay.select.shadow}"
    },
    list: {
        padding: "{list.padding}",
        gap: "{list.gap}",
        header: {
            padding: "{list.header.padding}"
        }
    },
    option: {
        focusBackground: "{list.option.focus.background}",
        selectedBackground: "{list.option.selected.background}",
        selectedFocusBackground: "{list.option.selected.focus.background}",
        color: "{list.option.color}",
        focusColor: "{list.option.focus.color}",
        selectedColor: "{list.option.selected.color}",
        selectedFocusColor: "{list.option.selected.focus.color}",
        padding: "{list.option.padding}",
        borderRadius: "{list.option.border.radius}"
    },
    optionGroup: {
        background: "{list.option.group.background}",
        color: "{list.option.group.color}",
        fontWeight: "{list.option.group.font.weight}",
        padding: "{list.option.group.padding}"
    },
    clearIcon: {
        color: "{form.field.icon.color}"
    },
    checkmark: {
        color: "{sky.500}",
        gutterStart: "-0.375rem",
        gutterEnd: "0.375rem"
    },
    emptyMessage: {
        padding: "{list.option.padding}"
    },
    // A small clear button with a hover state, so it reads as something to click rather than a stray glyph
    css: ({ dt }) => `
        .p-select-clear-icon {
            box-sizing: content-box;
            width: 0.625rem;
            height: 0.625rem;
            padding: 0.25rem;
            border-radius: 0.375rem;
            cursor: pointer;
            transition: background-color 0.15s, color 0.15s;
        }
        .p-select-clear-icon:hover {
            background: ${dt('content.hover.background')};
            color: ${dt('text.color')};
        }
    `
} satisfies SelectDesignTokens;