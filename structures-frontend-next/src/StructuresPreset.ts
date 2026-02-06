import { definePreset } from '@primeuix/themes'
import StructuresTheme from '@/theme'

/**
 * This is kinda a hack since the Theme Designer does not allow colors to be defined properly.
 */
export const StructuresPreset = definePreset(StructuresTheme, {
    primitive:{
        blue: {
            50: "#EFF5FF",
            100: "#DBE7FE",
            200: "#BFD0FE",
            300: "#93ACFD",
            400: "#6081FA",
            500: "#3651ED",
            600: "#2543EB",
            700: "#1D33D8",
            800: "#1E2AAD",
            900: "#1E2E8A",
            950: "#171754"
        }
    },
    semantic: {
        primary    : {
            50 : '{blue.50}',
            100: '{blue.100}',
            200: '{blue.200}',
            300: '{blue.300}',
            400: '{blue.400}',
            500: '{blue.500}',
            600: '{blue.600}',
            700: '{blue.700}',
            800: '{blue.800}',
            900: '{blue.900}',
            950: '{blue.950}'
        },
        colorScheme: {
            light: {
                surface: {
                    0  : "#ffffff",
                    50 : "#F5F7FA",
                    100: "#F0F1F5",
                    200: "#E6E7EB",
                    300: "#D2D3D9",
                    400: "#AFB0B8",
                    500: "#797B80",
                    600: "#4F5159",
                    700: "#3F424D",
                    800: "#28282B",
                    900: "#18181A",
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
            },
            dark : {
                surface: {
                    0  : "#ffffff",
                    50 : "#F5F7FA",
                    100: "#F0F1F5",
                    200: "#E6E7EB",
                    300: "#D2D3D9",
                    400: "#AFB0B8",
                    500: "#797B80",
                    600: "#4F5159",
                    700: "#3F424D",
                    800: "#28282B",
                    900: "#18181A",
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
            }
        }
    }
})