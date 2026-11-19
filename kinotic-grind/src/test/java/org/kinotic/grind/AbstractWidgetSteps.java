package org.kinotic.grind;

import org.kinotic.grind.api.model.Task;
import org.kinotic.grind.api.model.Tasks;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Builds an inline task from an abstract class, so the task is declared where its enclosing
 * instance is of a type that has no constructor to call.
 */
public abstract class AbstractWidgetSteps {

    /**
     * @param seen receives the name of the widget each execution is injected with
     * @return the task
     */
    public Task<Void> recordWidget(List<String> seen) {
        return Tasks.fromRunnable("declared in an abstract class", new Runnable() {

            @Autowired
            private Widget widget;

            @Override
            public void run() {
                seen.add("abstract class " + widget.name());
            }
        });
    }

}
