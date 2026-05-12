import { definePreset } from '@primeuix/themes'
import StructuresTheme from '@/theme'

/**
 * This is kinda a hack since the Theme Designer does not allow colors to be defined properly.
 */
export const StructuresPreset = definePreset(StructuresTheme, {
    primitive:{
        brand: {
            50:  "#FFF0F4",
            100: "#FFD9E3",
            200: "#FFB1C5",
            300: "#FF7D9E",
            400: "#F84C77",
            500: "#EC1F52",
            600: "#D31346",
            700: "#AE123C",
            800: "#901437",
            900: "#7A1634",
            950: "#450818"
        },
        teal: {
            400: "#28FEB4",
        },
    },
    semantic: {
        primary    : {
            50 : '{brand.50}',
            100: '{brand.100}',
            200: '{brand.200}',
            300: '{brand.300}',
            400: '{brand.400}',
            500: '{brand.500}',
            600: '{brand.600}',
            700: '{brand.700}',
            800: '{brand.800}',
            900: '{brand.900}',
            950: '{brand.950}'
        },
        colorScheme: {
            light: {
                surface: {
                    0  : "#ffffff",
                    50 : "#FAFAFA",
                    100: "#F3F3F4",
                    200: "#E5E5E7",
                    300: "#D5D5D9",
                    400: "#A1A1AA",
                    500: "#71717A",
                    600: "#52525B",
                    700: "#3F3F46",
                    800: "#27272A",
                    900: "#171717",
                    950: "#101010"
                },
                lime   : {
                    50 : "#F7FEE7",
                    100: "#ECFCCB",
                    200: "#D9F99D",
                    300: "#BEF264",
                    400: "#A3E635",
                    500: "#84CC16",
                    600: "#65A30D",
                    700: "#4D7C0F",
                    800: "#3F6212",
                    900: "#365314",
                    950: "#1A2E05"
                },
                formField: {
                    background: '#ffffff',
                    borderColor: '#d8dce6',
                    hoverBorderColor: '#d8dce6',
                    focusBorderColor: '#52525b',
                    color: '#101010',
                    placeholderColor: '#9ca3af',
                    shadow: 'none'
                }
            },
            dark : {
                surface: {
                    0  : "#ffffff",
                    50 : "#FAFAFA",
                    100: "#F3F3F4",
                    200: "#E5E5E7",
                    300: "#D5D5D9",
                    400: "#A1A1AA",
                    500: "#71717A",
                    600: "#52525B",
                    700: "#3F3F46",
                    800: "#27272A",
                    900: "#171717",
                    950: "#101010"
                },
                lime   : {
                    50 : "#F7FEE7",
                    100: "#ECFCCB",
                    200: "#D9F99D",
                    300: "#BEF264",
                    400: "#A3E635",
                    500: "#84CC16",
                    600: "#65A30D",
                    700: "#4D7C0F",
                    800: "#3F6212",
                    900: "#365314",
                    950: "#1A2E05"
                },
                formField: {
                    background: '#262626',
                    borderColor: '#434349',
                    hoverBorderColor: '#434349',
                    focusBorderColor: '#52525b',
                    color: '#ffffff',
                    placeholderColor: '#8d8d96',
                    shadow: 'none'
                }
            }
        }
    },
    css: ({ dt }) => `
        .app-surface-panel {
            background: ${dt('surface.0')};
            color: ${dt('surface.950')};
        }

        html.dark .app-surface-panel {
            background: ${dt('surface.900')};
            color: ${dt('surface.0')};
        }

        .app-surface-header {
            background: ${dt('surface.0')};
            border-color: ${dt('surface.200')};
        }

        html.dark .app-surface-header {
            background: ${dt('surface.900')};
            border-color: ${dt('surface.800')};
        }

        .app-surface-divider {
            border-color: ${dt('surface.200')};
        }

        html.dark .app-surface-divider {
            border-color: ${dt('surface.800')};
        }

        .app-card-surface {
            background: ${dt('surface.0')};
            border-color: ${dt('surface.200')};
        }

        html.dark .app-card-surface {
            background: ${dt('surface.900')};
            border-color: ${dt('surface.800')};
        }

        .app-heading-text {
            color: ${dt('surface.950')};
        }

        html.dark .app-heading-text {
            color: ${dt('surface.0')};
        }

        .app-muted-text {
            color: ${dt('surface.500')};
        }

        html.dark .app-muted-text {
            color: ${dt('surface.400')};
        }

        .app-subtle-text {
            color: ${dt('surface.600')};
        }

        html.dark .app-subtle-text {
            color: ${dt('surface.400')};
        }

        .p-button.app-neutral-button {
            border: 1px solid ${dt('surface.200')};
            background: ${dt('surface.0')};
            color: ${dt('surface.700')};
            border-radius: 0.375rem;
            font-weight: 500;
            box-shadow: none;
        }

        .p-button.app-neutral-button:hover {
            border-color: ${dt('surface.300')};
            background: ${dt('surface.50')};
        }

        html.dark .p-button.app-neutral-button {
            border: 1px solid ${dt('surface.700')};
            background: ${dt('surface.900')};
            color: ${dt('surface.0')};
        }

        html.dark .p-button.app-neutral-button:hover {
            border-color: ${dt('surface.600')};
            background: ${dt('surface.800')};
        }

        .p-button.app-neutral-button-sm {
            height: 33px;
            padding-inline: 1rem;
            font-size: 14px;
        }

        .p-button.app-neutral-button-sm .pi {
            font-size: 14px;
        }

        .p-button.app-tonal-button {
            border: 1px solid ${dt('surface.200')};
            background: ${dt('surface.200')};
            color: ${dt('surface.700')};
            border-radius: 0.375rem;
            font-weight: 500;
            box-shadow: none;
        }

        .p-button.app-tonal-button:hover {
            border-color: ${dt('surface.300')};
            background: ${dt('surface.300')};
        }

        html.dark .p-button.app-tonal-button {
            border: 1px solid ${dt('surface.800')};
            background: ${dt('surface.800')};
            color: ${dt('surface.0')};
        }

        html.dark .p-button.app-tonal-button:hover {
            border-color: ${dt('surface.700')};
            background: ${dt('surface.700')};
        }

        .p-button.app-tonal-button-sm {
            height: 33px;
            padding-inline: 1rem;
            font-size: 14px;
        }

        .login-page {
            --lp-page-bg: ${dt('surface.50')};
            --lp-panel-bg: ${dt('surface.0')};
            --lp-text: ${dt('surface.950')};
            --lp-text-muted: ${dt('surface.500')};
            --lp-radial-opacity: 0;
            --lp-input-bg: ${dt('surface.0')};
            --lp-input-border: ${dt('surface.300')};
            --lp-input-focus-border: ${dt('surface.400')};
            --lp-input-color: ${dt('surface.950')};
            --lp-input-placeholder: ${dt('surface.400')};
            --lp-input-disabled-bg: ${dt('surface.200')};
            --lp-input-disabled-border: ${dt('surface.300')};
            --lp-input-disabled-color: ${dt('surface.500')};
            --lp-icon-color: ${dt('surface.500')};
            --lp-icon-hover: ${dt('surface.950')};
            --lp-link-color: ${dt('surface.700')};
            --lp-link-hover: ${dt('surface.950')};
            --lp-footer-link: #0568FD;
            --lp-footer-divider: ${dt('surface.900')};
            --lp-overlay-bg: rgba(249, 250, 251, 0.92);
            --lp-overlay-title: ${dt('surface.950')};
            --lp-overlay-muted: ${dt('surface.500')};
            --lp-provider-bg: ${dt('surface.50')};
            --lp-provider-border: ${dt('surface.300')};
            --lp-provider-bg-hover: ${dt('surface.100')};
            --lp-provider-border-hover: ${dt('surface.400')};
            --lp-provider-color: ${dt('surface.950')};
        }

        html.dark .login-page {
            --lp-page-bg: ${dt('surface.950')};
            --lp-panel-bg: ${dt('surface.950')};
            --lp-text: ${dt('surface.0')};
            --lp-text-muted: ${dt('surface.400')};
            --lp-radial-opacity: 0;
            --lp-input-bg: transparent;
            --lp-input-border: ${dt('surface.600')};
            --lp-input-focus-border: ${dt('surface.500')};
            --lp-input-color: ${dt('surface.0')};
            --lp-input-placeholder: ${dt('surface.400')};
            --lp-input-disabled-bg: ${dt('surface.800')};
            --lp-input-disabled-border: ${dt('surface.600')};
            --lp-input-disabled-color: ${dt('surface.300')};
            --lp-icon-color: ${dt('surface.400')};
            --lp-icon-hover: ${dt('surface.100')};
            --lp-link-color: ${dt('surface.300')};
            --lp-link-hover: ${dt('surface.0')};
            --lp-footer-link: ${dt('surface.0')};
            --lp-footer-divider: ${dt('teal.400')};
            --lp-overlay-bg: rgba(16, 16, 16, 0.9);
            --lp-overlay-title: ${dt('surface.100')};
            --lp-overlay-muted: ${dt('surface.400')};
            --lp-provider-bg: ${dt('surface.950')};
            --lp-provider-border: ${dt('surface.800')};
            --lp-provider-bg-hover: ${dt('surface.900')};
            --lp-provider-border-hover: ${dt('surface.700')};
            --lp-provider-color: ${dt('surface.100')};
        }
    `
})