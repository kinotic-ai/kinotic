/** How a {@link SignInMethod} authenticates its account. */
export enum SignInMethodKind {
    /** The account's password. */
    PASSWORD = 'PASSWORD',
    /** An identity a provider asserts, bound to the account. */
    OIDC = 'OIDC'
}
