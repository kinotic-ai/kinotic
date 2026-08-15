

package org.kinotic.orchestrator.api.model.grind;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

/**
 * The sequence of steps that have been executed to get to a specific {@link Result}
 *
 * Created by Navid Mitchell on 11/18/20
 */
public class StepInfo {

    private final int sequence;

    private StepInfo ancestor = null;

    private StepInfo top = null;

    public StepInfo(int sequence) {
        this.sequence = sequence;
    }

    /**
     * The sequence of the step that created this
     * @return the sequence number
     */
    public int getSequence() {
        return sequence;
    }

    public void addAncestor(StepInfo ancestor){
        if(this.ancestor == null){
            this.ancestor = ancestor;
        }else{
            this.top.addAncestor(ancestor);
        }
        this.top = ancestor;
    }

    public StepInfo getAncestor() {
        return ancestor;
    }

    /**
     * The position of this step within the run's step tree, as the {@code /} separated
     * sequence numbers from the root {@link JobDefinition} down to this step
     * @return the step path, such as {@code 0/2/1}
     */
    public String path() {
        Deque<Integer> sequences = new ArrayDeque<>();
        for(StepInfo info = this; info != null; info = info.getAncestor()){
            sequences.addFirst(info.getSequence());
        }
        return sequences.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("/"));
    }
}
