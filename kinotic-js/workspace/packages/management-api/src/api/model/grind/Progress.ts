/**
 * The progress of a grind job definition or task.
 */
export class Progress {

    /**
     * How complete the work is, 0 to 100.
     */
    public percentageComplete: number = 0

    /**
     * Describes the work currently being performed.
     */
    public message: string = ''

}
