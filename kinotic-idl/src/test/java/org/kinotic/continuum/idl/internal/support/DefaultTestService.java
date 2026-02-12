

package org.kinotic.continuum.idl.internal.support;

import org.springframework.stereotype.Component;

/**
 *
 * Created by navid on 12/23/19
 */
@Component
public class DefaultTestService implements TestService{

    @Override
    public String helloWorld() {
        return "wat";
    }

    @Override
    public String hello(String who) {
        return "Hello "+who;
    }

    @Override
    public TestObject test() {
        return new TestObject("Bob", 42, true);
    }
}
