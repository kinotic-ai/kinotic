

package org.kinotic.system.internal.api.model.grind;

import org.kinotic.system.api.model.grind.Result;
import org.kinotic.system.api.model.grind.ResultType;
import org.kinotic.system.api.model.grind.StepInfo;

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

