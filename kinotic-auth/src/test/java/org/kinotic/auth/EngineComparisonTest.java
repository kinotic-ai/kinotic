package org.kinotic.auth;

import org.junit.jupiter.api.Test;
import org.kinotic.auth.api.engine.AuthorizationEngine;
import org.kinotic.auth.api.engine.AuthorizationRequest;
import org.kinotic.auth.casbin.CasbinAuthorizationService;
import org.kinotic.auth.casbin.PreparsedAviatorEngine;
import org.kinotic.auth.cedar.CedarAuthorizationService;
import org.kinotic.auth.engines.CelEngine;
import org.kinotic.auth.engines.ElEngine;
import org.kinotic.auth.engines.JaninoEngine;
import org.kinotic.auth.engines.Jexl3Engine;
import org.kinotic.auth.engines.PreparsedEngine;
import org.kinotic.auth.engines.RequestJson;
import org.kinotic.auth.engines.SpelEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Side-by-side comparison of the authorization engines: the same ABAC policies and requests are
 * evaluated by every engine, asserting each reaches the expected decision (so all engines agree),
 * then reporting throughput for the full path (request JSON parsed per evaluation) and, where an
 * engine exposes one, the pre-parsed path that isolates evaluator cost.
 */
class EngineComparisonTest {

    private record Scenario(String action, String expression, List<Case> cases) {}

    private record Case(String description,
                        String principalAttributesJson,
                        String argumentsJson,
                        List<String> parameterNames,
                        boolean expected) {}

    /** One engine under test: its full-path implementation and, when it has one, its pre-parsed entry point. */
    private record Candidate(String name, AuthorizationEngine full, PreparsedEngine preparsed) {}

    private record PreparsedCase(String action,
                                 Map<String, Object> subject,
                                 Map<String, Object> arguments,
                                 boolean expected,
                                 String label) {}

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("placeOrder",
                    "participant.roles contains 'finance' and order.amount < 50000",
                    List.of(
                            new Case("finance under limit", "{\"roles\":[\"finance\"]}", "[{\"amount\":25000}]", List.of("order"), true),
                            new Case("finance over limit", "{\"roles\":[\"finance\"]}", "[{\"amount\":75000}]", List.of("order"), false),
                            new Case("wrong role", "{\"roles\":[\"engineering\"]}", "[{\"amount\":25000}]", List.of("order"), false),
                            new Case("amount missing must deny", "{\"roles\":[\"finance\"]}", "[{\"department\":\"sales\"}]", List.of("order"), false))),

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

    private static List<Candidate> candidates() {
        PreparsedAviatorEngine aviator = new PreparsedAviatorEngine();
        CelEngine cel = new CelEngine();
        SpelEngine spel = new SpelEngine();
        Jexl3Engine jexl = new Jexl3Engine();
        JaninoEngine janino = new JaninoEngine();
        ElEngine el = new ElEngine();
        List<Candidate> ret = List.of(
                new Candidate("Cedar (JNI)", new CedarAuthorizationService(), null),
                new Candidate("Aviator (jCasbin core)", new CasbinAuthorizationService(), aviator),
                new Candidate("CEL (Google)", cel, cel),
                new Candidate("SpEL (Spring)", spel, spel),
                new Candidate("JEXL 3 (Apache)", jexl, jexl),
                new Candidate("Janino", janino, janino),
                new Candidate("Jakarta EL (Tomcat)", el, el));
        for (Scenario scenario : SCENARIOS) {
            for (Candidate candidate : ret) {
                candidate.full().registerPolicy(scenario.action(), scenario.expression());
            }
            aviator.registerPolicy(scenario.action(), scenario.expression());
        }
        return ret;
    }

    private static AuthorizationRequest requestFor(Scenario scenario, Case c) {
        return new AuthorizationRequest("user-1", c.principalAttributesJson(),
                scenario.action(), c.argumentsJson(), c.parameterNames());
    }

    private static List<PreparsedCase> preparsedCases() {
        List<PreparsedCase> ret = new ArrayList<>();
        for (Scenario scenario : SCENARIOS) {
            for (Case c : scenario.cases()) {
                AuthorizationRequest request = requestFor(scenario, c);
                ret.add(new PreparsedCase(scenario.action(), RequestJson.subject(request), RequestJson.arguments(request),
                        c.expected(), scenario.action() + " / " + c.description()));
            }
        }
        return ret;
    }

    @Test
    void enginesAgreeAndAreCorrect() {
        List<Candidate> candidates = candidates();
        for (Scenario scenario : SCENARIOS) {
            for (Case c : scenario.cases()) {
                AuthorizationRequest request = requestFor(scenario, c);
                String label = scenario.action() + " / " + c.description();
                for (Candidate candidate : candidates) {
                    assertEquals(c.expected(), candidate.full().isAuthorized(request),
                            candidate.name() + " reached the wrong decision: " + label);
                }
            }
        }
        List<PreparsedCase> preparsed = preparsedCases();
        for (Candidate candidate : candidates) {
            if (candidate.preparsed() != null) {
                for (PreparsedCase p : preparsed) {
                    assertEquals(p.expected(), candidate.preparsed().isAllowed(p.action(), p.subject(), p.arguments()),
                            candidate.name() + " (pre-parsed) reached the wrong decision: " + p.label());
                }
            }
        }
    }

    @Test
    void throughputComparison() {
        List<Candidate> candidates = candidates();
        List<AuthorizationRequest> requests = SCENARIOS.stream()
                .flatMap(s -> s.cases().stream().map(c -> requestFor(s, c)))
                .toList();
        List<PreparsedCase> preparsed = preparsedCases();

        // Full path: every engine runs its production entry point, parsing the request JSON per eval.
        int warmup = 200;
        int iterations = 500;
        long evaluations = (long) iterations * requests.size();
        for (Candidate candidate : candidates) {
            report("ENGINE     ", candidate.name(), timeFull(candidate.full(), requests, warmup, iterations), evaluations);
        }

        // Pre-parsed path isolates the evaluator; it is fast enough to need more iterations for stable figures.
        int preparsedIterations = 5000;
        long preparsedEvaluations = (long) preparsedIterations * preparsed.size();
        for (Candidate candidate : candidates) {
            if (candidate.preparsed() != null) {
                report("ENGINE-ONLY", candidate.name(), timePreparsed(candidate.preparsed(), preparsed, warmup, preparsedIterations), preparsedEvaluations);
            }
        }
        for (Candidate candidate : candidates) {
            if (candidate.full() instanceof SpelEngine spel) {
                System.out.println("SPEL compiled to bytecode per action: " + spel.compileAll());
            }
        }
    }

    private static long timeFull(AuthorizationEngine engine, List<AuthorizationRequest> requests, int warmup, int iterations) {
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

    private static long timePreparsed(PreparsedEngine engine, List<PreparsedCase> cases, int warmup, int iterations) {
        for (int i = 0; i < warmup; i++) {
            for (PreparsedCase c : cases) {
                engine.isAllowed(c.action(), c.subject(), c.arguments());
            }
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            for (PreparsedCase c : cases) {
                engine.isAllowed(c.action(), c.subject(), c.arguments());
            }
        }
        return System.nanoTime() - start;
    }

    private static void report(String path, String name, long nanos, long evaluations) {
        double microsPerEval = nanos / 1000.0 / evaluations;
        double perSecond = evaluations / (nanos / 1_000_000_000.0);
        System.out.printf("%s %-22s : %,d evals in %,d ms -> %.2f us/eval, %,.0f evals/sec%n",
                path, name, evaluations, nanos / 1_000_000, microsPerEval, perSecond);
    }
}
