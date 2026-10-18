package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.Member;
import org.kinotic.domain.api.model.security.identity.Membership;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

import java.util.List;

/**
 * The {@link Membership}s of accounts in organizations and in the platform. Checks no participant: the
 * callers, the login routes and the member management services, hold the scope they act in.
 */
public interface MembershipService {

    /**
     * Makes account {@code identityId} a member of organization {@code organizationId}, or of the platform
     * when it is {@code null}, enabled. An account already a member keeps its membership as it is.
     *
     * @return the membership, readable at once
     */
    Future<Membership> addMember(String identityId, String organizationId);

    /**
     * The membership of account {@code identityId} in organization {@code organizationId}, or in the
     * platform when it is {@code null}; {@code null} when the account is not a member.
     */
    Future<Membership> findMembership(String identityId, String organizationId);

    /** Every membership of account {@code identityId}, disabled ones included, oldest first. */
    Future<List<Membership>> findMemberships(String identityId);

    /**
     * The members of organization {@code organizationId}, or of the platform when it is {@code null},
     * each as the account joined with its membership.
     */
    Future<Page<Member>> findMembers(String organizationId, Pageable pageable);

    /**
     * The members of the scope whose email or display name matches {@code searchText}; blank text lists
     * them all as {@link #findMembers} does.
     */
    Future<Page<Member>> searchMembers(String searchText, String organizationId, Pageable pageable);

    /**
     * The participant {@code account} signs in to organization {@code organizationId} as, through its
     * membership of it, or {@code null} when it holds none; {@code organizationId} null is the platform. Every
     * login, invitation and token that admits an account to a scope resolves it here.
     *
     * @return fails with {@link org.kinotic.domain.api.exceptions.AccountDisabledException} when the account
     *         or its membership is disabled
     */
    Future<Participant> resolveMember(UserParticipantIdentity account, String organizationId);

    /**
     * Enables or disables account {@code identityId}'s membership of the scope. A disabled membership
     * refuses the scope's logins while the account and its other memberships are untouched.
     *
     * @return fails with {@link IllegalArgumentException} when the account is not a member
     */
    Future<Void> setMemberEnabled(String identityId, String organizationId, boolean enabled);

    /**
     * Removes account {@code identityId}'s membership of the scope, and the delegates the account
     * authorized in it. The account itself and its other memberships remain. Under an organization's own
     * configuration with {@code createUserOnSignIn}, the provider decides who is a member: an account it
     * still verifies joins again on its next sign-in, so to keep someone out, disable the membership
     * instead, or remove them at the provider.
     *
     * @return fails with {@link IllegalArgumentException} when the account is not a member
     */
    Future<Void> removeMember(String identityId, String organizationId);
}
