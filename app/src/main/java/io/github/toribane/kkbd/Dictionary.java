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
        // 参考：「日本語入力を支える技術」4.2 グラフの作成
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
        // 参考：「日本語入力を支える技術」5.10 変換誤りへの対処、ただしスコアではなくコストで構築
        for (int endPos = 1; endPos <= len; endPos++) {
            // endPos文字目で終わるノードのリスト
            List<Node> nodes = graph.get(endPos);
            for (Node node : nodes) {
                int cost = Integer.MAX_VALUE;  // コストの低いものを優先する
                Node bestPrev = null;
                // このノードの開始位置の一つ前が終わりのノード
                List<Node> prevNodes = graph.get(node.startPos - 1);
                for (Node prevNode : prevNodes) {
                    int edgeCost = getEdgeCost(prevNode, node);
                    int tmpCost = prevNode.costFromStart + edgeCost + node.word.cost;
                    if (tmpCost < cost) {
                        cost = tmpCost; // コストが低い
                        bestPrev = prevNode;
                    }
                }
                node.prev = bestPrev;
                node.costFromStart = cost;
            }
        }
        return graph;
    }

    public Candidate[] buildConversionCandidate(CharSequence reading, int mConvertLength) {
        String str = reading.toString();
        int len = str.length();

        int nBest = 30;

        Set<String> surfaces = new LinkedHashSet<>();// 追加順、重複なし
        ArrayList<String> learningWords;

        // 学習辞書から完全一致するものをすべて追加
        learningWords = findLearningWord(str);
        if (learningWords != null) {
            surfaces.addAll(learningWords);
        }
        // 1文字で先頭一致だと多すぎる
        if (str.length() > 1) {
            learningWords = browseLearningWord(str);
            if (learningWords != null) {
                surfaces.addAll(learningWords);
            }
        }

        //
        if (mConvertHalfKana) {
            String s = Converter.toHalfKatakana(str);
            if (!s.equals(str)) {
                surfaces.add(s);
            }
        }
        if (mConvertWideLatin) {
            String s = Converter.toWideLatin(str);
            if (!s.equals(str)) {
                surfaces.add(s);
            }
        }

        // 前半はグラフ作成
        List<List<Node>> graph = buildGraph(str, mConvertLength);

        // 後半は優先度キューを使ってたどるノードを選んでいく
        PriorityQueue<Node> pq = new PriorityQueue<>();
        // まず、優先度キューにゴールノード(EOS)を挿入する
        Node goalNode = graph.get(len + 1).get(0);
        pq.add(goalNode);
        // ここからループ
        while (!pq.isEmpty()) {
            Node node = pq.poll();
            if (node.startPos == 0) {
                // 取り出したノードがスタートノードであった場合、そのノードを結果に追加する
                // 分割や品詞が違っても表記が同じならばカウントしない
                surfaces.add(buildSurface(node));
                if (surfaces.size() >= nBest) {
                    break; //
                }
            } else {
                // スタートノードではなかった場合、そのノードに隣接するスタート側のノードのリストを取り出す
                List<Node> prevNodes = graph.get(node.startPos - 1);
                for (Node prevNode : prevNodes) {
                    int edgeCost = getEdgeCost(prevNode, node);
                    prevNode.costToGoal = node.costToGoal + edgeCost + node.word.cost;
                    prevNode.next = node;
                    // 優先度キューに追加
                    Node queueNode = new Node(prevNode);
                    queueNode.prio = prevNode.costFromStart + prevNode.costToGoal;
                    pq.add(queueNode);
                }
            }
        }

        Set<Candidate> candidates = new LinkedHashSet<>();// 追加順、重複なし

        for (String surface : surfaces) {
            candidates.add(new Candidate(str, surface));
        }
        //
        return candidates.toArray(new Candidate[0]);
    }

    private String buildSurface(Node node) {
        StringBuilder sb = new StringBuilder();
        for (node = node.next; node.next != null; node = node.next) {
            sb.append(node.word.surface);
        }
        return sb.toString();
    }

}
