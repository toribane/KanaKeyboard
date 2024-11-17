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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;

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

    // ２つのノード間のエッジのコストを返す
    public int getEdgeCost(Node left, Node right) {
        return mConnectionTable[left.word.id * mConnectionDim + right.word.id];
    }

    // システム辞書から語句を取得する
    public ArrayList<Word> findWord(String key) {
        if (mBTreeSystemDic == null) {
            return null;
        }
        String value;
        try {
            value = (String) mBTreeSystemDic.find(key);
        } catch (IOException e) {
            return null;
        }
        if (value == null) {
            return null;
        }
        ArrayList<Word> list = new ArrayList<>();
        for (String s : value.split("\\t")) {
            list.add(new Word(s));
        }
        return list;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4 * 1024];
        int len;
        while ((len = bis.read(buf, 0, buf.length)) > 0) {
            baos.write(buf, 0, len);
        }
        byte[] data = baos.toByteArray();
        baos.close();
        bis.close();

        BufferedOutputStream bos = new BufferedOutputStream(
                Files.newOutputStream(Paths.get(dbFileName)));
        bos.write(data);
        bos.flush();
        bos.close();
    }

    private void readConnection(Context context) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedInputStream bis = new BufferedInputStream(
                context.getResources().openRawResource(R.raw.connection));

        byte[] buf = new byte[4 * 1024];
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

    public void addLearning(String keyword, String word) {
        if (mRecmanLearningDic == null || mBTreeLearningDic == null) {
            return;
        }
        if (keyword.isEmpty() || word.isEmpty()) {
            return;
        }
        try {
            String value = (String) mBTreeLearningDic.find(keyword);
            if (value == null) {
                mBTreeLearningDic.insert(keyword, word, true);
            } else {
                StringBuilder sb = new StringBuilder(word);
                String[] ss = value.split("\t");
                for (String s : ss) {
                    if (s.equals(word)) {
                        continue;
                    }
                    sb.append("\t").append(s);
                }
                mBTreeLearningDic.insert(keyword, sb.toString(), true);
            }
            mRecmanLearningDic.commit();
        } catch (IOException ignored) {
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
                addLearning(key, ss[i]);
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

    public ArrayList<String> findLearningWord(String key) {
        ArrayList<String> list = new ArrayList<>();
        try {
            String value = (String) mBTreeLearningDic.find(key);
            if (value != null) {
                String[] words = value.split("\t");
                list.addAll(Arrays.asList(words));
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    public ArrayList<String> browseLearningWord(String key) {
        Tuple tuple = new Tuple();
        TupleBrowser browser;
        ArrayList<String> list = new ArrayList<>();
        try {
            browser = mBTreeLearningDic.browse(key);
            while (browser.getNext(tuple)) {
                String tupleKey = (String) tuple.getKey();
                if (!tupleKey.startsWith(key)) {
                    break;
                }
                String value = (String) tuple.getValue();
                if (value != null) {
                    String[] words = value.split("\t");
                    list.addAll(Arrays.asList(words));
                }
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

    private final Word BOS = new Word("0,0,BOS");
    private final Word EOS = new Word("0,0,EOS");

    public List<List<Node>> buildGraph(String str, int splitPos) {
        int len = str.length();
        List<List<Node>> graph = new ArrayList<>();
        for (int i = 0; i <= (len + 1); i++) {
            graph.add(i, new ArrayList<>());
        }
        graph.get(0).add(new Node(0, BOS));             // グラフの一番初めにBOSという特殊なノードを入れる
        graph.get(len + 1).add(new Node(len + 1, EOS)); // グラフの一番最後にEOSという特殊なノードを入れる

        // endPos文字目で終わる単語リストを作成
        for (int startPos = 1; startPos <= len; startPos++) {
            for (int endPos = startPos; endPos <= len; endPos++) {
                // 左右カーソルで区切を指定されていればそこをまたぐグラフは作らない
                if (splitPos != len) {
                    if (startPos <= splitPos && endPos > splitPos) {
                        continue;
                    }
                }
                String substr = str.substring(startPos - 1, endPos);
                ArrayList<Word> words = findWord(substr);
                if (words == null) {
                    continue;                    // TODO: 辞書にない場合の取り扱い
                }
                for (Word word : words) {
                    graph.get(endPos).add(new Node(startPos, word));
                }
            }
        }
        // 前向きDP
        // 参考：「形態要素解析の理論と実装」図5.6 ビタビアルゴリズムによるビタビ系列の探索
        for (int endPos = 1; endPos <= len; endPos++) {
            // endPos文字目で終わるノードのリスト
            List<Node> nodes = graph.get(endPos);
            for (Node node : nodes) {
                node.costFromStart = Integer.MAX_VALUE;  // このノードまでの最小コスト
                node.prev = null;   // このノードまでのコストが最小になる前方のノード
                // このノードの開始位置の一つ前が終わりのすべてのノードに対して
                List<Node> prevNodes = graph.get(node.startPos - 1);
                for (Node prevNode : prevNodes) {
                    // 前方のノードと注目しているノード間の連接コスト
                    int edgeCost = getEdgeCost(prevNode, node);
                    // 先頭から前方のノードまでのコストと連接コストとこのノードの語句のコストの和
                    int cost = prevNode.costFromStart + edgeCost + node.word.cost;
                    if (cost < node.costFromStart) {
                        // コストの低いものでこのノードを更新
                        node.costFromStart = cost;
                        node.prev = prevNode;
                    }
                }
            }
        }
        return graph;
    }

    public Candidate[] buildConversionCandidate(CharSequence cs, int mConvertLength) {
        String reading = cs.toString();
        int len = reading.length();

        int nBest = 30;

        // 重複チェック用、読みと表記をTABでつなげた文字列
        LinkedHashSet<String> pairs = new LinkedHashSet<>();
        ArrayList<String> surfaces;

        // 学習辞書から完全一致するものをすべて追加
        surfaces = findLearningWord(reading);
        if (surfaces != null) {
            for (String surface : surfaces) {
                pairs.add(reading + "\t" + surface);
            }
        }
        // 先頭一致(2文字以上)
//        if (reading.length() > 1) {
//            surfaces = browseLearningWord(reading);
//            if (surfaces != null) {
//                for (String surface : surfaces) {
//                    pairs.add(reading + "\t" + surface);
//                }
//            }
//        }

        // 前半は前向きDPでグラフ作成
        List<List<Node>> graph = buildGraph(reading, mConvertLength);

        // 後半は優先度キューを使ってたどるノードを選んでいく
        PriorityQueue<Node> pq = new PriorityQueue<>();
        // まず、優先度キューにゴールノード(EOS)を挿入する
        Node goalNode = graph.get(len + 1).get(0);
        pq.add(goalNode);
        // ここからループ
        while (!pq.isEmpty()) {
            // 優先度付きキューから最もコストの低いノードを取り出す。
            Node node = pq.poll();
            if (node.startPos == 0) {
                // 取り出したノードがスタートノードに到達したらそのノードを結果に追加する
                // 分割や品詞が違っても表記が同じならばカウントしない
                String surface = buildSurface(node);
                pairs.add(reading + "\t" + surface);
                if (pairs.size() >= nBest) {
                    break; //
                }
            } else {
                // スタートノードではなかった場合、そのノードに隣接するスタート側のノードのリストを取り出す
                List<Node> prevNodes = graph.get(node.startPos - 1);
                // 取り出した各隣接ノードに対して
                for (Node prevNode : prevNodes) {
                    // 隣接ノードのゴールまでのコストを注目しているノードのコストとノード間のコストと隣接ノードのコストの和にする
                    int edgeCost = getEdgeCost(prevNode, node);
                    prevNode.costToGoal = node.costToGoal + edgeCost + node.word.cost;
                    prevNode.next = node;
                    Node queueNode = new Node(prevNode);
                    queueNode.prio = queueNode.costToGoal + queueNode.costFromStart;
                    // 優先度キューに追加
                    pq.add(queueNode);
                }
            }
        }

        if (mConvertHalfKana) {
            String s = Converter.toHalfKatakana(reading);
            if (!s.equals(reading)) {
                pairs.add(reading + "\t" + s);
            }
        }
        if (mConvertWideLatin) {
            String s = Converter.toWideLatin(reading);
            if (!s.equals(reading)) {
                pairs.add(reading + "\t" + s);
            }
        }

        // Candidate[]にして返す
        ArrayList<Candidate> candidates = new ArrayList<>();
        for (String pair : pairs) {
            String[] ss = pair.split("\\t");
            candidates.add(new Candidate(ss[0], ss[1]));
        }
        return candidates.toArray(new Candidate[0]);
    }

    private String buildSurface(Node node) {
        StringBuilder sb = new StringBuilder();
        // BOSとEOSの間のみ
        for (node = node.next; node.next != null; node = node.next) {
            sb.append(node.word.surface);
        }
        return sb.toString();
    }

    public class Word {
        public short id;
        public short cost;
        public String surface;

        public Word(String s) {
            String[] ss = s.split(",", 3);
            this.id = Short.parseShort(ss[0]);
            this.cost = Short.parseShort(ss[1]);
            this.surface = ss[2];
        }
    }

    public class Node implements Comparable<Node> {
        public int startPos;   // 開始文字位置
        public Word word;
        // 前向きDP
        public int costFromStart; // スタートからこのノードまでの最小コスト
        public Node prev;
        // 後ろ向き
        public int costToGoal; // このノードからゴールまでのコスト
        public Node next;
        // 優先度付きキューへの登録に使用する優先度
        public int prio;

        public Node(Node node) {
            this.startPos = node.startPos;
            this.word = node.word;
            // 前向き
            this.costFromStart = node.costFromStart;
            this.next = node.next;
            // 後ろ向き
            this.costToGoal = node.costToGoal;
            this.prev = node.prev;
            // 優先度付きキュー
            this.prio = node.prio;
        }

        public Node(int startPos, Word word) {
            this.startPos = startPos;
            this.word = word;
        }

        @Override
        public int compareTo(Node node) {
            return this.prio - node.prio;
        }
    }
}
