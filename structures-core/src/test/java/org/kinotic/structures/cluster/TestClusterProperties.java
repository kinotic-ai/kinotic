package org.kinotic.structures.cluster;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TestClusterProperties {
    @NotNull
    private Boolean useExternal = false;
    @Min(1)
    @Max(4)  // Test instance (1) + up to 3 container nodes
    private int nodeCount = 1;
}
