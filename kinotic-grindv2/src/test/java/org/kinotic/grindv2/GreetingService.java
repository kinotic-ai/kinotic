package org.kinotic.grindv2;

/**
 * An application bean injected into steps-class constructors.
 */
public class GreetingService {

    public String greet(String who) {
        return "hello " + who;
    }

}
