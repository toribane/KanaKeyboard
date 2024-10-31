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

import androidx.annotation.NonNull;

import java.util.HashMap;

public class Converter {

    private static final HashMap<Character, String> halfKatakanaMap = new HashMap<Character, String>() {
        {
            put('あ', "ｱ");
            put('い', "ｲ");
            put('う', "ｳ");
            put('え', "ｴ");
            put('お', "ｵ");
            put('か', "ｶ");
            put('き', "ｷ");
            put('く', "ｸ");
            put('け', "ｹ");
            put('こ', "ｺ");
            put('さ', "ｻ");
            put('し', "ｼ");
            put('す', "ｽ");
            put('せ', "ｾ");
            put('そ', "ｿ");
            put('た', "ﾀ");
            put('ち', "ﾁ");
            put('つ', "ﾂ");
            put('て', "ﾃ");
            put('と', "ﾄ");
            put('な', "ﾅ");
            put('に', "ﾆ");
            put('ぬ', "ﾇ");
            put('ね', "ﾈ");
            put('の', "ﾉ");
            put('は', "ﾊ");
            put('ひ', "ﾋ");
            put('ふ', "ﾌ");
            put('へ', "ﾍ");
            put('ほ', "ﾎ");
            put('ま', "ﾏ");
            put('み', "ﾐ");
            put('む', "ﾑ");
            put('め', "ﾒ");
            put('も', "ﾓ");
            put('や', "ﾔ");
            put('ゆ', "ﾕ");
            put('よ', "ﾖ");
            put('ら', "ﾗ");
            put('り', "ﾘ");
            put('る', "ﾙ");
            put('れ', "ﾚ");
            put('ろ', "ﾛ");
            put('わ', "ﾜ");
            put('を', "ｦ");
            put('ん', "ﾝ");

            put('が', "ｶﾞ");
            put('ぎ', "ｷﾞ");
            put('ぐ', "ｸﾞ");
            put('げ', "ｹﾞ");
            put('ご', "ｺﾞ");
            put('ざ', "ｻﾞ");
            put('じ', "ｼﾞ");
            put('ず', "ｽﾞ");
            put('ぜ', "ｾﾞ");
            put('ぞ', "ｿﾞ");
            put('だ', "ﾀﾞ");
            put('ぢ', "ﾁﾞ");
            put('づ', "ﾂﾞ");
            put('で', "ﾃﾞ");
            put('ど', "ﾄﾞ");
            put('ば', "ﾊﾞ");
            put('び', "ﾋﾞ");
            put('ぶ', "ﾌﾞ");
            put('べ', "ﾍﾞ");
            put('ぼ', "ﾎﾞ");
            put('ぱ', "ﾊﾟ");
            put('ぴ', "ﾋﾟ");
            put('ぷ', "ﾌﾟ");
            put('ぺ', "ﾍﾟ");
            put('ぽ', "ﾎﾟ");

            put('ゔ', "ｳﾞ");

            put('ぁ', "ｧ");
            put('ぃ', "ｨ");
            put('ぅ', "ｩ");
            put('ぇ', "ｪ");
            put('ぉ', "ｫ");
            put('ゃ', "ｬ");
            put('ゅ', "ｭ");
            put('ょ', "ｮ");
            put('っ', "ｯ");
            put('ー', "ｰ");
            put('、', "､");
            put('。', "｡");
            put('「', "｢");
            put('」', "｣");
            put('゛', "ﾞ");
            put('゜', "ﾟ");
            put('・', "･");
        }
    };
    // 全角濁点'゛'結合用 consonant
    private static final HashMap<Character, Character> dakutenMap = new HashMap<Character, Character>() {
        {
            put('う', 'ゔ');

            put('か', 'が');
            put('き', 'ぎ');
            put('く', 'ぐ');
            put('け', 'げ');
            put('こ', 'ご');

            put('さ', 'ざ');
            put('し', 'じ');
            put('す', 'ず');
            put('せ', 'ぜ');
            put('そ', 'ぞ');

            put('た', 'だ');
            put('ち', 'ぢ');
            put('つ', 'づ');
            put('て', 'で');
            put('と', 'ど');

            put('は', 'ば');
            put('ひ', 'び');
            put('ふ', 'ぶ');
            put('へ', 'べ');
            put('ほ', 'ぼ');

            put('ウ', 'ヴ');

            put('カ', 'ガ');
            put('キ', 'ギ');
            put('ク', 'グ');
            put('ケ', 'ゲ');
            put('コ', 'ゴ');

            put('サ', 'ザ');
            put('シ', 'ジ');
            put('ス', 'ズ');
            put('セ', 'ゼ');
            put('ソ', 'ゾ');

            put('タ', 'ダ');
            put('チ', 'ヂ');
            put('ツ', 'ヅ');
            put('テ', 'デ');
            put('ト', 'ド');

            put('ハ', 'バ');
            put('ヒ', 'ビ');
            put('フ', 'ブ');
            put('ヘ', 'ベ');
            put('ホ', 'ボ');
        }
    };

    // 全角半濁点'゜'結合用
    private static final HashMap<Character, Character> handakutenMap = new HashMap<Character, Character>() {
        {
            put('は', 'ぱ');
            put('ひ', 'ぴ');
            put('ふ', 'ぷ');
            put('へ', 'ぺ');
            put('ほ', 'ぽ');

            put('ハ', 'パ');
            put('ヒ', 'ピ');
            put('フ', 'プ');
            put('ヘ', 'ペ');
            put('ホ', 'ポ');
        }
    };

    // 濁点
    public static char combineDakuten(char ch) {
        Character value = dakutenMap.get(ch);
        if (value == null) {
            return '\0';
        }
        return value;
    }

    // 半濁点
    public static char combineHandakuten(char ch) {
        Character value = handakutenMap.get(ch);
        if (value == null) {
            return '\0';
        }
        return value;
    }

    // 全角英数へ変換
    @NonNull
    public static String toWideLatin(@NonNull CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            sb.append(toWideLatin(cs.charAt(i)));
        }
        return sb.toString();
    }

    // 全角英数へ変換
    public static char toWideLatin(char ch) {
        if (ch == '\u0020') {
            return '\u3000';    // 全角スペース
        }
        if (ch == '\u00A5') {   // 「¥」
            return '￥';
        }
        if (ch > '\u0020' && ch < '\u007F') {
            return (char) ((ch - '\u0020') + '\uFF00');
        }
        return ch;
    }

    // 全角カタカナへ変換
    public static String toWideKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            sb.append(toWideKatakana(cs.charAt(i)));
        }
        return sb.toString();
    }

    // 全角カタカナへ変換
    public static char toWideKatakana(char ch) {
        if (ch >= 'ぁ' && ch <= 'ゖ') {
            return (char) (ch - 'ぁ' + 'ァ');
        }
        return ch;
    }

    // 半角カタカナへ変換
    public static String toHalfKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            char key = cs.charAt(i);
            String val = halfKatakanaMap.get(key);
            if (val != null) {
                sb.append(val);
            } else {
                sb.append(key);
            }
        }
        return sb.toString();
    }

}
