import {ObjectC3Type} from '@kinotic-ai/idl'
import { buildSchema, GraphQLSchema, isObjectType } from 'graphql'
import { glob } from 'glob'
import * as fs from 'fs'
import {GqlConversionState} from '../../src/internal/converter/graphql/GqlConversionState.js'
import {GqlConverterStrategy} from '../../src/internal/converter/graphql/GqlConverterStrategy.js'
import {createConversionContext} from '../../src/internal/converter/IConversionContext.js'
import {ConsoleLogger, Logger} from '../../src/internal/Logger.js'
import {EntityDecorator} from '@kinotic-ai/os-api'
import {MultiTenancyType} from '@kinotic-ai/persistence'

describe('GqlConversionTest', () => {
    it('runs conversion', async () => {
        await processEntityTypes('./test/mock/graphql/**/**.graphql', new ConsoleLogger())
    })
})


// Utility to load schema files and create a GraphQLSchema
async function loadSchemaFromGlob(pattern: string): Promise<GraphQLSchema> {
    const files = await glob(pattern)
    let schemaString = ''

    for (const file of files) {
        schemaString += fs.readFileSync(file, 'utf8') + '\n'
    }

    return buildSchema(schemaString)
}

// Process types with @entity directive
async function processEntityTypes(pattern: string, logger: Logger) {
    const schema = await loadSchemaFromGlob(pattern)
    const gqlConverterStrategy = new GqlConverterStrategy(new GqlConversionState("test"), logger)
    const conversionContext = createConversionContext(gqlConverterStrategy)
    const types = schema.getTypeMap()

    for (const typeName in types) {
        const type = types[typeName]

        // Only process object types
        if (isObjectType(type) && type.astNode) {
            const directives = type.astNode.directives || []

            const hasEntityDirective = directives.find(directive => directive.name.value === 'entity')

            let multiTenancyType = 'NONE' // Default
            if (hasEntityDirective) {

                const arg = hasEntityDirective.arguments?.find(arg => arg.name.value === 'multiTenancyType')
                if (arg && arg.value.kind === 'EnumValue') {
                    multiTenancyType = arg.value.value
                }

                const convertedType = conversionContext.convert(type)

                if(!(convertedType instanceof ObjectC3Type)){
                    throw new Error('Converted type not a ObjectC3Type')
                }

                (convertedType as ObjectC3Type).addDecorator(new EntityDecorator()
                                                                 .withMultiTenancyType((multiTenancyType === 'SHARED' ? MultiTenancyType.SHARED : MultiTenancyType.NONE)))

                console.log(`Converted Type: ${JSON.stringify(convertedType, null, 2)}`)
            }
        }
    }
}

