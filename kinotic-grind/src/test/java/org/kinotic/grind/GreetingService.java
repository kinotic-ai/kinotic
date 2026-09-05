package org.kinotic.grind;

/**
 * An application bean injected into tasks-class constructors.
 */
public class GreetingService {

    public String greet(String who) {
        return "hello " + who;
    }

}
