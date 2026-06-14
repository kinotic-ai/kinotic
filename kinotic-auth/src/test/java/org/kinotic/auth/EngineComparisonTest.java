package org.kinotic.auth;

import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.casbin.CasbinAuthorizationService;
import org.kinotic.auth.cedar.CedarAuthorizationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Side-by-side comparison of the Cedar and jCasbin {@link AuthorizationEngine} implementations:
 * the same ABAC policies and requests are evaluated by both, asserting they reach identical
 * decisions (and the expected ones), and reporting per-engine evaluation throughput.
 */
class EngineComparisonTest {

    private record Scenario(String action, String expression, List<Case> cases) {}

    private record Case(String description,
                        String principalAttributesJson,
                        String argumentsJson,
                        List<String> parameterNames,
                        boolean expected) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("placeOrder",
                    "participant.roles contains 'finance' and order.amount < 50000",
                    List.of(
                            new Case("finance under limit", "{\"roles\":[\"finance\"]}", "[{\"amount\":25000}]", List.of("order"), true),
                            new Case("finance over limit", "{\"roles\":[\"finance\"]}", "[{\"amount\":75000}]", List.of("order"), false),
                            new Case("wrong role", "{\"roles\":[\"engineering\"]}", "[{\"amount\":25000}]", List.of("order"), false))),

            new Scenario("transferFunds",
                    "participant.roles contains 'finance' and transfer.amount <= participant.transferLimit and transfer.currency == 'USD' and approval.approved == true",
                    List.of(
                            new Case("all conditions met", "{\"roles\":[\"finance\"],\"transferLimit\":100000}", "[{\"amount\":50000,\"currency\":\"USD\"},{\"approved\":true}]", List.of("transfer", "approval"), true),
                            new Case("over transfer limit", "{\"roles\":[\"finance\"],\"transferLimit\":100000}", "[{\"amount\":150000,\"currency\":\"USD\"},{\"approved\":true}]", List.of("transfer", "approval"), false),
                            new Case("wrong currency", "{\"roles\":[\"finance\"],\"transferLimit\":100000}", "[{\"amount\":50000,\"currency\":\"EUR\"},{\"approved\":true}]", List.of("transfer", "approval"), false),
                            new Case("not approved", "{\"roles\":[\"finance\"],\"transferLimit\":100000}", "[{\"amount\":50000,\"currency\":\"USD\"},{\"approved\":false}]", List.of("transfer", "approval"), false))),

            new Scenario("viewReport",
                    "participant.roles contains 'admin' or participant.roles contains 'manager'",
                    List.of(
                            new Case("admin", "{\"roles\":[\"admin\"]}", "[{}]", List.of("params"), true),
                            new Case("manager", "{\"roles\":[\"manager\"]}", "[{}]", List.of("params"), true),
                            new Case("regular user", "{\"roles\":[\"user\"]}", "[{}]", List.of("params"), false))),

            new Scenario("viewDoc",
                    "doc.status in ['active', 'pending']",
                    List.of(
                            new Case("active", "{}", "[{\"status\":\"active\"}]", List.of("doc"), true),
                            new Case("pending", "{}", "[{\"status\":\"pending\"}]", List.of("doc"), true),
                            new Case("archived", "{}", "[{\"status\":\"archived\"}]", List.of("doc"), false))),

            new Scenario("contactUser",
                    "user.email like '*@kinotic.ai'",
                    List.of(
                            new Case("matching domain", "{}", "[{\"email\":\"navid@kinotic.ai\"}]", List.of("user"), true),
                            new Case("other domain", "{}", "[{\"email\":\"navid@gmail.com\"}]", List.of("user"), false))),

            new Scenario("approveDoc",
                    "doc.approvedBy exists",
                    List.of(
                            new Case("approver present", "{}", "[{\"approvedBy\":\"mgr-1\"}]", List.of("doc"), true),
                            new Case("approver absent", "{}", "[{\"title\":\"q3\"}]", List.of("doc"), false)))
    );

    private static void registerAll(AuthorizationEngine engine) {
        for (Scenario scenario : SCENARIOS) {
            engine.registerPolicy(scenario.action(), scenario.expression());
        }
    }

    private static AuthorizationRequest requestFor(Scenario scenario, Case c) {
        return new AuthorizationRequest("user-1", c.principalAttributesJson(),
                scenario.action(), c.argumentsJson(), c.parameterNames());
    }

    @Test
    void enginesAgreeAndAreCorrect() {
        AuthorizationEngine cedar = new CedarAuthorizationService();
        AuthorizationEngine casbin = new CasbinAuthorizationService();
        registerAll(cedar);
        registerAll(casbin);

        for (Scenario scenario : SCENARIOS) {
            for (Case c : scenario.cases()) {
                AuthorizationRequest request = requestFor(scenario, c);
                boolean cedarDecision = cedar.isAuthorized(request);
                boolean casbinDecision = casbin.isAuthorized(request);

                String label = scenario.action() + " / " + c.description();
                assertEquals(c.expected(), cedarDecision, "Cedar reached the wrong decision: " + label);
                assertEquals(c.expected(), casbinDecision, "jCasbin reached the wrong decision: " + label);
                assertEquals(cedarDecision, casbinDecision, "Engines disagree: " + label);
            }
        }
    }

    @Test
    void throughputComparison() {
        AuthorizationEngine cedar = new CedarAuthorizationService();
        AuthorizationEngine casbin = new CasbinAuthorizationService();
        registerAll(cedar);
        registerAll(casbin);

        List<AuthorizationRequest> requests = SCENARIOS.stream()
                .flatMap(s -> s.cases().stream().map(c -> requestFor(s, c)))
                .toList();

        // Each eval re-parses the request JSON, so these numbers reflect the full first-pass
        // integration cost (parse + evaluate), not the engines' raw matcher speed in isolation.
        int warmup = 200;
        int iterations = 500;
        long evaluations = (long) iterations * requests.size();

        report("Cedar  ", time(cedar, requests, warmup, iterations), evaluations);
        report("jCasbin", time(casbin, requests, warmup, iterations), evaluations);
    }

    private static long time(AuthorizationEngine engine, List<AuthorizationRequest> requests, int warmup, int iterations) {
        for (int i = 0; i < warmup; i++) {
            for (AuthorizationRequest request : requests) {
                engine.isAuthorized(request);
            }
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (AuthorizationRequest request : requests) {
                engine.isAuthorized(request);
            }
        }
        return System.nanoTime() - start;
    }

    private static void report(String name, long nanos, long evaluations) {
        double microsPerEval = nanos / 1000.0 / evaluations;
        double perSecond = evaluations / (nanos / 1_000_000_000.0);
        System.out.printf("ENGINE %s : %,d evals in %,d ms -> %.2f us/eval, %,.0f evals/sec%n",
                name, evaluations, nanos / 1_000_000, microsPerEval, perSecond);
    }
}
