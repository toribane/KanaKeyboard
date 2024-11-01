/*
 * Copyright 2023-2024  kachaya
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.toribane.kkbd;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class Word implements Serializable, Comparable<Word> {

    private static final long serialVersionUID = 1L;

    public short id;
    public short cost;
    public String surface;

    @NonNull
    @Override
    public String toString() {
        return id + "," + cost + "," + surface;
    }

    public short getId() {
        return id;
    }

    public int getCost() {
        return cost;
    }

    public String getSurface() {
        return surface;
    }

    public Word(String s) {
        String[] ss = s.split(",", 3);
        this.id = Short.parseShort(ss[0]);
        this.cost = Short.parseShort(ss[1]);
        this.surface = ss[2];
    }

    public Word(String surface, int id, int cost) {
        this.surface = surface;
        this.id = (short) id;
        this.cost = (short) cost;
    }


    @Override
    public int compareTo(Word word) {
        if (cost != word.cost) {
            return cost - word.cost;
        }
        return surface.compareTo(word.surface);
    }

}
