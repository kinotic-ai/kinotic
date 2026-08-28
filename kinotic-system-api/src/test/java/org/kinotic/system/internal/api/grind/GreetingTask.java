package org.kinotic.system.internal.api.grind;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.Callable;

/**
 * A typed task class as used with {@code Tasks.fromClass}: dependencies arrive through the
 * constructor from the job scope, and a fresh instance is built per execution.
 */
@RequiredArgsConstructor
public class GreetingTask implements Callable<String> {

    private final Widget widget;

    @Override
    public String call() {
        return "typed:" + widget.label;
    }
}
