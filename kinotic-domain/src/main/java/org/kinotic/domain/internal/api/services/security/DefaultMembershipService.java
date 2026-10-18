package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.exceptions.AccountDisabledException;
import org.kinotic.domain.api.model.security.Member;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.Membership;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.MembershipService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.repositories.MembershipRepository;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DefaultMembershipService implements MembershipService {

    // the most delegates one account authorized in one scope, removed with its membership
    private static final int MAX_DELEGATES = 1000;

    private final MembershipRepository membershipRepository;
    private final ParticipantIdentityRepository identityRepository;

    @Override
    public Future<Membership> addMember(String identityId, String organizationId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return findMembership(identityId, organizationId).compose(existing -> {
            Future<Membership> ret;
            if (existing != null) {
                ret = Future.succeededFuture(existing);
            } else {
                Date now = new Date();
                Membership membership = new Membership().setId(Membership.idOf(identityId, organizationId))
                                                        .setIdentityId(identityId)
                                                        .setOrganizationId(organizationId)
                                                        .setEnabled(true)
                                                        .setCreated(now)
                                                        .setUpdated(now);
                // sync so the member list a console re-queries at once shows the new member
                ret = membershipRepository.saveSync(membership);
            }
            return ret;
        });
    }

    @Override
    public Future<Membership> findMembership(String identityId, String organizationId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return membershipRepository.findById(Membership.idOf(identityId, organizationId));
    }

    @Override
    public Future<List<Membership>> findMemberships(String identityId) {
        return membershipRepository.findByIdentity(identityId);
    }

    @Override
    public Future<Page<Member>> findMembers(String organizationId, Pageable pageable) {
        return membershipRepository.findByOrganization(organizationId, pageable)
                                   .compose(page -> identityRepository.findAllById(page.getContent().stream().map(Membership::getIdentityId).toList())
                                                                      .map(accounts -> members(page, accounts)));
    }

    // the page's memberships joined with their accounts, in the page's order; a membership whose account is gone is left out
    private static Page<Member> members(Page<Membership> page, List<ParticipantIdentity> accounts) {
        Map<String, UserParticipantIdentity> byId = accounts.stream()
                                                            .filter(UserParticipantIdentity.class::isInstance)
                                                            .map(UserParticipantIdentity.class::cast)
                                                            .collect(Collectors.toMap(ParticipantIdentity::getId, Function.identity()));
        List<Member> members = page.getContent().stream()
                                   .filter(membership -> byId.containsKey(membership.getIdentityId()))
                                   .map(membership -> member(byId.get(membership.getIdentityId()), membership))
                                   .toList();
        return new Page<>(members, page.getTotalElements());
    }

    private static Member member(UserParticipantIdentity account, Membership membership) {
        return new Member(account.getId(), account.getEmail(), account.getDisplayName(), membership.isEnabled(), membership.getCreated());
    }

    @Override
    public Future<Page<Member>> searchMembers(String searchText, String organizationId, Pageable pageable) {
        Future<Page<Member>> ret;
        if (searchText == null || searchText.isBlank()) {
            ret = findMembers(organizationId, pageable);
        } else {
            // the text matches accounts, so the search is held to the organization's members by their ids and
            // every page, and the total, counts members only
            ret = membershipRepository.findAllByOrganization(organizationId)
                                      .compose(memberships -> {
                                          Map<String, Membership> byIdentity = memberships.stream()
                                                  .collect(Collectors.toMap(Membership::getIdentityId, Function.identity()));
                                          return identityRepository.searchUsersById(searchText, byIdentity.keySet(), pageable)
                                                                   .map(accounts -> accounts.map(account -> member(account, byIdentity.get(account.getId()))));
                                      });
        }
        return ret;
    }

    @Override
    public Future<Participant> resolveMember(UserParticipantIdentity account, String organizationId) {
        Validate.notNull(account, "account cannot be null");
        Validate.isTrue(account.isAccount(), "%s is not an account", account.getId());
        Future<Participant> ret;
        if (Membership.PLATFORM.equals(organizationId)) {
            // the platform membership's id slot is no organization's: a login addressed to it holds no membership,
            // so an operator's password at an organization login never yields the platform's participant
            ret = Future.succeededFuture();
        } else {
            ret = membershipRepository.findById(Membership.idOf(account.getId(), organizationId)).map(membership -> {
                Participant participant = null;
                if (membership != null) {
                    if (!account.isEnabled() || !membership.isEnabled()) {
                        throw new AccountDisabledException("This account cannot sign in to the organization.");
                    }
                    participant = DomainUtil.createParticipant(account, membership);
                }
                return participant;
            });
        }
        return ret;
    }

    @Override
    public Future<Void> setMemberEnabled(String identityId, String organizationId, boolean enabled) {
        return requireMembership(identityId, organizationId)
                // sync so the console's immediate re-query sees the change
                .compose(membership -> membershipRepository.saveSync(membership.setEnabled(enabled).setUpdated(new Date())))
                .mapEmpty();
    }

    @Override
    public Future<Void> removeMember(String identityId, String organizationId) {
        return requireMembership(identityId, organizationId)
                .compose(membership -> identityRepository.findDelegatesByOwner(identityId, organizationId, null,
                                                                               Pageable.ofSize(MAX_DELEGATES)))
                // a delegate wields the membership's authority, so it does not outlive the membership
                .compose(delegates -> Future.all(delegates.getContent().stream()
                                                          .map(DelegatingParticipantIdentity::getId)
                                                          .map(identityRepository::deleteById)
                                                          .toList()))
                // sync so the console's immediate re-query no longer shows the member
                .compose(v -> membershipRepository.deleteByIdSync(Membership.idOf(identityId, organizationId)));
    }

    private Future<Membership> requireMembership(String identityId, String organizationId) {
        return findMembership(identityId, organizationId).map(membership -> {
            Validate.isTrue(membership != null, "Member not found.");
            return membership;
        });
    }
}
