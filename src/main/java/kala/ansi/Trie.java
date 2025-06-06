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

import java.util.*;

@SuppressWarnings("unchecked")
final class Trie {
    private static final Trie[] EMPTY_ARRAY = new Trie[0];

    static final Trie parseMap;

    static {
        String[] ks = new String[554];
        Object[] vs = new Object[554];
        int i = 0;

        for (Category category : Category.categories()) {
            for (Attr attr : category.lookupAttrTable) {
                if (attr.escape != null) {
                    ks[i] = attr.escape.substring(2);
                    vs[i] = attr;
                    ++i;
                }
            }
        }

        ks[i] = AnsiString.RESET.substring(2);
        vs[i] = AnsiString.Reset;
        ks[i + 1] = "38;2;";
        vs[i + 1] = AnsiString.Color.category;
        ks[i + 2] = "48;2;";
        vs[i + 2] = AnsiString.Back.category;


        parseMap = new Trie('\u001b', '\u001b',
                new Trie[]{
                        new Trie('[', '[',
                                new Trie[]{trie(Arrays.asList(ks), Arrays.asList(vs))}, null)},
                null);
    }

    private static Trie trie(List<String> keys, List<Object> values) {
        final int size = keys.size();
        if (size == 1 && keys.get(0).isEmpty()) {
            return new Trie(values.get(0));
        }
        char max = Character.MIN_VALUE;
        char min = Character.MAX_VALUE;

        for (String key : keys) {
            char ch = key.charAt(0);
            if (ch > max) {
                max = ch;
            }
            if (ch < min) {
                min = ch;
            }
        }

        final int tl = max - min + 1;
        ArrayList<Object>[] temp = (ArrayList<Object>[]) new ArrayList<?>[tl];
        for (int i = 0; i < size; i++) {
            String key = keys.get(i);
            char ch = key.charAt(0);
            ArrayList<Object> l = temp[ch - min];
            if (l == null) {
                temp[ch - min] = l = new ArrayList<>();
            }
            l.add(keys.get(i));
            l.add(values.get(i));
        }

        Trie[] arr = new Trie[tl];
        for (int i = 0; i < tl; i++) {
            if (temp[i] != null) {
                ArrayList<String> nks = new ArrayList<>();
                ArrayList<Object> nvs = new ArrayList<>();

                Iterator<Object> it = temp[i].iterator();
                while (it.hasNext()) {
                    nks.add(((String) it.next()).substring(1));
                    nvs.add(it.next());
                }
                arr[i] = trie(nks, nvs);
            }
        }
        return new Trie(min, max, arr, null);
    }

    final char min;
    final char max;
    final Trie[] arr;
    final Object value;

    private Trie(Object terminalValue) {
        this.min = 0;
        this.max = 0;
        this.arr = EMPTY_ARRAY;
        this.value = terminalValue;
    }

    private Trie(char min, char max, Trie[] arr, Object value) {
        this.min = min;
        this.max = max;
        this.arr = arr;
        this.value = value;
    }

    ValueWithLength query(CharSequence input, final int index) {
        final int inputLength = input.length();
        int offset = index;
        Trie currentNode = this;

        while (true) {
            if (currentNode.value != null) {
                return new ValueWithLength(offset - index, currentNode.value);
            }
            if (offset >= inputLength) {
                return null;
            }
            char ch = input.charAt(offset);
            if (ch > currentNode.max || ch < currentNode.min) {
                return null;
            }
            currentNode = currentNode.arr[ch - currentNode.min];
            ++offset;
        }
    }

}