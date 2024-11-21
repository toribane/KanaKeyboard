/*
 * Copyright 2023-2024 kachaya
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.toribane.kkbd;

import androidx.annotation.NonNull;

public class Candidate {
    public String reading;
    public String surface;
    public Node node;

    public Candidate(String reading, String surface) {
        this.reading = reading;
        this.surface = surface;
    }

    public Candidate(String reading, Node node) {
        this.reading = reading;
        this.surface = node.getSurface();
        this.node = node;
    }

    @NonNull
    @Override
    public String toString() {
        return reading + ' ' + surface;
    }
}
