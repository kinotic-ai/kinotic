export function usePropertyTypes() {
    const primitiveTypes = [
        'boolean', 'byte', 'char', 'date', 'double', 'enum',
        'float', 'integer', 'long', 'map', 'object',
        'short', 'string', 'union'
    ]

    function capitalize(str: string) {
        return str ? str.charAt(0).toUpperCase() + str.slice(1) : ''
    }

    const typeOptions = [
        {
            label: 'Array',
            code: 'array',
            children: primitiveTypes
                .filter(t => t !== 'array')
                .map(t => ({ label: `Array[${capitalize(t)}]`, code: `array<${t}>` }))
        },
        ...primitiveTypes.map(t => ({
            label: capitalize(t),
            code: t
        }))
    ]

    return {
        primitiveTypes,
        typeOptions
    }
}
