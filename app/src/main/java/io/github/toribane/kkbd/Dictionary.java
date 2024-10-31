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

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.btree.BTree;
import jdbm.helper.StringComparator;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;

public class Dictionary {

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

    public Dictionary(Context context) {
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
            e.printStackTrace();
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
            String[] ss = s.split(",", 3);
            int id = Integer.parseInt(ss[0]);
            int cost = Integer.parseInt(ss[1]);
            String surface = ss[2];
            list.add(new Word(surface, id, cost));
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

}
