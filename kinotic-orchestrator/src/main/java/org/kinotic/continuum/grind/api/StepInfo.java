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

package org.kinotic.continuum.grind.api;

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
