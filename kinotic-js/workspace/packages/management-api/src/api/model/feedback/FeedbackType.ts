/**
 * Whether feedback reports a problem with Kinotic or asks for something Kinotic does not do yet.
 */
export enum FeedbackType {
    /**
     * Something in Kinotic does not work as it should.
     */
    BUG_REPORT = 'BUG_REPORT',
    /**
     * Kinotic lacks something the sender needs.
     */
    FEATURE_REQUEST = 'FEATURE_REQUEST'
}
