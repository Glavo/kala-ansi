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

class Category {
    private static Category[] categories;

    static Category[] categories() {
        if (categories != null) {
            return categories;
        }
        synchronized (Category.class) {
            if (categories != null) {
                return categories;
            }
            return categories = new Category[]{
                    AnsiString.Color.category,
                    AnsiString.Back.category,
                    AnsiString.Bold.category,
                    AnsiString.Underlined.category,
                    AnsiString.Reversed.category
            };
        }
    }


    final String name;
    final int offset;
    final int width;

    final Attr[] lookupAttrTable;

    Category(String name, int offset, int width) {
        this(name, offset, width, 1 << width);
    }

    Category(String name, int offset, int width, int lookupTableWidth) {
        this.name = name;
        this.offset = offset;
        this.width = width;

        this.lookupAttrTable = new Attr[lookupTableWidth];
    }


    final int mask() {
        return ((1 << width) - 1) << offset;
    }

    String lookupEscape(long applyState) {
        String escape = lookupAttr(applyState).escape;
        return escape == null ? "" : escape;
    }

    Attr lookupAttr(long applyState) {
        return lookupAttrTable[(int) (applyState >> offset)];
    }

    Attr.Escape makeAttr0(String attrName, String escape, long applyValue) {
        return new Attr.Escape(name + "." + attrName, escape, mask(), applyValue << offset);
    }

    Attr.Escape makeAttr(String attrName, String escape, long applyValue) {
        Attr.Escape attr = makeAttr0(attrName, escape, applyValue);
        lookupAttrTable[(int) applyValue] = attr;
        return attr;
    }

    @SuppressWarnings("SameParameterValue")
    Attr.Reset makeNoneAttr(String attrName, long applyValue) {
        Attr.Reset attr = new Attr.Reset(name + "." + attrName, mask(), applyValue << offset);
        lookupAttrTable[(int) applyValue] = attr;
        return attr;
    }

    @Override
    public final String toString() {
        return name;
    }
}
