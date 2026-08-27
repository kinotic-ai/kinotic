

package org.kinotic.grind.api.model;

import lombok.Getter;

/**
 * The sequence of steps that have been executed to get to a specific {@link Result}
 *
 * Created by Navid Mitchell on 11/18/20
 */
public class StepInfo {

    /**
     *  The sequence of the step that created this
     */
    @Getter
    private final int sequence;

    @Getter
    private StepInfo ancestor = null;

    private StepInfo top = null;

    public StepInfo(int sequence) {
        this.sequence = sequence;
    }

    public void addAncestor(StepInfo ancestor){
        if(this.ancestor == null){
            this.ancestor = ancestor;
        }else{
            this.top.addAncestor(ancestor);
        }
        this.top = ancestor;
    }

    /**
     * The {@code /} separated sequence path from the run's root down to the step that created
     * this info, matching the {@code stepPath} recorded on a {@code TaskRecord}.
     * @return the step path, complete once the {@link Result} carrying this info has been
     *         delivered to a subscriber of the run
     */
    public String path() {
        StringBuilder ret = new StringBuilder();
        for(StepInfo info = this; info != null; info = info.getAncestor()){
            if(!ret.isEmpty()){
                ret.insert(0, '/');
            }
            ret.insert(0, info.getSequence());
        }
        return ret.toString();
    }
}
