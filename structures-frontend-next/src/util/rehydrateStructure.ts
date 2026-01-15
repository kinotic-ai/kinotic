import {
    ObjectC3Type,
    EnumC3Type,
    UnionC3Type,
    ArrayC3Type,
    PropertyDefinition,
    C3Decorator,
    C3Type
} from '@mindignited/continuum-idl'

/**
 * Recursively reconstructs C3Type objects from plain JSON
 */
export const rehydrateStructure = (json: any): C3Type => {
    if (!json) return json;

    switch (json.type) {
        case 'object': {
            const obj = new ObjectC3Type(json.name, json.namespace);

            if (json.decorators) {
                obj.decorators = json.decorators.map((d: any) => {
                    const dec = new C3Decorator();
                    Object.assign(dec, d);
                    return dec;
                });
            }

            if (json.properties) {
                obj.properties = json.properties.map((p: any) => {
                    return new PropertyDefinition(
                        p.name,
                        rehydrateStructure(p.type),
                        (p.decorators || []).map((d: any) => {
                            const dec = new C3Decorator();
                            Object.assign(dec, d);
                            return dec;
                        })
                    );
                });
            }

            return obj;
        }

        case 'enum': {
            const en = new EnumC3Type(json.name, json.namespace);
            en.values = [...(json.values || [])];
            return en;
        }

        case 'union': {
            const un = new UnionC3Type(json.name, json.namespace);
            un.types = (json.types || []).map((t: any) => rehydrateStructure(t));
            return un;
        }

        case 'array': {
            return new ArrayC3Type(rehydrateStructure(json.contains));
        }

        default:
            // primitive types are their own classes — create them accordingly
            // You could also map these from a lookup table if needed
            return json as C3Type;
    }
};
