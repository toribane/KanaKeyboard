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

public class Node implements Comparable<Node> {
    // グラフ作成時にセットするメンバー
    int startPos; // 読みの開始位置
    Word word;      // 単語
    // 前向きDPで作りこむメンバー
    int costFromStart; // スタートからこのノードまでの最小コスト
    Node prev;
    //
    int costToGoal; // このノードからゴールまでのコスト
    Node next;
    // 優先度付きキューへの登録に使用する優先度
    int prio;

    public Node(Node node) {
        this.startPos = node.startPos;
        this.word = node.word;
        this.costFromStart = node.costFromStart;
        this.prev = node.prev;
        this.costToGoal = node.costToGoal;
        this.next = node.next;
        this.prio = node.prio;
    }

    public Node(int startPos, Word word) {
        this.startPos = startPos;
        this.word = word;
    }

    // 優先度キューに入れる際の比較用
    @Override
    public int compareTo(Node node) {
        return this.prio - node.prio;
    }

    @NonNull
    @Override
    public String toString() {
        return "Node{" +
                "costFromStart=" + costFromStart +
                ", startPos=" + startPos +
                ", word=" + word +
                ", prev=" + prev +
                ", costToGoal=" + costToGoal +
                ", next=" + next +
                ", prio=" + prio +
                '}';
    }
}
