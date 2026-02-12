

package org.kinotic.orchestrator.api;

/**
 * The sequence of {@link Step}'s that have been executed to get to a specific {@link Result}
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
     * The sequence of the {@link Step} that created this
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
}
