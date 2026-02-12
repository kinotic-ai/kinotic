

package org.kinotic.orchestrator.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 *
 * Created by Navid Mitchell on 11/12/20
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Accessors(chain = true)
public class Diagnostic {

    private DiagnosticLevel diagnosticLevel;
    private String message;

}
