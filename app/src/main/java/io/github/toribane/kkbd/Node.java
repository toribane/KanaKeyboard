package io.github.toribane.kkbd;

public class Node implements Comparable<Node> {

    // グラフ作成時にセットするメンバー
    int startPos; // 読みの開始位置
    String reading; // 単語の読み
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
        this.reading = node.reading;
        this.word = node.word;

        this.costFromStart = node.costFromStart;
        this.prev = node.prev;

        this.costToGoal = node.costToGoal;
        this.next = node.next;

        this.prio = node.prio;
    }

    public Node(int startPos, String reading, Word word) {
        this.startPos = startPos;
        this.reading = reading;
        this.word = word;
    }

    // 優先度キューに入れる際の比較用
    @Override
    public int compareTo(Node node) {
        return this.prio - node.prio;
    }

    public String getSurface() {
        StringBuilder sb = new StringBuilder();
        // BOSとEOSの間のみ
        for (Node node = this.next; node.next != null; node = node.next) {
            sb.append(node.word.surface);
        }
        return sb.toString();
    }
}
