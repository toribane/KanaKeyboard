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

import java.util.ArrayList;
import java.util.Objects;

public class Candidate {
    public String reading;
    public String surface;
    public Word[] words;

    public Candidate(String reading, String surface) {
        this.reading = reading;
        this.surface = surface;
    }

    public Candidate(Word word) {
        this.reading = word.reading;
        this.surface = word.surface;
        this.words = new Word[1];
        this.words[0] = word;
    }

    public Candidate(String reading, String surface, ArrayList<Word> words) {
        this.reading = reading;
        this.surface = surface;
        this.words = words.toArray(new Word[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Candidate candidate = (Candidate) o;
        return Objects.equals(reading, candidate.reading) && Objects.equals(surface, candidate.surface);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reading, surface);
    }

}
