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

import java.util.Objects;

public class Word implements Comparable<Word> {

    public String reading;
    public short lid;
    public short rid;
    public short cost;
    public String surface;

    public static final Word bos = new Word("", "0,0,0,BOS");
    public static final Word eos = new Word("", "0,0,0,EOS");

    public Word(String key, short lid, short rid, short cost, String surface) {
        this.reading = key;
        this.lid = lid;
        this.rid = rid;
        this.cost = cost;
        this.surface = surface;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Word word = (Word) o;
        return lid == word.lid && rid == word.rid && Objects.equals(reading, word.reading) && Objects.equals(surface, word.surface);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reading, lid, rid, surface);
    }

    public Word(String entry) {
        String[] ss = entry.split(",", 5);
        this.reading = ss[0];
        this.lid = Short.parseShort(ss[1]);
        this.rid = Short.parseShort(ss[2]);
        this.cost = Short.parseShort(ss[3]);
        this.surface = ss[4];
    }

    public Word(String key, String value) {
        this.reading = key;
        String[] ss = value.split(",", 4);
        this.lid = Short.parseShort(ss[0]);
        this.rid = Short.parseShort(ss[1]);
        this.cost = Short.parseShort(ss[2]);
        this.surface = ss[3];
    }

    public String getKey() {
        return reading;
    }

    public String getValue() {
        return lid + "," + rid + "," + cost + "," + surface;
    }

    @Override
    public int compareTo(Word word) {
        if (cost != word.cost) {
            return (cost - word.cost);
        }
        return surface.compareTo(word.surface);
    }

    @Override
    public String toString() {
        return reading + "," + lid + "," + rid +  "," + cost + "," + surface;
    }
}
