/*
 * Copyright 2023-2024  kachaya
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.toribane.kkbd;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

public class SoftKey {
    private final int mId;
    private final RectF mRect = new RectF();
    private final Paint mPaintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPaintBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private char mCharacter;
    private Drawable mDrawable;
    private boolean mRepeatable;
    private boolean mPressed;
    private int mForegroundColor;
    private int mBackgroundColor;

    public SoftKey(int id) {
        mId = id;
    }

    public int getId() {
        return mId;
    }

    public void setPos(float x, float y, float w, float h) {
        mRect.left = x;
        mRect.top = y;
        mRect.right = x + w;
        mRect.bottom = y + h;
    }

    public void setColor(int foregroundColor, int backgroundColor) {
        mForegroundColor = foregroundColor;
        mBackgroundColor = backgroundColor;
    }

    public void setCharacter(char c) {
        mCharacter = c;
    }

    public void setDrawable(Drawable d) {
        mDrawable = d;
    }

    public boolean isRepeatable() {
        return mRepeatable;
    }

    public void setRepeatable(boolean b) {
        mRepeatable = b;
    }

    public void setPressed(boolean b) {
        mPressed = b;
    }

    public boolean contains(float x, float y) {
        return mRect.contains(x, y);
    }

    public void draw(Canvas canvas) {
        if (mPressed) {
            mPaintBackground.setColor(Color.GRAY);
        } else {
            mPaintBackground.setColor(mBackgroundColor);
        }
        float r = mRect.height() * 0.1f;
        float ofs = 2.0f;
        canvas.drawRoundRect(mRect.left + ofs, mRect.top + ofs, mRect.right - ofs, mRect.bottom - ofs, r, r, mPaintBackground);
        if (mDrawable != null) {
            drawDrawableKey(canvas);
            return;
        }
        if (mCharacter > ' ') {
            drawCharacterKey(canvas);
        }
    }

    private void drawDrawableKey(Canvas canvas) {
        int r = (int) (mRect.height() / 4.0);
        int left = (int) mRect.centerX() - r;
        int top = (int) mRect.centerY() - r;
        int right = (int) mRect.centerX() + r;
        int bottom = (int) mRect.centerY() + r;
        mDrawable.setBounds(left, top, right, bottom);
        mDrawable.setTint(mForegroundColor);
        mDrawable.draw(canvas);
    }

    private void drawCharacterKey(@NonNull Canvas canvas) {
        String text = Character.toString(mCharacter);
        mPaintText.setColor(mForegroundColor);
        mPaintText.setTextSize(mRect.height() * 0.5f);
        Paint.FontMetrics fontMetrics = mPaintText.getFontMetrics();
        float x = mRect.centerX() - (mPaintText.measureText(text) / 2.0f);
        float y = mRect.centerY() - ((fontMetrics.ascent + fontMetrics.descent) / 2.0f);
        canvas.drawText(text, x, y, mPaintText);
    }
}
