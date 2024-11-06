/*
 * Copyright 2024 kachaya
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
import android.inputmethodservice.InputMethodService;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class KeyboardService extends InputMethodService  implements SharedPreferences.OnSharedPreferenceChangeListener {

    // キーボード
    private ViewGroup mKeyboardLayout;
    private View mCandidateView;
    private ViewGroup mCandidateLayout;
    // シンボル
    private ViewGroup mSymbolKeyboard;
    //
    private Dictionary mDictionary;
    //
    private StringBuilder mInputText;
    private int mConvertLength;
    private int mCandidateIndex;
    private Candidate[] mCandidates;
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
    @Override
    public void onCreate() {
        super.onCreate();
        mDictionary = new Dictionary(this);
        mInputText = new StringBuilder();
    }

    @Override
    public View onCreateInputView() {
        FrameLayout layout = (FrameLayout) LayoutInflater.from(this).inflate(R.layout.input_layout, null);

        mKeyboardLayout = layout.findViewById(R.id.keyboard_layout);
        mSymbolKeyboard = layout.findViewById(R.id.emoji_keyboard);

        mCandidateView = layout.findViewById(R.id.candidate_view);
        mCandidateLayout = layout.findViewById(R.id.candidate_layout);

        return layout;
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        super.onStartInputView(editorInfo, restarting);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mConvertHalfKana = sharedPreferences.getBoolean("convert_half_kana", false);
        mConvertWideLatin = sharedPreferences.getBoolean("convert_wide_latin", false);

        mKeyboardLayout.setVisibility(View.VISIBLE);
        mSymbolKeyboard.setVisibility(View.INVISIBLE);

        mInputText.setLength(0);
        mConvertLength = 0;
        mCandidateLayout.removeAllViewsInLayout();
        mCandidateIndex = -1;
    }

    private void icSetComposingText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        SpannableString ss = new SpannableString(mInputText);
        int color = ContextCompat.getColor(this, R.color.select_bg);
        ss.setSpan(new BackgroundColorSpan(color), 0, mConvertLength, Spanned.SPAN_COMPOSING);
        ss.setSpan(new UnderlineSpan(), 0, ss.length(), Spanned.SPAN_COMPOSING);

        ic.setComposingText(ss, 1);
    }

    private void icCommitText(CharSequence cs) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(cs, 1);
        }
    }

    // 入力中テキストをコミット
    private void commitInputText() {
        icCommitText(mInputText);
        mInputText.setLength(0);
        mConvertLength = 0;
        mCandidateLayout.removeAllViewsInLayout();
        mCandidateIndex = -1;
    }

    private void commitCandidateText() {
        Candidate candidate = mCandidates[mCandidateIndex];
        mDictionary.addLearning(candidate.key, candidate.value);

        mCandidateLayout.removeAllViewsInLayout();
        mCandidateIndex = -1;

        icCommitText(candidate.value);
        mInputText.setLength(0);
        mConvertLength = 0;
    }

    public void handleString(String s) {
        if (mCandidateIndex >= 0) {
            // 候補選択中なら確定する
            commitCandidateText();
        }
        // 直接コミット
        icCommitText(s);
    }

    public void handleCharacter(char c) {
        if (mCandidateIndex >= 0) {
            // 候補選択中なら確定する
            commitCandidateText();
        }
        int len = mInputText.length();
        if (len > 0) {
            char c2;
            if (c == '゛') {
                c2 = Converter.combineDakuten(mInputText.charAt(len - 1));
                if (c2 != '\0') {
                    mInputText.deleteCharAt(len - 1);
                    c = c2;
                }
            } else if (c == '゜') {
                c2 = Converter.combineHandakuten(mInputText.charAt(len - 1));
                if (c2 != '\0') {
                    mInputText.deleteCharAt(len - 1);
                    c = c2;
                }
            }
        }
        mInputText.append(c);
        mConvertLength = mInputText.length();
        icSetComposingText();
        buildConversionCandidate();
    }

    public void handleBackspace() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            return;
        }
        if (mCandidateIndex >= 0) {
            // 候補選択中→候補未選択に戻す
            mCandidateIndex = -1;
            selectCandidate();
            return;
        }
        // 候補未選択→入力テキストの最後の文字を削除して候補を作り直す
        mInputText.deleteCharAt(mInputText.length() - 1);
        mConvertLength = mInputText.length();
        icSetComposingText();
        buildConversionCandidate();
    }

    public void handleEnter() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            return;
        }
        if (mCandidateIndex >= 0) {
            // 候補選択中→選択中の候補をコミット
            commitCandidateText();
        } else {
            // 候補未選択→入力テキストをそのままコミット
            commitInputText();
            icCommitText(mInputText);
            mInputText.setLength(0);
            mCandidateLayout.removeAllViewsInLayout();
            mCandidateIndex = -1;
        }
    }

    public void handleSpace() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_SPACE);
            return;
        }
        mCandidateIndex = (mCandidateIndex + 1) % mCandidateLayout.getChildCount();
        selectCandidate();
    }

    public void handleCursorLeft() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
            return;
        }
        mConvertLength--;
        if (mConvertLength < 1) {
            mConvertLength = 1;
        }
        icSetComposingText();
        buildConversionCandidate();
    }

    public void handleCursorRight() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
            return;
        }
        mConvertLength++;
        if (mConvertLength > mInputText.length()) {
            mConvertLength = mInputText.length();
        }
        icSetComposingText();
        buildConversionCandidate();
    }

    public void handleCursorUp() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP);
        }
    }

    public void handleCursorDown() {
        if (mInputText.length() == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN);
        }
    }

    /**
     * シンボルキーボードに切り替え
     */
    public void handleSymbol() {
        if (mCandidateIndex >= 0) {
            // 候補選択中なら確定する
            commitCandidateText();
        }
        if (mInputText.length() > 0) {
            commitInputText();
        }

        mKeyboardLayout.setVisibility(View.INVISIBLE);
        mSymbolKeyboard.setVisibility(View.VISIBLE);
    }

    /**
     * テキストキーボードに切り替え
     */
    public void handleKeyboard() {
        if (mInputText.length() > 0) {
            commitInputText();
        }
        mKeyboardLayout.setVisibility(View.VISIBLE);
        mSymbolKeyboard.setVisibility(View.INVISIBLE);
    }

    private void onClickCandidateTextListener(View view) {
        mCandidateIndex = mCandidateLayout.indexOfChild(view);
        commitCandidateText();
    }

    /**
     * 候補ビューに候補一覧を表示する
     */
    private void setCandidateText() {
        mCandidateIndex = -1;
        mCandidateLayout.removeAllViewsInLayout();
        mCandidateView.scrollTo(0, 0);
        if (mCandidates == null) {
            return;
        }
        int style = R.style.CandidateText;
        Context context = new ContextThemeWrapper(this, style);
        for (Candidate candidate : mCandidates) {
            TextView view = new TextView(context, null, style);
            view.setText(candidate.value);    // 表示用テキスト
            view.setOnClickListener(this::onClickCandidateTextListener);
            mCandidateLayout.addView(view);
        }
    }

    private void selectCandidate() {
        TextView view;
        int cX = mCandidateView.getScrollX();
        int cW = mCandidateView.getWidth();
        for (int i = 0; i < mCandidateLayout.getChildCount(); i++) {
            view = (TextView) mCandidateLayout.getChildAt(i);
            if (i == mCandidateIndex) {
                // 見える場所にスクロールする
                int bT = view.getTop();
                int bL = view.getLeft();
                int bR = view.getRight();
                if (bL < cX) {
                    mCandidateView.scrollTo(bL, bT);
                }
                if (bR > (cX + cW)) {
                    mCandidateView.scrollTo(bR - cW, bT);
                }
                view.setSelected(true);
            } else {
                view.setSelected(false);
            }
        }
    }

    /*
     * 現在入力中の文字列から変換候補を作り出す
     */
    private final Word BOS = new Word("BOS", 0, 0);
    private final Word EOS = new Word("EOS", 0, 0);

    private void buildConversionCandidate() {
        String str = mInputText.toString();
        int len = str.length();

        int nBest = 20;

        Set<String> surfaces = new LinkedHashSet<>();// 追加順、重複なし

        // 学習辞書から完全一致するものをすべて追加
        ArrayList<String> learningWords = mDictionary.findLearningWord(str);
        if (learningWords != null) {
            surfaces.addAll(learningWords);
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
                if (mConvertLength != len) {
                    if (startPos <= mConvertLength && endPos > mConvertLength) {
                        continue;
                    }
                }
                String substr = str.substring(startPos - 1, endPos);
                ArrayList<Word> words = mDictionary.findWord(substr);
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
                    int edgeCost = mDictionary.getEdgeCost(prevNode, node);
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
                    int edgeCost = mDictionary.getEdgeCost(prevNode, node);
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
        mCandidates = candidates.toArray(new Candidate[0]);
        setCandidateText();
    }

    private String buildSurface(Node node) {
        StringBuilder sb = new StringBuilder();
        for (node = node.next; node.next != null; node = node.next) {
            sb.append(node.word.surface);
        }
        return sb.toString();
    }

}
