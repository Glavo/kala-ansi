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

abstract class Attr extends AnsiString.Attribute {
    final String escape;

    Attr(String escape, long resetMask, long applyMask) {
        super(resetMask, applyMask);
        this.escape = escape;
    }

    @Override
    public final AnsiString.Attribute concat(AnsiString.Attribute other) {
        if (other == null) {
            throw new NullPointerException();
        }

        if (other instanceof Attrs) {
            return other.concat(this);
        } else {
            return of(this, other);
        }
    }

    static final class Escape extends Attr {
        private final String str;

        Escape(String name, String escape, long resetMask, long applyMask) {
            super(escape, resetMask, applyMask);
            str = escape + name + AnsiString.RESET;
        }

        @Override
        public final String toString() {
            return str;
        }
    }

    static final class Reset extends Attr {
        final String name;

        Reset(String name, long resetMask, long applyMask) {
            super(null, resetMask, applyMask);
            this.name = name;
        }

        @Override
        public final String toString() {
            return name;
        }
    }
}
