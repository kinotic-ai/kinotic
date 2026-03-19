
/**
 * This is the base class for all type schemas in C3.
 * <p>
 * Created by navid on 2023-4-13.
 */
export class C3Type {

    public readonly type: string = ''

    protected constructor(type: string) {
        this.type = type
    }

}
