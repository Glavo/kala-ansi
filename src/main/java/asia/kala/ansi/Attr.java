package asia.kala.ansi;

import java.util.Objects;

abstract class Attr extends AnsiString.Attribute {
    final String escape;

    Attr(String escape, long resetMask, long applyMask) {
        super(resetMask, applyMask);
        this.escape = escape;
    }

    @Override
    public final AnsiString.Attribute concat(AnsiString.Attribute other) {
        Objects.requireNonNull(other);

        throw new UnsupportedOperationException(); // TODO
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
