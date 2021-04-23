package kala.ansi;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("SpellCheckingInspection")
class AnsiStringTest {
    static final String R = ((Attr) AnsiString.Color.Red).escape;
    static final String G = ((Attr) AnsiString.Color.Green).escape;
    static final String B = ((Attr) AnsiString.Color.Blue).escape;
    static final String Y = ((Attr) AnsiString.Color.Yellow).escape;
    static final String UND = ((Attr) AnsiString.Underlined.On).escape;
    static final String DUND = ((Attr) AnsiString.Underlined.Off).escape;
    static final String REV = ((Attr) AnsiString.Reversed.On).escape;
    static final String DREV = ((Attr) AnsiString.Reversed.Off).escape;
    static final String DCOL = ((Attr) AnsiString.Color.Reset).escape;
    static final String RES = ((Attr) AnsiString.Reset).escape;

    /**
     * ANSI escape sequence to reset text color
     */
    static final String RTC = ((Attr) AnsiString.Color.Reset).escape;

    static final String rgbOps = String.format("+++%s---%s***%s///", R, G, B);
    static final String rgb = R + G + B;

    @Test
    void parsing() {
        String r = AnsiString.parse(rgbOps).toString();

        assertEquals("+++---***///", AnsiString.parse(rgbOps).getPlain());
        assertEquals("", AnsiString.parse(rgb).getPlain());
        assertEquals(rgbOps + RTC, r);
        assertEquals("", AnsiString.parse(rgb).toString());
    }

    @Test
    void equality() {
        assertEquals(AnsiString.Color.Red.overlay("foo"), AnsiString.Color.Red.overlay("foo"));
    }

    @Test
    void concat() {
        AnsiString s = AnsiString.parse(rgbOps);

        String expected = rgbOps + RTC + rgbOps + RTC + rgbOps + RTC;

        assertEquals(expected, s.concat(s).concat(s).toString());
        assertEquals(expected, AnsiString.concat(s, s, s).toString());
    }

    @Test
    void get() {
        AnsiString str = AnsiString.parse(rgbOps);
        long w = AnsiString.Attribute.empty().transform(0);
        long r = AnsiString.Color.Red.transform(0);
        long g = AnsiString.Color.Green.transform(0);
        long b = AnsiString.Color.Blue.transform(0);

        long[] states = str.getStates();

        assertEquals("+++---***///", str.getPlain());
        assertEquals('+', str.charAt(0));
        assertEquals('+', str.charAt(1));
        assertEquals('+', str.charAt(2));
        assertEquals('-', str.charAt(3));
        assertEquals('-', str.charAt(4));
        assertEquals('-', str.charAt(5));
        assertEquals('*', str.charAt(6));
        assertEquals('*', str.charAt(7));
        assertEquals('*', str.charAt(8));
        assertEquals('/', str.charAt(9));
        assertEquals('/', str.charAt(10));
        assertEquals('/', str.charAt(11));
        assertArrayEquals(new long[]{w, w, w, r, r, r, g, g, g, b, b, b}, states);
        assertEquals(w, str.stateAt(0));
        assertEquals(w, str.stateAt(1));
        assertEquals(w, str.stateAt(2));
        assertEquals(r, str.stateAt(3));
        assertEquals(r, str.stateAt(4));
        assertEquals(r, str.stateAt(5));
        assertEquals(g, str.stateAt(6));
        assertEquals(g, str.stateAt(7));
        assertEquals(g, str.stateAt(8));
        assertEquals(b, str.stateAt(9));
        assertEquals(b, str.stateAt(10));
        assertEquals(b, str.stateAt(11));
    }

    @Test
    void substring() {
        String substringed = AnsiString.parse(rgbOps).substring(4, 9).toString();
        assertEquals(String.format("%s--%s***%s", R, G, RTC), substringed);

        String defaultString = AnsiString.parse(rgbOps).toString();


        AnsiString parsed = AnsiString.parse(rgbOps);
        String noOpSubstringed2 = parsed.substring(0, parsed.length()).toString();
        assertEquals(defaultString, noOpSubstringed2);
    }

    @Nested
    @DisplayName("overlay")
    class OverlayTest {
        @Test
        void simple() {
            AnsiString overlayed = AnsiString.parse(rgbOps).overlay(AnsiString.Color.Yellow, 4, 7);
            String expected = String.format("+++%s-%s--*%s**%s///%s", R, Y, G, B, RTC);

            assertEquals(expected, overlayed.toString());
        }

        @Test
        void resetty() {
            String resetty = String.format("+%s++%s--%s-%s%s***%s///", RES, R, RES, RES, G, B);
            String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Color.Yellow, 4, 7).toString();
            String expected = String.format("+++%s-%s--*%s**%s///%s", R, Y, G, B, RTC);
            assertEquals(expected, overlayed);
        }

        @Test
        void mixedResetUnderline() {
            String resetty = String.format("+%s++%s--%s-%s%s***%s///", RES, R, RES, UND, G, B);
            String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Color.Yellow, 4, 7).toString();
            String expected = String.format("+++%s-%s--%s*%s**%s///%s%s", R, Y, UND, G, B, DCOL, DUND);

            assertEquals(expected, overlayed);
        }

        @Nested
        @DisplayName("underlines")
        class Underlines {
            final String resetty = String.format("%s#%s    %s#%s", UND, RES, UND, RES);

            @Test
            void underlineBug() {
                String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Reversed.On, 0, 2).toString();
                String expected = String.format("%s%s#%s %s   %s#%s", UND, REV, DUND, DREV, UND, DUND);
                assertEquals(expected, overlayed);
            }

            @Test
            void barelyOverlapping() {
                String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Reversed.On, 0, 1).toString();
                String expected = String.format("%s%s#%s%s    %s#%s", UND, REV, DUND, DREV, UND, DUND);
                assertEquals(expected, overlayed);
            }

            @Test
            void endOfLine() {
                String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Reversed.On, 5, 6).toString();
                String expected = String.format("%s#%s    %s%s#%s%s", UND, DUND, UND, REV, DUND, DREV);
                assertEquals(expected, overlayed);
            }

            @Test
            void overshoot() {
                assertThrows(IndexOutOfBoundsException.class, () -> {
                    AnsiString.parse(resetty).overlay(AnsiString.Reversed.On, 5, 10);
                });
            }

            @Test
            void empty() {
                String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Reversed.On, 0, 0).toString();
                String expected = String.format("%s#%s    %s#%s", UND, DUND, UND, DUND);
                assertEquals(expected, overlayed);
            }

            @Test
            void singleContent() {
                String overlayed = AnsiString.parse(resetty).overlay(AnsiString.Reversed.On, 2, 4).toString();
                String expected = String.format("%s#%s %s  %s %s#%s", UND, DUND, REV, DREV, UND, DUND);
                assertEquals(expected, overlayed);
            }
        }

        @Test
        void overlayAll() {
            String overlayed = AnsiString.parse(rgbOps).overlayAll(
                    AnsiString.Color.Yellow.withRange(4, 7),
                    AnsiString.Underlined.On.withRange(4, 7),
                    AnsiString.Underlined.Off.withRange(5, 6),
                    AnsiString.Color.Blue.withRange(7, 9)
            ).toString();
            String expected = String.format("+++%s-%s%s-%s-%s*%s%s**///%s", R, Y, UND, DUND, UND, B, DUND, DCOL);
            assertEquals(expected, overlayed);
        }
    }
}
