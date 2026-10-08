import ts from 'typescript'
import type { Plugin } from 'vite'

const PERSISTENCE_IMPORT = /from\s*['"]@kinotic-ai\/persistence['"]/

/**
 * A Vite plugin that compiles the persistence decorators on bundled entity and repository classes
 * to plain JavaScript, so a UI that uses those classes as values (`new Todo()`, `instanceof`) runs
 * in the browser. It compiles every `.ts` module outside `node_modules` that imports from
 * `@kinotic-ai/persistence`, and leaves every other module to Vite.
 *
 * @return the plugin, to add to the `plugins` of a Vite config
 */
export function kinoticDecorators(): Plugin {
    return {
        name: 'kinotic-decorators',
        // Runs before Vite's own TypeScript transform, which passes decorator syntax through unchanged
        enforce: 'pre',
        transform(code, id) {
            const file = id.split('?', 1)[0] ?? id
            let ret: { code: string, map: string | undefined } | undefined
            if (file.endsWith('.ts') && !file.includes('/node_modules/') && PERSISTENCE_IMPORT.test(code)) {
                const result = ts.transpileModule(code, {
                    fileName: file,
                    compilerOptions: {
                        target: ts.ScriptTarget.ES2022,
                        module: ts.ModuleKind.ESNext,
                        useDefineForClassFields: true,
                        sourceMap: true
                    }
                })
                ret = { code: result.outputText, map: result.sourceMapText }
            }
            return ret
        }
    }
}
