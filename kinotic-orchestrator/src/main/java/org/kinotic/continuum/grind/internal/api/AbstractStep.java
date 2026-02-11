/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.grind.internal.api;

import org.kinotic.continuum.grind.api.*;
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

