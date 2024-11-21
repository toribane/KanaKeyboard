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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.btree.BTree;
import jdbm.helper.StringComparator;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;

public class Dictionary implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String BTREE_NAME = "btree_dic";
    private static final String SYSTEM_DIC_NAME = "system_dic";
    private static final String LEARNING_DIC_NAME = "learning_dic";
    //
    private final String mFilesDirPath;
    //
    private RecordManager mRecmanSystemDic;
    private BTree mBTreeSystemDic;
    private RecordManager mRecmanLearningDic;
    private BTree mBTreeLearningDic;
    //
    private short mConnectionDim;
    private short[] mConnectionTable;
    //
    private boolean mConvertHalfKana;
    private boolean mConvertWideLatin;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (key == null) {
            return;
        }
        if (key.equals("convert_half_kana")) {
            mConvertHalfKana = sharedPreferences.getBoolean(key, false);
        }
        if (key.equals("convert_wide_latin")) {
            mConvertWideLatin = sharedPreferences.getBoolean(key, false);
        }
    }

    public Dictionary(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        mConvertHalfKana = sharedPreferences.getBoolean("convert_half_kana", false);
        mConvertWideLatin = sharedPreferences.getBoolean("convert_wide_latin", false);

        mFilesDirPath = context.getFilesDir().getAbsolutePath() + "/";

        try {
            copyFromResRaw(context);
            readConnection(context);
            loadDictionary();
        } catch (IOException e) {
            mRecmanSystemDic = null;
            mBTreeSystemDic = null;
            mRecmanLearningDic = null;
            mBTreeLearningDic = null;
        }
    }

    private void loadDictionary() throws IOException {
        long recid;
        // システム辞書
        mRecmanSystemDic = RecordManagerFactory.createRecordManager(mFilesDirPath + SYSTEM_DIC_NAME);
        mBTreeSystemDic = BTree.load(mRecmanSystemDic, mRecmanSystemDic.getNamedObject(BTREE_NAME));
        // 学習辞書
        mRecmanLearningDic = RecordManagerFactory.createRecordManager(mFilesDirPath + LEARNING_DIC_NAME);
        recid = mRecmanLearningDic.getNamedObject(BTREE_NAME);
        if (recid == 0) {
            mBTreeLearningDic = BTree.createInstance(mRecmanLearningDic, new StringComparator());
            mRecmanLearningDic.setNamedObject(BTREE_NAME, mBTreeLearningDic.getRecid());
            mRecmanLearningDic.commit();
        } else {
            mBTreeLearningDic = BTree.load(mRecmanLearningDic, recid);
        }
    }

    public String getLearningDictionaryName() {
        return LEARNING_DIC_NAME;
    }

    private void copyFromResRaw(Context context) throws IOException {
        String dbFileName = mFilesDirPath + SYSTEM_DIC_NAME + ".db";
        File dbFile = new File(dbFileName);

        BufferedInputStream bis = new BufferedInputStream(
                context.getResources().openRawResource(R.raw.system_dic));
        // ファイルサイズが同じならば同じ辞書ファイルとみなす
        if (bis.available() == dbFile.length()) {
            bis.close();
            return;
        }

        BufferedOutputStream bos = new BufferedOutputStream(
                Files.newOutputStream(Paths.get(dbFileName)));
        int size;
        byte[] buf = new byte[16 * 1024];
        while ((size = bis.read(buf, 0, buf.length)) > 0) {
            bos.write(buf, 0, size);
        }
        bos.flush();
        bos.close();

        bis.close();
    }

    private void readConnection(Context context) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedInputStream bis = new BufferedInputStream(
                context.getResources().openRawResource(R.raw.connection));

        byte[] buf = new byte[16 * 1024];
        int len;
        while ((len = bis.read(buf, 0, buf.length)) > 0) {
            baos.write(buf, 0, len);
        }
        bis.close();

        ShortBuffer sb = ByteBuffer.wrap(baos.toByteArray()).asShortBuffer();
        mConnectionDim = sb.get();
        mConnectionTable = new short[mConnectionDim * mConnectionDim];
        sb.get(mConnectionTable);
    }

    // ２つのノード間のエッジのコストを返す
    private int getEdgeCost(Node leftNode, Node rightNode) {
        return mConnectionTable[leftNode.word.rid * mConnectionDim + rightNode.word.lid];
    }

    // 辞書を指定して語句を探す
    private ArrayList<Word> findDictionaryWords(String reading, BTree btree) {
        ArrayList<Word> words = new ArrayList<>();
        try {
            String value = (String) btree.find(reading);
            if (value != null) {
                for (String s : value.split("\t")) {
                    words.add(new Word(s));
                }
            }
        } catch (IOException ignored) {
        }
        return words;
    }

    // 学習辞書から語句を探す
    private ArrayList<Word> findLearningWords(String reading) {
        return findDictionaryWords(reading, mBTreeLearningDic);
    }

    // すべての辞書から語句を探す
    private ArrayList<Word> findWords(String reading) {
        ArrayList<Word> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        // 学習辞書
        try {
            String value = (String) mBTreeLearningDic.find(reading);
            if (value != null) {
                sb.append(value);
            }
        } catch (IOException ignored) {
        }
        // システム辞書
        try {
            String value = (String) mBTreeSystemDic.find(reading);
            if (value != null) {
                sb.append("\t").append(value);
            }
        } catch (IOException ignored) {
        }
        // value="cost", key="lid,rid,surface"
        for (String s : sb.toString().split("\t")) {
            String[] ss = s.split(",", 2);
            if (ss.length == 2) {
                map.putIfAbsent(ss[1], ss[0]); // 学習辞書でcost調整済みならば上書きしない
            }
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            list.add(new Word(entry.getValue() + "," + entry.getKey()));
        }
        return list;
    }

    private void addLearningWord(String reading, Word word) {
        ArrayList<Word> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        // 先頭に今回の語句を入れておく
        StringBuilder sb = new StringBuilder(word.toString());
        // 学習辞書に
        try {
            String value = (String) mBTreeLearningDic.find(reading);
            if (value != null) {
                sb.append("\t").append(value);
            }
        } catch (IOException ignored) {
        }
        // value="cost", key="lid,rid,surface"
        for (String s : sb.toString().split("\t")) {
            String[] ss = s.split(",", 2);
            if (ss.length == 2) {
                map.putIfAbsent(ss[1], ss[0]);
            }
        }
        sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() != 0) {
                sb.append("\t");
            }
            sb.append(entry.getValue() + "," + entry.getKey());
        }
        try {
            mBTreeLearningDic.insert(reading, sb.toString(), true);
            mRecmanLearningDic.commit();
        } catch (IOException ignored) {
        }
    }

    // Wordを学習辞書に登録する
    private void addLearningNode(Node node) {
        String reading = node.reading;
        // 同じ読みとIDの単語リストの中で最もコストの低いものを探す
        short bestCost = node.word.cost;
        Word bestWord = node.word;
        // 再学習のため学習辞書も含める
        for (Word word : findWords(reading)) {
            if (word.lid == node.word.lid && word.rid == node.word.rid) {
                if (bestCost > word.cost) {
                    bestCost = word.cost;
                    bestWord = word;
                }
            }
        }
        if (node.word.cost == bestWord.cost && node.word.surface.equals(bestWord.surface)) {
            // 最もコストの低いものが選択されても学習辞書に登録する、ただしコスト0は除く(助詞等)
            if (bestCost != 0) {
                addLearningWord(reading, bestWord);
            }
        } else {
            // 今回選択された語句のコストと最もコストの低い語句のコストを入れ替えたものを学習辞書に登録する
            addLearningWord(reading, new Word(node.word.cost, bestWord.lid, bestWord.rid, bestWord.surface));
            addLearningWord(reading, new Word(bestWord.cost, node.word.lid, node.word.rid, node.word.surface));
        }
    }

    // Candidate内のNodeから学習する
    public void addLearning(Candidate candidate) {
        if (candidate.node == null) {
            return;
        }
        for (Node node = candidate.node.next; node.next != null; node = node.next) {
            addLearningNode(node);
        }
    }

    public void importLearningDictionary(ArrayList<String> entries) {
        for (String entry : entries) {
            String[] ss = entry.split("\t");
            if (ss.length < 2) {
                continue;
            }
            String key = ss[0];
            for (int i = 1; i < ss.length; i++) {
                addLearningWord(key, new Word(ss[i]));
            }
        }
    }

    public ArrayList<String> exportLearningDictionary() {
        ArrayList<String> list = new ArrayList<>();
        Tuple tuple = new Tuple();
        try {
            mRecmanLearningDic.commit();
            TupleBrowser browser = mBTreeLearningDic.browse();
            while (browser.getNext(tuple)) {
                list.add(tuple.getKey() + "\t" + tuple.getValue());
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    public void deleteLearning(String key) {
        try {
            mBTreeLearningDic.remove(key);
            mRecmanLearningDic.commit();
        } catch (IOException ignored) {
        }
    }

    public Candidate[] buildConversionCandidate(CharSequence cs, int splitPos) {
        String reading = cs.toString();
        int len = reading.length();
        int nBest = 20;
        Set<String> surfaceCheckSet = new HashSet<>();        // 表記重複チェック用

        ArrayList<Candidate> candidates = new ArrayList<>();

        // 前半はグラフを作って前向きDP
        List<List<Node>> graph = buildGraph(reading, splitPos);
        // 後半は優先度キューを使ってたどるノードを選んでいく
        List<Node> bestNodes = new ArrayList<>();
        PriorityQueue<Node> pq = new PriorityQueue<>();
        // まず、優先度キューにゴールノード(EOS)を挿入する
        Node goalNode = graph.get(len + 1).get(0);
        pq.add(goalNode);
        // ここからループ
        while (!pq.isEmpty()) {
            Node node = pq.poll();
            if (node.startPos == 0) {
                // 取り出したノードがスタートノードであった場合、そのノードを結果に追加する
                String surface = node.getSurface();
                if (!surfaceCheckSet.contains(surface)) {
                    // 表記が同じならばコストの低いものだけ、後から出てくるのはコストが高い
                    surfaceCheckSet.add(surface);
                    bestNodes.add(node);
                    if (bestNodes.size() >= nBest) {
                        break; //
                    }
                }
            } else {
                // スタートノードではなかった場合、そのノードに隣接するスタート側のノードのリストを取り出す
                List<Node> prevNodes = graph.get(node.startPos - 1);
                for (Node prevNode : prevNodes) {
                    // 優先度キューに追加するためコピーを作る
                    Node queueNode = new Node(prevNode);
                    int edgeCost = getEdgeCost(queueNode, node);
                    queueNode.costToGoal = node.costToGoal + edgeCost + node.word.cost;
                    queueNode.next = node;
                    queueNode.prio = queueNode.costFromStart + queueNode.costToGoal;
                    // 優先度キューに追加
                    pq.add(queueNode);
                }
            }
        }

        // n-bestの候補
        for (Node node : bestNodes) {
            candidates.add(new Candidate(reading, node));
        }

        // 全角英数
        if (mConvertWideLatin) {
            String s = Converter.toWideLatin(reading);
            if (!s.equals(reading)) {
                candidates.add(new Candidate(reading, s));
            }
        }
        // 半角カナ
        if (mConvertHalfKana) {
            String s = Converter.toHalfKatakana(reading);
            if (!s.equals(reading)) {
                candidates.add(new Candidate(reading, s));
            }
        }

        return candidates.toArray(new Candidate[0]);
    }

    private List<List<Node>> buildGraph(String str, int splitPos) {
        int len = str.length();
        List<List<Node>> graph = new ArrayList<>();
        for (int i = 0; i <= (len + 1); i++) {
            graph.add(i, new ArrayList<>());
        }
        Node bos = new Node(0, "", new Word("0,0,0,BOS"));
        Node eos = new Node(len + 1, "", new Word("0,0,0,EOS"));

        graph.get(0).add(bos); // BOS
        graph.get(len + 1).add(eos); // EOS

        // endPos文字目で終わる単語リストを作成
        for (int startPos = 1; startPos <= len; startPos++) {
            for (int endPos = startPos; endPos <= len; endPos++) {
                // 左右カーソルで区切を指定されていればそこをまたぐグラフは作らない
                if (splitPos != len) {
                    if (startPos <= splitPos && endPos > splitPos) {
                        continue;
                    }
                }
                String reading = str.substring(startPos - 1, endPos);
                // 単語リストを探す
                ArrayList<Word> words = findWords(reading);
                if (words.isEmpty()) {
                    // 単語が見つからない場合は1文字を1単語となるダミーノードを登録する
                    continue;
                }
                for (Word word : words) {
                    Node node = new Node(startPos, reading, word);
                    graph.get(endPos).add(node);
                }
            }
        }

        // 前半はviterbiアルゴリズムで前向きDP
        for (int endPos = 1; endPos <= len + 1; endPos++) {
            // endPos文字目で終わるノードのリスト
            List<Node> nodes = graph.get(endPos);
            for (Node node : nodes) {
                node.costFromStart = Integer.MAX_VALUE;
                node.prev = null;
                // このノードの開始位置の一つ前が終わりのノード
                List<Node> prevNodes = graph.get(node.startPos - 1);
                for (Node prevNode : prevNodes) {
                    int edgeCost = getEdgeCost(prevNode, node);
                    int cost = prevNode.costFromStart + edgeCost + node.word.cost;
                    if (cost < node.costFromStart) {
                        node.costFromStart = cost;
                        node.prev = prevNode;
                    }
                }
            }
        }
        return graph;
    }
}
