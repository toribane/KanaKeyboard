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
import android.text.InputType;
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

public class KeyboardService extends InputMethodService implements SharedPreferences.OnSharedPreferenceChangeListener {

    // キーボード
    private ViewGroup mKeyboardLayout;
    private View mCandidateView;
    private ViewGroup mCandidateLayout;
    private KeyboardLayout mJiskanaKeyboard;
    // シンボル
    private KeyboardLayout mSymbolKeyboard;
    //
    private Dictionary mDictionary;
    //
    private StringBuilder mInputText;
    private int mConvertLength;
    private int mCandidateIndex;
    private Candidate[] mCandidates;
    // 入力モード、onStartInputView()で決まる
    private boolean mInputJapanese; // 日本語入力モード
    private boolean mInputPassword; // 入力フィールドはパスワード

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (key == null) {
            return;
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
        mSymbolKeyboard = layout.findViewById(R.id.symbol_keyboard);
        mJiskanaKeyboard = layout.findViewById(R.id.jiskana_keyboard);
        mCandidateView = layout.findViewById(R.id.candidate_view);
        mCandidateLayout = layout.findViewById(R.id.candidate_layout);

        return layout;
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        super.onStartInputView(editorInfo, restarting);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        mInputJapanese = true;
        mInputPassword = false;
        switch (editorInfo.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
            case InputType.TYPE_CLASS_PHONE:
                mInputJapanese = false;
                break;
            case InputType.TYPE_CLASS_TEXT:
                switch (editorInfo.inputType & InputType.TYPE_MASK_VARIATION) {
                    case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                    case InputType.TYPE_TEXT_VARIATION_PASSWORD:
//                    case InputType.TYPE_TEXT_VARIATION_URI:
                    case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                    case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                    case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                        mInputPassword = true;
                        mInputJapanese = false;
                        break;
                    default:
                        break;
                }
            default:
                break;
        }
        mJiskanaKeyboard.setJapaneseInputMode(mInputJapanese);

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
        if (mInputPassword) {
            String s = String.valueOf(c);
            icCommitText(s);
            return;
        }
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
        mCandidates = mDictionary.buildConversionCandidate(mInputText, mConvertLength);
        setCandidateText();
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
        mCandidates = mDictionary.buildConversionCandidate(mInputText, mConvertLength);
        setCandidateText();
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
        mCandidates = mDictionary.buildConversionCandidate(mInputText, mConvertLength);
        setCandidateText();
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
        mCandidates = mDictionary.buildConversionCandidate(mInputText, mConvertLength);
        setCandidateText();
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
}
