

package org.kinotic.orchestrator.api;

/**
 *
 * Created by Navid Mitchell on 11/11/20
 */
public class ResultOptions {
    private DiagnosticLevel diagnosticsLevel;
    private boolean enableProgressResults;

    public ResultOptions() {
    }

    public ResultOptions(DiagnosticLevel diagnosticsLevel, boolean enableProgressResults) {
        this.diagnosticsLevel = diagnosticsLevel;
        this.enableProgressResults = enableProgressResults;
    }

    public DiagnosticLevel getDiagnosticsLevel() {
        return diagnosticsLevel;
    }

    public ResultOptions setDiagnosticsLevel(DiagnosticLevel diagnosticsLevel) {
        this.diagnosticsLevel = diagnosticsLevel;
        return this;
    }

    public boolean isEnableProgressResults() {
        return enableProgressResults;
    }

    public ResultOptions setEnableProgressResults(boolean enableProgressResults) {
        this.enableProgressResults = enableProgressResults;
        return this;
    }

}
