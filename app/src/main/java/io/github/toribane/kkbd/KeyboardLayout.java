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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;

public class KeyboardLayout extends LinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {

    //
    public final static int SOFTKEY_ID_SPACE = -1;
    public final static int SOFTKEY_ID_BACKSPACE = -2;
    public final static int SOFTKEY_ID_ENTER = -3;
    public final static int SOFTKEY_ID_CURSOR_LEFT = -4;
    public final static int SOFTKEY_ID_CURSOR_RIGHT = -5;
    public final static int SOFTKEY_ID_CURSOR_UP = -6;
    public final static int SOFTKEY_ID_CURSOR_DOWN = -7;
    //
    public final static int SOFTKEY_ID_SHIFT = -8;
    public final static int SOFTKEY_ID_KEYBOARD_VIEW = -9;
    public final static int SOFTKEY_ID_SYMBOL_VIEW = -10;
    public final static int SOFTKEY_ID_LANGUAGE = -11;
    public final static int SOFTKEY_ID_SYMBOL_EMOJI = -12;
    public final static int SOFTKEY_ID_SYMBOL_KIGOU = -13;

    public Context mContext;
    public KeyboardService mKeyboardService;

    public SoftKey mBackspaceKey;
    public SoftKey mCursorLeftKey;
    public SoftKey mCursorRightKey;
    public SoftKey mEnterKey;
    public SoftKey mKeyboardViewKey;
    public SoftKey mLastKey;
    public SoftKey mRepeatKey;
    public SoftKey mShiftKey;
    public SoftKey mSpaceKey;
    public SoftKey mSymbolViewKey;
    public SoftKey mLanguageKey;
    public SoftKey mSymbolEmojiKey;
    public SoftKey mSymbolKigouKey;

    public ArrayList<SoftKey> mSoftKeys;

    public Bitmap mBitmap;
    public Canvas mCanvas;
    public Drawable mShiftLockDrawable;
    public Drawable mShiftNoneDrawable;
    public Drawable mShiftSingleDrawable;
    public Drawable mLangJaDrawable;
    public Drawable mLangEnDrawable;
    //
    public Handler mRepeatHandler;
    public ImageView mImageView;

    public int mBackgroundColor;
    public int mCharacterKeyBackgroundColor;
    public int mFunctionKeyBackgroundColor;
    public int mKeyForegroundColor;

    public boolean mLanguageJapaneseFlag;
    public boolean mShiftLockFlag;
    public boolean mShiftSingleFlag;

    public int mWidth;
    public int mHeight;

    public int mRepeatTimeout;
    public int mRepeatDelay;
    public final Runnable mRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRepeatKey == null) {
                return;
            }
            processSoftKey(mRepeatKey);
            mRepeatHandler.postDelayed(this, mRepeatDelay);
        }
    };

    public KeyboardLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mKeyboardService = (KeyboardService) context;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        mRepeatTimeout = Integer.parseInt(sharedPreferences.getString("key_repeat_timeout", "400"));
        mRepeatDelay = Integer.parseInt(sharedPreferences.getString("key_repeat_delay", "50"));
        mRepeatHandler = new Handler(Looper.getMainLooper());

        Resources res = getResources();
        mBackgroundColor = res.getColor(R.color.background, null);
        mCharacterKeyBackgroundColor = res.getColor(R.color.character_key_background, null);
        mFunctionKeyBackgroundColor = res.getColor(R.color.function_key_background, null);
        mKeyForegroundColor = res.getColor(R.color.key_foreground, null);

        mShiftNoneDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_shift_none, null);
        mShiftSingleDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_shift_single, null);
        mShiftLockDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_shift_lock, null);
        mLangJaDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_lang_ja, null);
        mLangEnDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_lang_en, null);

        mLanguageJapaneseFlag = true;
        mShiftSingleFlag = false;
        mShiftLockFlag = false;

        mSoftKeys = new ArrayList<>();

        mShiftKey = new SoftKey(SOFTKEY_ID_SHIFT);
        mShiftKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mShiftKey.setDrawable(mShiftNoneDrawable);

        mSymbolEmojiKey = new SoftKey(SOFTKEY_ID_SYMBOL_EMOJI);
        mSymbolEmojiKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mSymbolEmojiKey.setDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_symbol_emoji, null));

        mSymbolKigouKey = new SoftKey(SOFTKEY_ID_SYMBOL_KIGOU);
        mSymbolKigouKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mSymbolKigouKey.setDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_symbol_kigou, null));

        mLanguageKey = new SoftKey(SOFTKEY_ID_LANGUAGE);
        mLanguageKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mLanguageKey.setDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_lang_ja, null));

        mSymbolViewKey = new SoftKey(SOFTKEY_ID_SYMBOL_VIEW);
        mSymbolViewKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mSymbolViewKey.setDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_symbol_view, null)); // ☺

        mKeyboardViewKey = new SoftKey(SOFTKEY_ID_KEYBOARD_VIEW);
        mKeyboardViewKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mKeyboardViewKey.setCharacter('⌨');   // u2328

        mSpaceKey = new SoftKey(SOFTKEY_ID_SPACE);
        mSpaceKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mSpaceKey.setCharacter('⎵');   // u23B5

        mCursorLeftKey = new SoftKey(SOFTKEY_ID_CURSOR_LEFT);
        mCursorLeftKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mCursorLeftKey.setCharacter('◂');  // u25C2
        mCursorLeftKey.setRepeatable(true);

        mCursorRightKey = new SoftKey(SOFTKEY_ID_CURSOR_RIGHT);
        mCursorRightKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mCursorRightKey.setCharacter('▸'); // u25B8
        mCursorRightKey.setRepeatable(true);

        mBackspaceKey = new SoftKey(SOFTKEY_ID_BACKSPACE);
        mBackspaceKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mBackspaceKey.setCharacter('⌫');   // u232B
        mBackspaceKey.setRepeatable(true);

        mEnterKey = new SoftKey(SOFTKEY_ID_ENTER);
        mEnterKey.setColor(mKeyForegroundColor, mFunctionKeyBackgroundColor);
        mEnterKey.setCharacter('⏎');   // u23CE

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (key == null) {
            return;
        }
        if (key.equals("key_repeat_timeout")) {
            mRepeatTimeout = Integer.parseInt(sharedPreferences.getString(key, "400"));
        } else if (key.equals("key_repeat_delay")) {
            mRepeatDelay = Integer.parseInt(sharedPreferences.getString(key, "50"));
        }
    }

    public void setJapaneseInputMode(boolean mode) {
        mLanguageJapaneseFlag = mode;
        if (mLanguageJapaneseFlag) {
            mLanguageKey.setDrawable(mLangJaDrawable);
        } else {
            mLanguageKey.setDrawable(mLangEnDrawable);
        }
    }

    public char getKeyChar(int id) {
        return '\0';
    }

    public void processSoftKey(@NonNull SoftKey softKey) {
        int id = softKey.getId();
        if (id >= 0) {
            mKeyboardService.handleCharacter(getKeyChar(id));
            mShiftSingleFlag = false;
            return;
        }
        switch (id) {
            case SOFTKEY_ID_SHIFT:
                if (mShiftLockFlag) {
                    mShiftLockFlag = false;
                    mShiftSingleFlag = false;
                } else {
                    if (mShiftSingleFlag) {
                        mShiftLockFlag = true;
                        mShiftSingleFlag = false;
                    } else {
                        mShiftSingleFlag = true;
                    }
                }
                break;
            case SOFTKEY_ID_LANGUAGE:
                mLanguageJapaneseFlag = !mLanguageJapaneseFlag;
                if (mLanguageJapaneseFlag) {
                    mLanguageKey.setDrawable(mLangJaDrawable);
                } else {
                    mLanguageKey.setDrawable(mLangEnDrawable);
                }
                break;
            case SOFTKEY_ID_KEYBOARD_VIEW:
                mKeyboardService.handleKeyboard();
                break;
            case SOFTKEY_ID_SYMBOL_VIEW:
                mKeyboardService.handleSymbol();
                break;
            case SOFTKEY_ID_SPACE:
                mKeyboardService.handleSpace();
                break;
            case SOFTKEY_ID_CURSOR_LEFT:
                mKeyboardService.handleCursorLeft();
                break;
            case SOFTKEY_ID_CURSOR_RIGHT:
                mKeyboardService.handleCursorRight();
                break;
            case SOFTKEY_ID_CURSOR_UP:
                mKeyboardService.handleCursorUp();
                break;
            case SOFTKEY_ID_CURSOR_DOWN:
                mKeyboardService.handleCursorDown();
                break;
            case SOFTKEY_ID_BACKSPACE:
                mKeyboardService.handleBackspace();
                break;
            case SOFTKEY_ID_ENTER:
                mKeyboardService.handleEnter();
                break;
            default:
                break;
        }
    }
}
