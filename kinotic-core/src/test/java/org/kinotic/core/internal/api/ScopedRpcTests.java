package org.kinotic.core.internal.api;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.annotations.ScopeOptional;
import org.kinotic.core.api.exceptions.RpcInvocationException;
import org.kinotic.core.api.exceptions.RpcMissingServiceException;
import org.kinotic.core.internal.api.support.AffineRpcTestServiceUnscopedProxy;
import org.kinotic.core.internal.api.support.ScopedRpcTestService;
import org.kinotic.core.internal.api.support.ScopedRpcTestServiceProxy;
import org.kinotic.core.internal.api.support.ScopedRpcTestServiceUnscopedProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

/**
 * Invocation of scoped services over both of their addresses: the scoped address always
 * serves, the shared unscoped address exists only when the service declares
 * {@link ScopeOptional} methods and serves only those.
 */
@SpringBootTest
@ActiveProfiles({"test"})
public class ScopedRpcTests {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because continuum wires them..
    @Autowired
    private ScopedRpcTestServiceProxy scopedProxy;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because continuum wires them..
    @Autowired
    private ScopedRpcTestServiceUnscopedProxy unscopedProxy;
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // these are not detected because continuum wires them..
    @Autowired
    private AffineRpcTestServiceUnscopedProxy affineUnscopedProxy;

    @Test
    public void scopedInvocationReachesTheNamedInstance() {
        StepVerifier.create(scopedProxy.instanceValue(ScopedRpcTestService.NODE_ID))
                    .expectNext(ScopedRpcTestService.INSTANCE_VALUE)
                    .verifyComplete();
    }

    @Test
    public void scopeOptionalMethodAnswersScopedInvocationsToo() {
        StepVerifier.create(scopedProxy.anyInstanceValue(ScopedRpcTestService.NODE_ID))
                    .expectNext(ScopedRpcTestService.ANY_INSTANCE_VALUE)
                    .verifyComplete();
    }

    @Test
    public void scopeOptionalMethodAnswersOnTheUnscopedAddress() {
        StepVerifier.create(unscopedProxy.anyInstanceValue())
                    .expectNext(ScopedRpcTestService.ANY_INSTANCE_VALUE)
                    .verifyComplete();
    }

    @Test
    public void instanceAffineMethodRejectsUnscopedInvocation() {
        StepVerifier.create(unscopedProxy.instanceValue())
                    .expectError(RpcInvocationException.class)
                    .verify();
    }

    @Test
    public void serviceWithoutScopeOptionalMethodsListensOnNoUnscopedAddress() {
        StepVerifier.create(affineUnscopedProxy.instanceValue())
                    .expectError(RpcMissingServiceException.class)
                    .verify();
    }

}
