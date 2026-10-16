/**
 * What a console renders in a task's detail pane: the component registered under kind, given data.
 * A task's record keeps the latest view it showed; its events carry each new one.
 */
export interface TaskView<T = unknown> {

    /**
     * The key of the component that renders the view, one of TaskViews.
     */
    readonly kind: string

    /**
     * What the component renders.
     */
    readonly data: T

}
