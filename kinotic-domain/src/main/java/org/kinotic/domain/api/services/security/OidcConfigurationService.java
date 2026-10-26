package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.ClientRegistrationType;
import org.kinotic.domain.api.model.security.ManagedOidcConfiguration;
import org.kinotic.domain.api.model.security.ManagedSignIn;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;

import java.util.List;
import java.util.Collection;

/**
 * The OIDC provider configurations users sign up and in through: the system's managed registrations
 * and each organization's own, each made for one sign-in scope. A write is held to its kind's owner: a
 * {@link ManagedOidcConfiguration} is saved or deleted by a system participant only, an
 * {@link OwnedOidcConfiguration} by an organization participant of the organization it names, and an
 * {@link ApplicationOidcConfiguration} must name an application of that organization. An organization's
 * configuration must name a provider kind {@link OidcProviderType#offeredToOrganizations() offered to
 * organizations} and its account there in the form the kind expects; its authority is derived from the
 * two on every save. An application's configuration that starts signing in through Kinotic's registration
 * ({@link ClientRegistrationType#MANAGED}) must name a provider kind the system holds an enabled
 * {@link ApplicationSocialOidcConfiguration} for, and needs no account or client of its own; once it does, it saves
 * whether or not the system still holds one. An enabled {@link ApplicationSocialOidcConfiguration} is refused while
 * another of its provider kind is enabled. The finders that serve the sign-in flows, which run before a participant
 * is bound, check no participant and say so.
 */
public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {

    /**
     * Whether {@code configuration} signs in through one of the system's registrations rather than a client of the
     * organization's own: an {@link ApplicationOidcConfiguration} using {@link ClientRegistrationType#MANAGED}, which
     * signs in through the {@link ApplicationSocialOidcConfiguration} of its provider kind.
     */
    static boolean isManaged(OidcConfiguration configuration) {
        return configuration instanceof ApplicationOidcConfiguration forApplication
                && forApplication.getClientRegistrationType() == ClientRegistrationType.MANAGED;
    }

    /** The configurations among {@code ids} that exist, of any kind, in one read. */
    Future<List<OidcConfiguration>> findAllById(Collection<String> ids);

    /**
     * Every enabled registration offered to organization sign-up — the source of the social button list
     * rendered on the organization signup and login pages. Checks no participant.
     */
    Future<List<OrgSignupOidcConfiguration>> findEnabledOrgSignupProviders();

    /**
     * The single enabled org sign-up registration of the given provider kind, or {@code null} when none
     * is configured, for the social-button start endpoints resolving the {@code :provider} path param.
     * Checks no participant.
     */
    Future<OrgSignupOidcConfiguration> findEnabledOrgSignupByProvider(OidcProviderType provider);

    /**
     * The org sign-up registration {@code id}, enabled or not, or {@code null} when no
     * {@link OrgSignupOidcConfiguration} has that id, for the social callbacks. Checks no participant.
     *
     * @param id the configuration id
     */
    Future<OrgSignupOidcConfiguration> findOrgSignupById(String id);

    /**
     * Every enabled registration the system holds for applications' sign-in, one per provider kind: the
     * registrations an {@link ApplicationOidcConfiguration} using {@link ClientRegistrationType#MANAGED} signs in
     * through. Checks no participant.
     */
    Future<List<ApplicationSocialOidcConfiguration>> findEnabledAppSocialRegistrations();

    /**
     * The enabled configurations a sign-in scope offers, in the order to present them. The organization's
     * own login ({@code applicationId} null) offers the org sign-up registrations followed by the
     * organization's {@link OrganizationOidcConfiguration}s; an application's login offers its
     * {@link ApplicationOidcConfiguration}s, one using {@link ClientRegistrationType#MANAGED} while the system
     * holds an enabled registration of its provider kind; the login of one tenant of the application offers
     * the tenant's {@link TenantOidcConfiguration}s followed by what the application's login offers. Empty
     * when the scope offers none. Checks no participant.
     *
     * @param organizationId the organization the scope belongs to
     * @param applicationId  the application within it, or {@code null} for the organization's own login
     * @param tenantId       the tenant of the application, or {@code null} for the application's own login
     */
    Future<List<OidcConfiguration>> findEnabledForScope(String organizationId, String applicationId, String tenantId);

    /** {@link #findEnabledForScope(String, String, String)} for a scope that is no tenant's. */
    default Future<List<OidcConfiguration>> findEnabledForScope(String organizationId, String applicationId) {
        return findEnabledForScope(organizationId, applicationId, null);
    }

    /**
     * The enabled configuration {@code configurationId} when the scope offers it, or {@code null} when the
     * scope does not offer it or it is disabled, for the sign-in starts and callbacks that name a
     * configuration: an {@link OwnedOidcConfiguration} the organization made for the scope, one using
     * {@link ClientRegistrationType#MANAGED} while the system holds an enabled registration of its kind, or at an
     * organization's own login any {@link OrgSignupOidcConfiguration}, which every organization's login offers. A tenant's login
     * offers what the application's offers as well as the tenant's own. Checks no participant.
     *
     * @param configurationId the configuration a sign-in names
     * @param organizationId  the organization the scope belongs to
     * @param applicationId   the application within it, or {@code null} for the organization's own login
     * @param tenantId        the tenant of the application, or {@code null} for the application's own login
     */
    Future<OidcConfiguration> findOffered(String configurationId, String organizationId, String applicationId, String tenantId);

    /** {@link #findOffered(String, String, String, String)} for a scope that is no tenant's. */
    default Future<OidcConfiguration> findOffered(String configurationId, String organizationId, String applicationId) {
        return findOffered(configurationId, organizationId, applicationId, null);
    }

    /**
     * The enabled configuration {@code configurationId} of application {@code applicationId} that signs in through
     * Kinotic's registration ({@link ClientRegistrationType#MANAGED}), with the system's enabled registration of its
     * provider kind, or {@code null} when the application has no such enabled configuration or the system holds no
     * enabled registration of its kind: a configuration {@link #findOffered} offers at the application's login and
     * every one of its tenants', with the registration it signs in through. Checks no participant.
     *
     * @param configurationId the configuration a sign-in names
     * @param organizationId  the organization the application belongs to
     * @param applicationId   the application whose login it is
     */
    Future<ManagedSignIn> findOfferedManaged(String configurationId, String organizationId, String applicationId);

    /**
     * The configuration {@code id} organization {@code organizationId} owns, of any owned kind, enabled or
     * not, or {@code null} when the organization owns none by that id. Checks no participant.
     *
     * @param id             the configuration id
     * @param organizationId the organization that owns the configuration
     */
    Future<OwnedOidcConfiguration> findOwnedById(String id, String organizationId);

    /**
     * The configurations organization {@code organizationId} owns, of every owned kind, enabled or not, oldest
     * first, a page at a time. Checks no participant.
     *
     * @param organizationId the organization that owns the configurations
     */
    Future<Page<OwnedOidcConfiguration>> findOwnedByOrganization(String organizationId, Pageable pageable);

    /**
     * Saves the configuration as {@link #save(Object)} does and, when {@code clientSecret} is given, makes it
     * the secret the configuration sends its provider, replacing the one it had. The secret is held in the
     * owning organization's scope of the secret storage, keyed by the configuration's id, so it is never a
     * field of the configuration and no other organization's configuration can read it; the configuration's
     * {@code confidentialClient} records whether one is held, whatever the configuration carried. A sign-in
     * through the configuration picks a new secret up on its next flow. Disabling an organization's own
     * configuration, as deleting one, fails with an {@link IllegalStateException} naming the enabled members it would
     * leave with no way to sign in to the organization.
     *
     * @param configuration the configuration, owned by the caller's organization
     * @param clientSecret  the client secret's value, or {@code null} or blank to keep the secret held for the
     *                      configuration, if any; a new configuration saved without one is a public client
     * @return the configuration as saved
     */
    <C extends OwnedOidcConfiguration> Future<C> save(C configuration, String clientSecret);

    /**
     * Deletes the client secret held for the caller's organization's configuration {@code id} and saves the
     * configuration as a public client, which sends its provider no secret on the code exchange.
     *
     * @param id the configuration, owned by the caller's organization
     * @return the configuration as saved, or {@code null} when the organization owns no configuration by that id
     */
    Future<OwnedOidcConfiguration> deleteClientSecret(String id);

    /**
     * Creates a copy of the caller's organization's configuration {@code sourceId} for another of its
     * sign-in scopes: the login of application {@code applicationId}, or the organization's own login when
     * {@code applicationId} is null. The copy is a new configuration of the target scope's kind with the
     * source's name, provider, account, client id and client secret, enabled, creating no users on sign-in
     * until told to. It has its own id and therefore its own callback URL, which the provider must be given
     * before a sign-in through it succeeds; a later change to either configuration leaves the other as it
     * is.
     *
     * @param sourceId      the configuration to copy, owned by the caller's organization
     * @param applicationId the application whose login the copy is offered at, or {@code null} for the
     *                      organization's own login
     * @return the copy as saved; fails when the source is not the organization's or the application is
     *         not the organization's
     */
    Future<OwnedOidcConfiguration> copy(String sourceId, String applicationId);

    /**
     * The client secret of {@code configuration}, or {@code null} for a public client, which the
     * configuration's {@code confidentialClient} tells without a read of the secret storage. Reads the
     * owning organization's scope of the secret storage only. Checks no participant.
     *
     * @param configuration the configuration as persisted, naming its organization
     */
    Future<String> findClientSecret(OwnedOidcConfiguration configuration);
}
