package io.github.toribane.kkbd;

import androidx.annotation.NonNull;

public class Node implements Comparable<Node> {

    public int startPos;   // 開始文字位置
    public Word word;      // 品詞ID、コスト、表記
    public int costFromStart; // スタートからこのノードまでの最小コスト
    public int costToGoal; // このノードからゴールまでのコスト
    public Node prev;
    public Node next;
    public int prio;   // 優先度付きキューへの登録に使用する優先度

    public Node(int startPos, Word word) {
        this.startPos = startPos;
        this.word = word;
    }

    public Node(Node node) {
        this.startPos = node.startPos;
        this.word = node.word;
        this.costFromStart = node.costFromStart;
        this.costToGoal = node.costToGoal;
        this.next = node.next;
        this.prio = node.prio;
    }

    @Override
    public int compareTo(Node node) {
        return this.prio - node.prio;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + word.surface + "(" + costFromStart + ")" + word.cost +"]";
    }

}
