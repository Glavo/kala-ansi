/*
 * Copyright 2025 Glavo
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
package kala.ansi;

final class ColorCategory extends Category {
    final int colorCode;

    ColorCategory(String name, int offset, int width, int colorCode) {
        super(name, offset, width, 273);
        this.colorCode = colorCode;

        for (int i = 0; i < 256; i++) {
            makeAttr(
                    "Full(" + i + ")",
                    "\u001b[" + colorCode + ";5;" + i + "m", 17 + i
            );
        }
    }

    @Override
    final String lookupEscape(long applyState) {
        int rawIndex = (int) (applyState >> offset);
        if (rawIndex < 273) return super.lookupEscape(applyState);
        else {
            int index = rawIndex - 273;
            return trueRgbEscape(index >> 16, (index & 0x00FF00) >> 8, index & 0x0000FF);
        }
    }

    @Override
    final Attr lookupAttr(long applyState) {
        int index = (int) (applyState >> offset);
        if (index < 273) return lookupAttrTable[index];
        else return True(index - 273);
    }

    private Attr.Escape true0(int r, int g, int b, int index) {
        return makeAttr0("True(" + r + "," + g + "," + b + ")", trueRgbEscape(r, g, b), 273 + index);
    }

    final String trueRgbEscape(int r, int g, int b) {
        return "\u001b[" + colorCode + ";2;" + r + ";" + g + ";" + b + "m";
    }

    int trueIndex(int r, int g, int b) {
        if (r < 0 || r >= 256) {
            throw new IllegalArgumentException("True parameter `r` must be 0 <= r < 256, not " + r);
        }
        if (g < 0 || g >= 256) {
            throw new IllegalArgumentException("True parameter `g` must be 0 <= g < 256, not " + g);
        }
        if (b < 0 || b >= 256) {
            throw new IllegalArgumentException("True parameter `b` must be 0 <= r < 256, not " + b);
        }

        return r << 16 | g << 8 | b;
    }

    Attr.Escape True(int index) {
        if (index < 0 || index > (1 << 24)) {
            throw new IllegalArgumentException("True parameter `index` must be 273 <= index <= 16777488, not " + index);
        }
        int r = index >> 16;
        int g = (index & 0x00FF00) >> 8;
        int b = index & 0x0000FF;
        return true0(r, g, b, index);
    }

    Attr.Escape True(int r, int g, int b) {
        return true0(r, g, b, trueIndex(r, g, b));
    }
}
