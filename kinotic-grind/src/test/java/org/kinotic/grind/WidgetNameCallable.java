package org.kinotic.grind;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Callable;

/**
 * A task written as a named class that takes the scope's {@link Widget} through an injected field.
 */
public class WidgetNameCallable implements Callable<String> {

    @Autowired
    private Widget widget;

    @Override
    public String call() {
        return widget.name();
    }

}
