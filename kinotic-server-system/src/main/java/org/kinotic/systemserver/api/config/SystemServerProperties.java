package org.kinotic.systemserver.api.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** The URLs the system server is reached at, {@code kinotic.systemServer.*}, set per deployment. */
@Getter
@Setter
public class SystemServerProperties {

    /** Base URL of the system console, where the system server's browser flows send the user. */
    @NotBlank
    private String consoleBaseUrl;
}
