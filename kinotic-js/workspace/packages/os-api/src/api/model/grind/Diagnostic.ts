import { DiagnosticLevel } from '@/api/model/grind/DiagnosticLevel'

/**
 * The value of a DIAGNOSTIC Result: a leveled message describing something that happened
 * while a step executed.
 */
export class Diagnostic {

    /**
     * Severity of the message.
     */
    public diagnosticLevel: DiagnosticLevel = DiagnosticLevel.NONE

    /**
     * What happened.
     */
    public message: string = ''

}
