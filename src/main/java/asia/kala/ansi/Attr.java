package asia.kala.ansi;

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
            return AnsiString.Attribute.of(this, other);
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
