import type { DataTableDesignTokens } from '@primeuix/themes/types/datatable';

 export default {
    root: {
        transitionDuration: "{transition.duration}"
    },
    header: {
        background: "{content.background}",
        borderColor: "{datatable.border.color}",
        color: "{content.color}",
        borderWidth: "0 0 1px 0",
        padding: "0.75rem 1rem",
        sm: {
            padding: "0.375rem 0.5rem"
        },
        lg: {
            padding: "1rem 1.25rem"
        }
    },
    headerCell: {
        background: "{content.background}",
        hoverBackground: "{content.hover.background}",
        selectedBackground: "{highlight.background}",
        borderColor: "{datatable.border.color}",
        color: "{content.color}",
        hoverColor: "{content.hover.color}",
        selectedColor: "{highlight.color}",
        gap: "0.5rem",
        padding: "0.625rem 0.75rem",
        focusRing: {
            width: "{focus.ring.width}",
            style: "{focus.ring.style}",
            color: "{focus.ring.color}",
            offset: "-1px",
            shadow: "{focus.ring.shadow}"
        },
        sm: {
            padding: "0.375rem 0.5rem"
        },
        lg: {
            padding: "1rem 1.25rem"
        }
    },
    columnTitle: {
        fontWeight: "400"
    },
    row: {
        background: "{content.background}",
        hoverBackground: "{content.hover.background}",
        selectedBackground: "{highlight.background}",
        color: "{content.color}",
        hoverColor: "{content.hover.color}",
        selectedColor: "{highlight.color}",
        focusRing: {
            width: "{focus.ring.width}",
            style: "{focus.ring.style}",
            color: "{focus.ring.color}",
            offset: "-1px",
            shadow: "{focus.ring.shadow}"
        }
    },
    bodyCell: {
        borderColor: "{datatable.border.color}",
        padding: "0.5rem 0.75rem",
        sm: {
            padding: "0.375rem 0.5rem"
        },
        lg: {
            padding: "1rem 1.25rem"
        }
    },
    footerCell: {
        background: "{content.background}",
        borderColor: "{datatable.border.color}",
        color: "{content.color}",
        padding: "0.75rem 1rem",
        sm: {
            padding: "0.375rem 0.5rem"
        },
        lg: {
            padding: "1rem 1.25rem"
        }
    },
    columnFooter: {
        fontWeight: "600"
    },
    footer: {
        background: "{content.background}",
        borderColor: "{datatable.border.color}",
        color: "{content.color}",
        borderWidth: "0 0 1px 0",
        padding: "0.75rem 1rem",
        sm: {
            padding: "0.375rem 0.5rem"
        },
        lg: {
            padding: "1rem 1.25rem"
        }
    },
    dropPoint: {
        color: "{primary.color}"
    },
    columnResizer: {
        width: "0.5rem"
    },
    resizeIndicator: {
        width: "1px",
        color: "{primary.color}"
    },
    sortIcon: {
        color: "{text.muted.color}",
        hoverColor: "{text.hover.muted.color}",
        size: "0.875rem"
    },
    loadingIcon: {
        size: "2rem"
    },
    rowToggleButton: {
        hoverBackground: "{content.hover.background}",
        selectedHoverBackground: "{content.background}",
        color: "{text.muted.color}",
        hoverColor: "{text.color}",
        selectedHoverColor: "{primary.color}",
        size: "1.75rem",
        borderRadius: "50%",
        focusRing: {
            width: "{focus.ring.width}",
            style: "{focus.ring.style}",
            color: "{focus.ring.color}",
            offset: "{focus.ring.offset}",
            shadow: "{focus.ring.shadow}"
        }
    },
    filter: {
        inlineGap: "0.5rem",
        overlaySelect: {
            background: "{overlay.select.background}",
            borderColor: "{overlay.select.border.color}",
            borderRadius: "{overlay.select.border.radius}",
            color: "{overlay.select.color}",
            shadow: "{overlay.select.shadow}"
        },
        overlayPopover: {
            background: "{overlay.popover.background}",
            borderColor: "{overlay.popover.border.color}",
            borderRadius: "{overlay.popover.border.radius}",
            color: "{overlay.popover.color}",
            shadow: "{overlay.popover.shadow}",
            padding: "{overlay.popover.padding}",
            gap: "0.5rem"
        },
        rule: {
            borderColor: "{content.border.color}"
        },
        constraintList: {
            padding: "{list.padding}",
            gap: "{list.gap}"
        },
        constraint: {
            focusBackground: "{list.option.focus.background}",
            selectedBackground: "{list.option.selected.background}",
            selectedFocusBackground: "{list.option.selected.focus.background}",
            color: "{list.option.color}",
            focusColor: "{list.option.focus.color}",
            selectedColor: "{list.option.selected.color}",
            selectedFocusColor: "{list.option.selected.focus.color}",
            separator: {
                borderColor: "{content.border.color}"
            },
            padding: "{list.option.padding}",
            borderRadius: "{list.option.border.radius}"
        }
    },
    paginatorTop: {
        borderColor: "{datatable.border.color}",
        borderWidth: "0 0 1px 0"
    },
    paginatorBottom: {
        borderColor: "{datatable.border.color}",
        borderWidth: "0 0 1px 0"
    },
    colorScheme: {
        light: {
            root: {
                borderColor: "{content.border.color}"
            },
            headerCell: {
                background: "{surface.50}",
                hoverBackground: "{surface.100}",
                color: "{surface.600}",
                hoverColor: "{surface.800}",
                // The sorted column reads as a darker grey band, not the brand highlight
                selectedBackground: "{surface.200}",
                selectedColor: "{surface.950}"
            },
            // A selected row reads as the hover grey; the brand red stays for failures
            row: {
                stripedBackground: "{surface.50}",
                selectedBackground: "{surface.100}",
                selectedColor: "{surface.950}"
            },
            bodyCell: {
                selectedBorderColor: "{surface.200}"
            }
        },
        dark: {
            root: {
                borderColor: "{surface.800}"
            },
            headerCell: {
                background: "{surface.800}",
                hoverBackground: "{surface.700}",
                color: "{surface.300}",
                hoverColor: "{surface.100}",
                selectedBackground: "{surface.700}",
                selectedColor: "{surface.0}"
            },
            row: {
                stripedBackground: "{surface.950}",
                selectedBackground: "{surface.800}",
                selectedColor: "{surface.0}"
            },
            bodyCell: {
                selectedBorderColor: "{surface.700}"
            }
        }
    },
    // Every table is framed evenly: the first and last columns sit the same 1.25rem in from the
    // table's edges; a status tag in a row sits at the row's small type size
    css: `
        .p-datatable .p-datatable-thead > tr > th:first-child,
        .p-datatable .p-datatable-tbody > tr > td:first-child {
            padding-inline-start: 1.25rem;
        }
        .p-datatable .p-datatable-thead > tr > th:last-child,
        .p-datatable .p-datatable-tbody > tr > td:last-child {
            padding-inline-end: 1.25rem;
        }
        /* A text button ending a row reaches into the cell's padding, so its label keeps the same inset */
        .p-datatable .p-datatable-tbody > tr > td:last-child .p-button-text:not(.p-button-icon-only):last-child {
            margin-inline-end: calc(-1 * var(--p-button-padding-x));
        }
        .p-datatable .p-datatable-tbody > tr > td:last-child .p-button-text.p-button-sm:not(.p-button-icon-only):last-child {
            margin-inline-end: calc(-1 * var(--p-button-sm-padding-x));
        }
        /* The sort-order badge means something only once two columns sort together; it reads as a
           quiet grey count then, never the brand red the platform keeps for failures */
        .p-datatable .p-datatable-sort-badge {
            min-width: 1rem;
            height: 1rem;
            font-size: 0.625rem;
            line-height: 1rem;
            background: var(--p-surface-200);
            color: var(--p-surface-700);
        }
        .p-dark .p-datatable .p-datatable-sort-badge,
        .dark .p-datatable .p-datatable-sort-badge {
            background: var(--p-surface-700);
            color: var(--p-surface-100);
        }
        .p-datatable-thead:not(:has(th:is([aria-sort="ascending"], [aria-sort="descending"]) ~ th:is([aria-sort="ascending"], [aria-sort="descending"]))) .p-datatable-sort-badge {
            display: none;
        }
        .p-datatable .p-datatable-tbody .p-tag {
            font-size: 0.6875rem;
            line-height: 1rem;
            padding: 0.125rem 0.375rem;
            letter-spacing: 0.02em;
        }
    `
} satisfies DataTableDesignTokens;