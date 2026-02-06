export class IndexNameHelper {
    private static illegalStructureNameChars: RegExp = new RegExp(/[.][.]|[\\][\\]|[/]|[*]|[?]|[\\]|<|>|[|]|[ ]|[,]|[#]|[:]|[;]|[+]|[=]|[(]|[)]|[{]|[}]/)
    public static checkNameAndNamespace(value: string, reference: string): string {
        let ret: string = ''
        if(value === null || value === undefined) {
            ret = `${reference} must contain a valid value`
        } else if(value.length === 0) {
            ret = 'This field is required'
        } else if(value.length >= 255){
            ret = `${reference} must be less than 255 characters`
        } else if(value.charAt(0) === '_') {
            ret = `${reference} must not start with _`
        } else if(value.charAt(0) === '-'){
            ret = `${reference} must not start with -`
        } else if(value.charAt(0) === '+') {
            ret = `${reference} must not start with +`
        } else if(value.charAt(0) === '.') {
            ret = `${reference} must not start with .`
        } else if(this.illegalStructureNameChars.test(value)) {
            ret = `${reference} must not contain these characters .. \\ / * ? \ < > | , # : ; + = ( ) { } or spaces`
        }
        return ret
    }
}