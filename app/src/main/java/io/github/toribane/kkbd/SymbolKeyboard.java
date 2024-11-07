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
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    //
    private final FlexboxListViewAdapter mFlexListViewAdapter;
    private final ArrayList<SymbolGroup> mEmojiList;
    private final ArrayList<SymbolGroup> mKigouList;
    private float mSymbolAreaHeight;
    private int mSymbolType;
    private int mSymbolGroupIndex;
    //
    private LinearLayout mGroupView;
    //
    private HorizontalScrollView mHorizontalScrollView;
    private ArrayList<String> mSymbolList;

    public SymbolKeyboard(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        mContext = context;

        RecyclerView recyclerView = new RecyclerView(context);

        FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(context);
        flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP);
        flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
        flexboxLayoutManager.setAlignItems(AlignItems.STRETCH);

        recyclerView.setLayoutManager(flexboxLayoutManager);

        // RecyclerViewの中身
        mFlexListViewAdapter = new FlexboxListViewAdapter(context);
        recyclerView.setAdapter(mFlexListViewAdapter);

        // シンボルデータ読み込み
        mEmojiList = buildSymbolList("emoji.txt");
        mKigouList = buildSymbolList("kigou.txt");

        // ImageViewに配置するSoftKey
        mImageView = new ImageView(context);
        mSoftKeys.add(mSymbolEmojiKey);
        mSoftKeys.add(mSymbolKigouKey);
        mSoftKeys.add(mKeyboardViewKey);
        mSoftKeys.add(mSpaceKey);
        mSoftKeys.add(mCursorLeftKey);
        mSoftKeys.add(mCursorRightKey);
        mSoftKeys.add(mBackspaceKey);
        mSoftKeys.add(mEnterKey);

        // グループ選択ボタンを配置
        mHorizontalScrollView = new HorizontalScrollView(context);
        mHorizontalScrollView.setBackgroundColor(Color.DKGRAY);

        mGroupView = new LinearLayout(context);
        mGroupView.setOrientation(HORIZONTAL);

        mHorizontalScrollView.addView(mGroupView, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addView(mHorizontalScrollView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        addView(recyclerView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 4.0f));
        addView(mImageView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        //
        mSymbolType = SYMBOL_TYPE_EMOJI;
        mSymbolGroupIndex = 0;

        // 最初の表示
        selectEmojiKeyboard();

    }

    private void selectEmojiKeyboard() {
        mSymbolType = SYMBOL_TYPE_EMOJI;
        int style = R.style.CandidateText;
        Context wrappedContext = new ContextThemeWrapper(mContext, style);

        mGroupView.removeAllViews();
        for (int i = 0; i < mEmojiList.size(); i++) {
            TextView view = new TextView(wrappedContext, null, style);
            String[] ss = mEmojiList.get(i).group.split(" ");
            view.setText(ss[1]);    // 表示用テキスト
            mGroupView.addView(view);
            view.setOnClickListener(this::onClickGroupTextListener);
        }
        mSymbolList = mEmojiList.get(mSymbolGroupIndex).symbolList;
        mFlexListViewAdapter.setData(mSymbolList);
    }

    private void selectKigouKeyboard() {
        mSymbolType = SYMBOL_TYPE_KIGOU;
        int style = R.style.CandidateText;
        Context wrappedContext = new ContextThemeWrapper(mContext, style);

        mGroupView.removeAllViews();
        for (int i = 0; i < mKigouList.size(); i++) {
            TextView view = new TextView(wrappedContext, null, style);
            String[] ss = mKigouList.get(i).group.split(" ");
            view.setText(ss[1]);    // 表示用テキスト
            mGroupView.addView(view);
            view.setOnClickListener(this::onClickGroupTextListener);
        }
        mSymbolList = mKigouList.get(mSymbolGroupIndex).symbolList;
        mFlexListViewAdapter.setData(mSymbolList);
    }

    // グループ選択
    private void onClickGroupTextListener(View view) {
        mSymbolGroupIndex = mGroupView.indexOfChild(view);
        if (mSymbolType == SYMBOL_TYPE_EMOJI) {
            selectEmojiKeyboard();
        }  else {
            selectKigouKeyboard();
        }
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
        mSymbolGroupIndex = 0;
        int id = softKey.getId();
        switch (id) {
            case SOFTKEY_ID_SYMBOL_EMOJI:
                selectEmojiKeyboard();
                break;
            case SOFTKEY_ID_SYMBOL_KIGOU:
                selectKigouKeyboard();
                break;
            default:
                super.processSoftKey(softKey);
                break;
        }
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
        float mKeypadAreaHeight = h / 6.0f; // 6段のうち下1段
        mSymbolAreaHeight = h - mKeypadAreaHeight;

        float kh = mKeypadAreaHeight;
        float kw = mWidth / 9.0f;

        mSymbolKigouKey.setPos(kw * 0, 0, kw, kh);
        mSymbolEmojiKey.setPos(kw * 1, 0, kw, kh);
        mKeyboardViewKey.setPos(kw * 2, 0, kw, kh);
        mSpaceKey.setPos(kw * 3, 0, kw * 2, kh);
        mCursorLeftKey.setPos(kw * 5, 0, kw, kh);
        mCursorRightKey.setPos(kw * 6, 0, kw, kh);
        mBackspaceKey.setPos(kw * 7, 0, kw, kh);
        mEnterKey.setPos(kw * 8, 0, kw, kh);

        mBitmap = Bitmap.createBitmap(w, (int) mKeypadAreaHeight, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        drawKeyboard();
    }

    /*
     * シンボル一覧表示
     */

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

    /*
     * シンボルグループ
     */
    private static class SymbolGroup {
        public String group;
        public ArrayList<String> symbolList;

        public SymbolGroup(String group) {
            this.group = group;
            this.symbolList = new ArrayList<>();
        }
    }

    private ArrayList<SymbolGroup> buildSymbolList(String fileName) {
        ArrayList<SymbolGroup> symbolGroup = new ArrayList<>();
        SymbolGroup list = null;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(mContext.getAssets().open(fileName)));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#")) {
                    list = new SymbolGroup(line);
                    symbolGroup.add(list);
                } else {
                    if (list != null) {
                        list.symbolList.add(line);
                    }
                }
            }
            br.close();
        } catch (IOException ignored) {
        }
        return symbolGroup;
    }


}
