

package org.kinotic.orchestrator.internal.api.grind;

import org.kinotic.orchestrator.api.grind.Result;
import org.kinotic.orchestrator.api.grind.ResultType;
import org.kinotic.orchestrator.api.grind.StepInfo;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class DefaultResult<T> implements Result<T> {

    private StepInfo stepInfo;
    private ResultType resultType;
    private T value;

    public DefaultResult() {
    }

    public DefaultResult(StepInfo stepInfo,
                         ResultType resultType,
                         T value) {
        this.stepInfo = stepInfo;
        this.resultType = resultType;
        this.value = value;
    }

    @Override
    public StepInfo getStepInfo() {
        return stepInfo;
    }

    @Override
    public ResultType getResultType() {
        return resultType;
    }

    @Override
    public T getValue() {
        return value;
    }
}

