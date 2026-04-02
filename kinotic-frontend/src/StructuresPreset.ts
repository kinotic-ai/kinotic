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
            }
        }
    }
})