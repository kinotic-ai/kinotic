/**
 * The sequence of steps that were executed to get to a specific Result.
 */
export class StepInfo {

    /**
     * The sequence of the step that created the Result.
     */
    public sequence: number = 0

    /**
     * The enclosing step's info, or null for the run's root.
     */
    public ancestor: StepInfo | null = null

}

/**
 * The `/` separated sequence path from the run's root down to the step that created the
 * given info, matching the stepPath recorded on a TaskRecord.
 * @param info the step info of a received Result
 * @return the step path, e.g. "0/2/1"
 */
export function stepPathOf(info: StepInfo): string {
    let ret = ''
    for (let current: StepInfo | null = info; current !== null; current = current.ancestor) {
        ret = ret.length > 0 ? `${current.sequence}/${ret}` : String(current.sequence)
    }
    return ret
}
