

package org.kinotic.orchestrator.internal.api;

import org.kinotic.orchestrator.api.Task;

import java.util.UUID;

/**
 *
 * Created by Navid Mitchell on 3/25/20
 */
public abstract class AbstractTask<R> implements Task<R> {

    private final String description;

    public AbstractTask() {
        this(null);
    }

    public AbstractTask(String description) {
        if(description != null){
            this.description = description;
        }else{
            this.description = UUID.randomUUID().toString();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }

}
