package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.security.ApplicationOidcConfiguration;
import org.kinotic.domain.api.model.security.ApplicationSocialOidcConfiguration;
import org.kinotic.domain.api.model.security.ManagedOidcConfiguration;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OidcProviderKind;
import org.kinotic.domain.api.model.security.OrganizationOidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.TenantOidcConfiguration;

import java.util.List;
import java.util.Collection;

/**
 * The OIDC provider configurations users sign up and in through: the platform's managed registrations
 * and each organization's own, each made for one sign-in scope. A write is held to its kind's owner: a
 * {@link ManagedOidcConfiguration} is saved or deleted by a system participant only, an
 * {@link OwnedOidcConfiguration} by an organization participant of the organization it names, and an
 * {@link ApplicationOidcConfiguration} must name an application of that organization. An organization's
 * configuration must name a provider kind {@link OidcProviderKind#offeredToOrganizations() offered to
 * organizations} and its account there in the form the kind expects; its authority is derived from the
 * two on every save. The finders that serve the sign-in flows, which run before a participant is bound,
 * check no participant and say so.
 */
public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {

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
    Future<OrgSignupOidcConfiguration> findEnabledOrgSignupByProvider(OidcProviderKind provider);

    /**
     * The org sign-up registration {@code id}, enabled or not, or {@code null} when no
     * {@link OrgSignupOidcConfiguration} has that id, for the social callbacks. Checks no participant.
     *
     * @param id the configuration id
     */
    Future<OrgSignupOidcConfiguration> findOrgSignupById(String id);

    /**
     * The enabled configurations a sign-in scope offers, in the order to present them. The organization's
     * own login ({@code applicationId} null) offers the org sign-up registrations followed by the
     * organization's {@link OrganizationOidcConfiguration}s; an application's login offers its
     * {@link ApplicationOidcConfiguration}s followed by the platform's
     * {@link ApplicationSocialOidcConfiguration}s whose provider kinds the application names; the login of
     * one tenant of the application offers the tenant's {@link TenantOidcConfiguration}s followed by what
     * the application's login offers. Empty when the scope offers none. Checks no participant.
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
     * configuration: an {@link OwnedOidcConfiguration} the organization made for the scope, an
     * {@link ApplicationSocialOidcConfiguration} the application names, or at an organization's own login
     * any {@link OrgSignupOidcConfiguration}, which every organization's login offers. A tenant's login
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
     * The configuration {@code id} organization {@code organizationId} owns, of any owned kind, enabled or
     * not, or {@code null} when the organization owns none by that id. Checks no participant.
     *
     * @param id             the configuration id
     * @param organizationId the organization that owns the configuration
     */
    Future<OwnedOidcConfiguration> findOwnedById(String id, String organizationId);

    /**
     * Saves the configuration as {@link #save(Object)} does and makes {@code clientSecret} the secret it
     * sends its provider, replacing the one it had. The secret is held in the owning organization's scope
     * of the secret storage, keyed by the configuration's id, so it is never a field of the configuration
     * and no other organization's configuration can read it; the configuration's
     * {@code confidentialClient} records whether one is held. A sign-in through the configuration picks
     * the new secret up on its next flow.
     *
     * @param configuration the configuration, owned by the caller's organization
     * @param clientSecret  the client secret's value, or {@code null} or blank for a public client, whose
     *                      stored secret is deleted
     * @return the configuration as saved
     */
    <C extends OwnedOidcConfiguration> Future<C> save(C configuration, String clientSecret);

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
