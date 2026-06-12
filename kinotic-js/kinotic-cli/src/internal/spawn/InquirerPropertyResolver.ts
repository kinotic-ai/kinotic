import {confirm, input, number, select} from '@inquirer/prompts'
import type {PropertyResolver, PropertySchema} from '@kinotic-ai/spawn'

/**
 * Prompts the user on the terminal for spawn properties missing from the
 * render context, choosing the prompt type from the property's schema.
 */
export class InquirerPropertyResolver implements PropertyResolver {

  private hasPrompted = false

  async resolve(key: string, schema: PropertySchema, message: string, defaultValue?: unknown): Promise<unknown> {
    if (!this.hasPrompted) {
      console.log('Please provide the following...\n')
      this.hasPrompted = true
    }

    if (schema.type === 'boolean') {
      return confirm({message, default: typeof defaultValue === 'boolean' ? defaultValue : undefined})
    } else if (schema.enum) {
      return select({
        message,
        choices: schema.enum.map((v: string) => ({value: v})),
        default: defaultValue !== undefined ? String(defaultValue) : undefined
      })
    } else if (schema.type === 'number' || schema.type === 'integer') {
      return number({message, default: typeof defaultValue === 'number' ? defaultValue : undefined})
    } else {
      return input({message, default: typeof defaultValue === 'string' ? defaultValue : undefined})
    }
  }

}
