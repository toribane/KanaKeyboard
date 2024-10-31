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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class SymbolKeyboard extends KeyboardLayout {

    private final static int SYMBOL_TYPE_EMOJI = 0;
    private final static int SYMBOL_TYPE_KIGOU = 1;
    private final FlexboxListViewAdapter mFlexListViewAdapter;
    private final ArrayList<String> mEmojiList;
    private final ArrayList<String> mKigouList;
    private float mSymbolAreaHeight;
    private int mSymbolType;

    public SymbolKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);

        // 上部80%にRecyclerViewを配置
        RecyclerView recyclerView = new RecyclerView(context);
        addView(recyclerView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 4.0f));

        FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(context);
        flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP);
        flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
        flexboxLayoutManager.setAlignItems(AlignItems.STRETCH);

        recyclerView.setLayoutManager(flexboxLayoutManager);

        // RecyclerViewの中身
        mFlexListViewAdapter = new FlexboxListViewAdapter(context);
        recyclerView.setAdapter(mFlexListViewAdapter);

        mEmojiList = buildSymbolList("emoji.txt");
        mKigouList = buildSymbolList("kigou.txt");

        mSymbolType = SYMBOL_TYPE_EMOJI;
        mFlexListViewAdapter.setData(mEmojiList);

        // 下部20%にImageViewを配置
        mImageView = new ImageView(context);
        addView(mImageView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // ImageViewに配置するSoftKey
        mSoftKeys.add(mSymbolKey);
        mSoftKeys.add(mKeyboardViewKey);
        mSoftKeys.add(mSpaceKey);
        mSoftKeys.add(mCursorLeftKey);
        mSoftKeys.add(mCursorRightKey);
        mSoftKeys.add(mBackspaceKey);
        mSoftKeys.add(mEnterKey);
    }

    private ArrayList<String> buildSymbolList(String fileName) {
        ArrayList<String> list = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(mContext.getAssets().open(fileName)));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                list.add(line);
            }
            br.close();
        } catch (IOException ignored) {
        }
        return list;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        SoftKey currentKey = null;  // キーがない場所の場合(外へ出て行った等)
        for (SoftKey softkey : mSoftKeys) {
            if (softkey.contains(x, y - mSymbolAreaHeight)) {
                currentKey = softkey;
                break;
            }
        }
        if (currentKey == null) {
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (currentKey.isRepeatable()) {
                    // 押されたキーの初回リピート
                    mRepeatKey = currentKey;
                    mRepeatHandler.postDelayed(mRepeatRunnable, mRepeatTimeout);
                }
                currentKey.setPressed(true);
                mLastKey = currentKey;
                break;

            case MotionEvent.ACTION_MOVE:
                if (currentKey.equals(mLastKey)) {
                    return true;    // 同じキー内
                }
                if (currentKey.isRepeatable()) {
                    // 移動した先のキーの初回リピート
                    mRepeatKey = currentKey;
                    mRepeatHandler.postDelayed(mRepeatRunnable, mRepeatTimeout);
                }
                if (mLastKey != null) {
                    mLastKey.setPressed(false);
                }
                currentKey.setPressed(true);
                mLastKey = currentKey;
                break;

            case MotionEvent.ACTION_UP:
                currentKey.setPressed(false);
                processSoftKey(currentKey);
                mRepeatKey = null;
                mLastKey = null;
                break;

            default:
                break;
        }
        drawKeyboard();
        return true;
    }

    @Override
    public void processSoftKey(@NonNull SoftKey softKey) {
        int id = softKey.getId();
        if (id == SOFTKEY_ID_SYMBOL) {
            if (mSymbolType == SYMBOL_TYPE_EMOJI) {
                mSymbolType = SYMBOL_TYPE_KIGOU;
                mSymbolKey.setDrawable(mSymbolEmojiDrawable);
                mFlexListViewAdapter.setData(mKigouList);
            } else {
                mSymbolType = SYMBOL_TYPE_EMOJI;
                mSymbolKey.setDrawable(mSymbolKigouDrawable);
                mFlexListViewAdapter.setData(mEmojiList);
            }
            return;
        }
        super.processSoftKey(softKey);
    }

    private void drawKeyboard() {
        if (mCanvas == null) {
            return;
        }
        mCanvas.drawColor(mBackgroundColor);
        for (SoftKey softKey : mSoftKeys) {
            softKey.draw(mCanvas);
        }
        mImageView.setImageBitmap(mBitmap);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        float mKeypadAreaHeight = h / 5.0f;
        mSymbolAreaHeight = h - mKeypadAreaHeight;
        float kw = mWidth / 8.0f;

        mSymbolKey.setPos(kw * 0, 0, kw, mKeypadAreaHeight);
        mKeyboardViewKey.setPos(kw * 1, 0, kw, mKeypadAreaHeight);
        mSpaceKey.setPos(kw * 2, 0, kw * 2, mKeypadAreaHeight);
        mCursorLeftKey.setPos(kw * 4, 0, kw, mKeypadAreaHeight);
        mCursorRightKey.setPos(kw * 5, 0, kw, mKeypadAreaHeight);
        mBackspaceKey.setPos(kw * 6, 0, kw, mKeypadAreaHeight);
        mEnterKey.setPos(kw * 7, 0, kw, mKeypadAreaHeight);

        mBitmap = Bitmap.createBitmap(w, (int) mKeypadAreaHeight, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        drawKeyboard();
    }

    private static class FlexboxListViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTextView;

        FlexboxListViewHolder(View itemView) {
            super(itemView);
            mTextView = itemView.findViewById(R.id.text_view);
        }

        void bindTo(String text) {
            mTextView.setText(text);
            ViewGroup.LayoutParams lp = mTextView.getLayoutParams();
            if (lp instanceof FlexboxLayoutManager.LayoutParams) {
                FlexboxLayoutManager.LayoutParams flexboxLp = (FlexboxLayoutManager.LayoutParams) lp;
                flexboxLp.setFlexGrow(1.0f);
            }
        }
    }

    private class FlexboxListViewAdapter extends RecyclerView.Adapter<FlexboxListViewHolder> {
        private ArrayList<String> data;

        FlexboxListViewAdapter(Context context) {
        }

        public void setData(ArrayList<String> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FlexboxListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.viewholder_text, parent, false);
            view.setOnClickListener(this::onClickListener);
            return new FlexboxListViewHolder(view);
        }

        private void onClickListener(View view) {
            TextView tv = (TextView) view;
            mKeyboardService.handleString(tv.getText().toString());
        }

        @Override
        public void onBindViewHolder(@NonNull FlexboxListViewHolder holder, int position) {
            holder.bindTo(data.get(position));
        }

        @Override
        public int getItemCount() {
            if (data == null) {
                return 0;
            }
            return data.size();
        }
    }
}
