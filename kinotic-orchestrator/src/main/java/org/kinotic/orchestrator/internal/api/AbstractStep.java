

package org.kinotic.orchestrator.internal.api;

import org.kinotic.orchestrator.api.*;
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
            sink.next(new org.kinotic.orchestrator.internal.api.DefaultResult<>(new StepInfo(sequence), ResultType.PROGRESS, progress));
        }
    }

    protected void notifyDiagnostic(DiagnosticLevel diagnosticLevel, Supplier<String> messageSupplier, FluxSink<Result<?>> sink, ResultOptions options, Logger log){
        String message = (diagnosticLevel.ordinal() >= options.getDiagnosticsLevel().ordinal() || log.isTraceEnabled()) ? messageSupplier.get() : "";

        if (log.isTraceEnabled()){
            log.trace(message);
        }

        if(options.getDiagnosticsLevel().ordinal() >= diagnosticLevel.ordinal()){
            sink.next(new org.kinotic.orchestrator.internal.api.DefaultResult<>(new StepInfo(sequence), ResultType.DIAGNOSTIC, new Diagnostic(diagnosticLevel, message)));
        }
    }

    protected void notifyException(Supplier<String> messageSupplier, Throwable throwable, FluxSink<Result<?>> sink, ResultOptions options, Logger log){
        String message = (options.isEnableProgressResults() || log.isDebugEnabled()) ? messageSupplier.get() : "";

        if (log.isDebugEnabled()){
            log.debug(message, throwable);
        }

        if(options.isEnableProgressResults()) {
            sink.next(new org.kinotic.orchestrator.internal.api.DefaultResult<>(new StepInfo(sequence), ResultType.EXCEPTION, message + " Exception: " + throwable.getMessage()));
        }
    }


}

