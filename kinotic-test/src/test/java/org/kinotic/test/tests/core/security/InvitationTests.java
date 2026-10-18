package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.exceptions.AccountDisabledException;
import org.kinotic.domain.api.exceptions.ExistingAccountException;
import org.kinotic.domain.api.model.security.Member;
import org.kinotic.domain.api.model.security.PendingInvite;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.MembershipService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invitations into an organization under the account model: an invitation is for someone who is not yet a
 * member, and accepting one signs in only an account that may sign in to the organization. Also covers the
 * member search, which must list the organization's members alone.
 */
@SpringBootTest
public class InvitationTests extends KinoticTestBase {

    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private InviteService invites;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private MembershipService memberships;

    @Test
    public void anInvitationIsRefusedForAnEmailAlreadyAMemberOfTheOrganization() throws Exception {
        String email = "member-" + suffix() + "@example.test";
        UserParticipantIdentity account = await(identities.createUser(account(email, "Existing member"), PASSWORD));
        await(memberships.addMember(account.getId(), TEST_ORG_ID));

        Throwable refusal = failure(() -> invites.createInvite(invite(email)));
        assertInstanceOf(IllegalArgumentException.class, refusal);
        assertTrue(refusal.getMessage().contains("already exists in this organization"), refusal.getMessage());
    }

    @Test
    public void anInvitationAcceptedByADisabledMemberSignsNoOneIn() throws Exception {
        String email = "disabled-" + suffix() + "@example.test";
        PendingInvite invite = await(invites.createInvite(invite(email)));
        // the account joins and is disabled in the organization while the invitation is still out
        UserParticipantIdentity account = await(identities.createUser(account(email, "Disabled member"), PASSWORD));
        await(memberships.addMember(account.getId(), TEST_ORG_ID));
        await(memberships.setMemberEnabled(account.getId(), TEST_ORG_ID, false));

        String token = invite.getVerificationToken();
        assertInstanceOf(ExistingAccountException.class, failure(() -> invites.acceptLocalInvite(token, "not-the-password", null)));
        assertInstanceOf(AccountDisabledException.class, failure(() -> invites.acceptLocalInvite(token, PASSWORD, null)));
        // a refused acceptance does not consume the invitation
        assertNotNull(await(invites.getValidInvite(token)));
    }

    @Test
    public void memberSearchIsHeldToTheOrganizationsMembers() throws Exception {
        // one token, so the search matches these two accounts and nothing another test created
        String name = "zq" + suffix();
        UserParticipantIdentity member = await(identities.createUser(account("zq-member-" + suffix() + "@example.test", name), PASSWORD));
        await(identities.createUser(account("zq-other-" + suffix() + "@example.test", name), PASSWORD));
        await(memberships.addMember(member.getId(), TEST_ORG_ID));

        // the search reads the index, which sees a write only once it has refreshed
        Page<Member> found = awaitUntil(() -> memberships.searchMembers(name, TEST_ORG_ID, Pageable.ofSize(10)),
                                        page -> !page.getContent().isEmpty());
        assertEquals(List.of(member.getId()), found.getContent().stream().map(Member::id).toList());
        assertEquals(1, found.getTotalElements());
    }

    private static PendingInvite invite(String email) {
        PendingInvite invite = new PendingInvite();
        invite.setEmail(email);
        invite.setDisplayName("Invitee");
        invite.setOrganizationId(TEST_ORG_ID);
        invite.setInvitedByName("Invitation tests");
        return invite;
    }

    private static UserParticipantIdentity account(String email, String displayName) {
        UserParticipantIdentity account = new UserParticipantIdentity();
        account.setEmail(email);
        account.setDisplayName(displayName);
        return account;
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(runAsOrganization(call::get))).getCause();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private static <T> T awaitUntil(Supplier<Future<T>> call, Predicate<T> done) throws Exception {
        T ret = await(call.get());
        for (int attempt = 0; attempt < 20 && !done.test(ret); attempt++) {
            Thread.sleep(500);
            ret = await(call.get());
        }
        return ret;
    }
}
