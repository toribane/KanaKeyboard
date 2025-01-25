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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final String PREDICTION_DIC_NAME = "prediction_dic";
    //
    private final String mFilesDirPath;
    //
    private RecordManager mRecmanSystemDic;
    private BTree mBTreeSystemDic;
    private RecordManager mRecmanLearningDic;
    private BTree mBTreeLearningDic;
    private RecordManager mRecmanPredictionDic;
    private BTree mBTreePredictionDic;
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
            mRecmanPredictionDic = null;
            mBTreePredictionDic = null;
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
        // 予測辞書
        mRecmanPredictionDic = RecordManagerFactory.createRecordManager(mFilesDirPath + PREDICTION_DIC_NAME);
        recid = mRecmanPredictionDic.getNamedObject(BTREE_NAME);
        if (recid == 0) {
            mBTreePredictionDic = BTree.createInstance(mRecmanPredictionDic, new StringComparator());
            mRecmanPredictionDic.setNamedObject(BTREE_NAME, mBTreePredictionDic.getRecid());
            mRecmanPredictionDic.commit();
        } else {
            mBTreePredictionDic = BTree.load(mRecmanPredictionDic, recid);
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

    public void importLearningDictionary(ArrayList<String> entries) {
        for (String entry : entries) {
            String[] ss = entry.split("\t");
            if (ss.length < 2) {
                continue;
            }
            String key = ss[0];
            for (int i = 1; i < ss.length; i++) {
                addLearningWord(new Word(key, ss[i]));
            }
        }
    }

    public ArrayList<String> exportLearningDictionary() {
        ArrayList<String> list = new ArrayList<>();
        try {
            mRecmanLearningDic.commit();
            Tuple tuple = new Tuple();
            TupleBrowser browser = mBTreeLearningDic.browse();
            while (browser.getNext(tuple)) {
                StringBuilder sb = new StringBuilder((String) tuple.getKey());
                byte[] byteArray = (byte[]) tuple.getValue();
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(byteArray));
                while (dis.available() > 0) {
                    short lid = dis.readShort();
                    short rid = dis.readShort();
                    short cost = dis.readShort();
                    String surface = dis.readUTF();
                    sb.append("\t" + lid + "," + rid + "," + cost + "," + surface);
                }
                list.add(sb.toString());
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

    //
    private Set<Word> findWords(String key, BTree btree) {
        Set<Word> set = new HashSet<>();
        try {
            byte[] byteArray = (byte[]) btree.find(key);
            if (byteArray != null) {
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(byteArray));
                while (dis.available() > 0) {
                    short lid = dis.readShort();
                    short rid = dis.readShort();
                    short cost = dis.readShort();
                    String surface = dis.readUTF();
                    set.add(new Word(key, lid, rid, cost, surface));
                }
            }
        } catch (IOException ignored) {
        }
        return set;
    }
    // 学習辞書とシステム辞書から語句を探す
    private Set<Word> findWords(String key) {
        Set<Word> set = findWords(key, mBTreeLearningDic);
        set.addAll(findWords(key, mBTreeSystemDic));
        return set;
    }

    // 学習辞書に語句を追加する
    private void addLearningWord(Word word) {
        System.out.println(word);
        try {
            Set<Word> set = new HashSet<>();
            // 今回の語句を最初に追加しておく
            set.add(word);
            set.addAll(findWords(word.reading, mBTreeLearningDic));
            // 辞書を更新
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            for (Word w : set) {
                dos.writeShort(w.lid);
                dos.writeShort(w.rid);
                dos.writeShort(w.cost);
                dos.writeUTF(w.surface);
            }
            byte[] byteArray = baos.toByteArray();
            mBTreeLearningDic.insert(word.reading, byteArray, true);
            mRecmanLearningDic.commit();
        } catch (IOException ignored) {
        }
    }

    // 選択された語句のコストを低くして次回の候補作成で先に現れるようにする
    private void updateWordCost(Word selectWord) {
        Word bestWord = selectWord;
        for (Word word : findWords(selectWord.reading)) {
            if (word.lid == selectWord.lid && word.rid == selectWord.rid) {
                if (word.cost < bestWord.cost) {
                    bestWord = word;
                }
            }
        }
        if (selectWord.cost != bestWord.cost) {
            // コストを入れ替えて学習辞書に登録
            short cost = selectWord.cost;
            selectWord.cost = bestWord.cost;
            bestWord.cost = cost;
            addLearningWord(bestWord);
        }
        addLearningWord(selectWord);
    }

    // 予測辞書
    private void addPredictionWord(Word currWord, Word nextWord) {
        // 読みに','を含むものは現行の辞書では対応できないが一文字の','だけなので問題ない
        if (currWord.reading.contains(",") || nextWord.reading.contains(",")) {
            return;
        }
        try {
            Set<Word> set = new LinkedHashSet<>();  // 追加順を保持
            set.add(nextWord);
            // keyにはcostを含めない
            String key = currWord.reading + "," + currWord.lid + "," + currWord.rid + "," + currWord.surface;
            String values = (String) mBTreePredictionDic.find(key);
            if (values != null) {
                for (String value : values.split("\t")) {
                    // reading,cost,lid,rid,surface
                    String[] ss = value.split(",", 2);
                    set.add(new Word(ss[0], ss[1]));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (Word w : set) {
                if (sb.length() != 0) {
                    sb.append("\t");
                }
                sb.append(w.toString());
            }
            mBTreePredictionDic.insert(key, sb.toString(), true);
            mRecmanPredictionDic.commit();
        } catch (IOException ignored) {
        }
    }

    // Candidateから学習する
    public void addLearning(Candidate candidate) {
        if (candidate.words == null) {
            return;
        }
        for (Word word : candidate.words) {
            // 語句のコストを調整
            updateWordCost(word);
        }
        // 予測辞書に登録
        for (int i = 0; i < candidate.words.length - 1; i++) {
            addPredictionWord(candidate.words[i], candidate.words[i + 1]);
        }
    }

    public Candidate[] buildPredictionCandidate(Candidate candidate) {
        Set<Candidate> set = new LinkedHashSet<>(); // 追加順保持
        if (candidate.words == null) {
            return set.toArray(new Candidate[0]);
        }
        Word lastWord = candidate.words[candidate.words.length - 1];
        String key = lastWord.reading + "," + lastWord.lid + "," + lastWord.rid + "," + lastWord.surface;
        try {
            String values = (String) mBTreePredictionDic.find(key);
            if (values != null){
                for (String value : values.split("\t")) {
                    set.add(new Candidate(new Word(value)));
                }
            }
        } catch (IOException ignored) {
        }
        return set.toArray(new Candidate[0]);
    }

    public Candidate[] buildConversionCandidate(CharSequence cs, int splitPos) {
        String reading = cs.toString();
        int len = reading.length();
        int nBest = 20;
        Set<Candidate> set = new LinkedHashSet<>(); // 追加順保持

        // 前半はグラフを作って前向きDP
        List<List<Node>> graph = buildGraph(reading, splitPos);

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
                ArrayList<Word> words = new ArrayList<>();
                StringBuilder sbReading = new StringBuilder();
                StringBuilder sbSurface = new StringBuilder();
                // BOSとEOSは含まない
                for (Node n = node.next; n.next != null; n = n.next) {
                    sbReading.append(n.word.reading);
                    sbSurface.append(n.word.surface);
                    words.add(n.word);
                }
                Candidate candidate = new Candidate(sbReading.toString(), sbSurface.toString(), words);
                set.add(candidate);
                if (set.size() >= nBest) {
                    break;
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
        // 全角英数
        if (mConvertWideLatin) {
            String s = Converter.toWideLatin(reading);
            if (!s.equals(reading)) {
                set.add(new Candidate(reading, s));
            }
        }
        // 半角カナ
        if (mConvertHalfKana) {
            String s = Converter.toHalfKatakana(reading);
            if (!s.equals(reading)) {
                set.add(new Candidate(reading, s));
            }
        }

        return set.toArray(new Candidate[0]);
    }

    private List<List<Node>> buildGraph(String str, int splitPos) {
        int len = str.length();
        List<List<Node>> graph = new ArrayList<>();
        for (int i = 0; i <= (len + 1); i++) {
            graph.add(i, new ArrayList<>());
        }
        graph.get(0).add(new Node(0, Word.bos)); // BOS
        graph.get(len + 1).add(new Node(len + 1, Word.eos)); // EOS

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
                Set<Word> words = findWords(reading);
                if (words.isEmpty()) {
                    // 単語が見つからない場合は1文字を1単語となるダミーノードを登録する
                    continue;
                }
                for (Word word : words) {
                    Node node = new Node(startPos, word);
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
