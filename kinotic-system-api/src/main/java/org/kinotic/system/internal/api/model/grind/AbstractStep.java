

package org.kinotic.system.internal.api.model.grind;

import org.kinotic.management.api.model.grind.Result;
import org.kinotic.management.api.model.grind.ResultType;
import org.kinotic.management.api.model.grind.StepInfo;
import org.kinotic.management.api.model.grind.StepCompletion;
import org.kinotic.management.api.model.grind.Progress;
import org.kinotic.management.api.model.grind.Diagnostic;
import org.kinotic.management.api.model.grind.DiagnosticLevel;
import org.kinotic.system.api.model.grind.*;
import org.slf4j.Logger;
import reactor.core.publisher.FluxSink;

import java.util.function.Supplier;

/**
 *
 * Created by Navid Mitchell on 11/11/20
 */
public abstract class AbstractStep implements Step {

    protected final int sequence;

    public AbstractStep(int sequence) {
        this.sequence = sequence;
    }

    @Override
    public int getSequence() {
        return sequence;
    }

    protected void notifyProgress(Supplier<Progress> progressSupplier, FluxSink<Result<?>> sink, ResultOptions options, Logger log){
        Progress progress = (options.isEnableProgressResults() || log.isDebugEnabled()) ? progressSupplier.get() : new Progress();

        log.debug("{} ({}%)", progress.getMessage(), progress.getPercentageComplete());

        if(options.isEnableProgressResults()){
            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.PROGRESS, progress));
        }
    }

    protected void notifyDiagnostic(DiagnosticLevel diagnosticLevel, Supplier<String> messageSupplier, FluxSink<Result<?>> sink, ResultOptions options, Logger log){
        String message = (diagnosticLevel.ordinal() >= options.getDiagnosticsLevel().ordinal() || log.isTraceEnabled()) ? messageSupplier.get() : "";

        if (log.isTraceEnabled()){
            log.trace(message);
        }

        if(options.getDiagnosticsLevel().ordinal() >= diagnosticLevel.ordinal()){
            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DIAGNOSTIC, new Diagnostic(diagnosticLevel, message)));
        }
    }

    protected void notifyStepStarted(String description, FluxSink<Result<?>> sink){
        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.STEP_STARTED, description));
    }

    protected void notifyStepCompleted(StepCompletion completion, FluxSink<Result<?>> sink){
        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.STEP_COMPLETED, completion));
    }

    protected void notifyStepFailed(Throwable throwable, FluxSink<Result<?>> sink){
        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.STEP_FAILED, throwable));
    }

    protected void notifyException(Supplier<String> messageSupplier, Throwable throwable, FluxSink<Result<?>> sink, ResultOptions options, Logger log){
        String message = (options.isEnableProgressResults() || log.isDebugEnabled()) ? messageSupplier.get() : "";

        if (log.isDebugEnabled()){
            log.debug(message, throwable);
        }

        if(options.isEnableProgressResults()) {
            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.EXCEPTION, message + " Exception: " + throwable.getMessage()));
        }
    }


}

