/**
 * I copied this here a workaround till I can fix the CLI
 */
export class DataInsightsComponent {
    /**
     * Unique identifier for this component.
     */
    public id!: string;
    /**
     * Human-readable name for the component.
     */
    public name!: string;
    /**
     * Description of what this component visualizes.
     */
    public description!: string;
    /**
     * When this component was last modified.
     */
    public modifiedAt!: string;
    /**
     * The complete HTML source code for the web component.
     * This includes the JavaScript class definition and customElements.define() call.
     */
    public rawHtml!: string;
    /**
     * The application ID this component belongs to.
     */
    public applicationId!: string;
    /**
     * Whether this component supports date range filtering.
     * Components that return true will subscribe to global date range changes.
     */
    public supportsDateRangeFiltering?: boolean;
}
