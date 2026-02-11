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
 * Represents the progress of a {@link JobDefinition} or {@link Task}
 *
 *
 * Created by Navid Mitchell on 11/11/20
 */
public class Progress {

    private int percentageComplete;
    private String message;

    public Progress() {
    }

    public Progress(int percentageComplete, String message) {
        this.percentageComplete = percentageComplete;
        this.message = message;
    }

    public int getPercentageComplete() {
        return percentageComplete;
    }

    public Progress setPercentageComplete(int progress) {
        this.percentageComplete = progress;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Progress setMessage(String message) {
        this.message = message;
        return this;
    }

}
