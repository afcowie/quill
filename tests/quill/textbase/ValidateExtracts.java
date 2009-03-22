/*
 * ValidateExtracts.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the suite it is a part of, are made available
 * to you by the authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */

package quill.textbase;

import junit.framework.TestCase;

/**
 * Make sure the extraction and reversing properties of Extract and the
 * extractRange() which creates them hold.
 * 
 * @author Andrew Cowie
 */
public class ValidateExtracts extends TestCase
{
    public final void testExtractRange() {
        final Text text;
        Extract extract;

        text = new Text();
        text.append(new StringSpan("Hello World", null));

        extract = text.extractRange(1, 3);
        assertEquals("Hello World", text.toString());
        assertEquals(1, extract.range.length);
        assertEquals("ell", extract.range[0].getText());

        extract = text.extractRange(0, 11);
        assertEquals(3, extract.range.length);
        assertEquals("H", extract.range[0].getText());
        assertEquals("ell", extract.range[1].getText());
        assertEquals("o World", extract.range[2].getText());
    }

    /*
     * This used to check that extractRange() would deal with inverted
     * parameters, but we later ran into a problem whereby we need the
     * absolute offset to construct the Change objects, and had no [good] way
     * to get the correct offset value back.
     */
    public final void testWidthNegative() {
        final Text text;
        Extract extract;

        text = new Text();
        text.append(new StringSpan("Hello World", null));

        try {
            extract = text.extractRange(9, -2);
            fail();
            extract.getWidth();
        } catch (IllegalArgumentException iae) {
            // good
        }
    }

    public final void testExtractLines() {
        final Text text;
        Extract[] lines;
        Extract extract;
        Span span;

        text = new Text();
        text.append(new StringSpan("Hello World", null));
        text.append(new CharacterSpan('\n', null));
        text.append(new StringSpan("Goodbye Eternity", null));
        assertEquals("Hello World\nGoodbye Eternity", text.toString());

        lines = text.extractLines();
        assertEquals(2, lines.length);

        extract = lines[0];
        assertNotNull(extract);
        assertEquals(1, extract.size());
        span = extract.get(0);
        assertEquals("Hello World", span.getText());

        extract = lines[1];
        assertNotNull(extract);
        assertEquals(1, extract.size());
        span = extract.get(0);
        assertEquals("Goodbye Eternity", span.getText());
    }

    /*
     * Corner case: only a single span
     */
    public final void testExtractLinesSingle() {
        final Text text;
        Extract[] lines;
        Extract extract;
        Span span;

        text = new Text();
        text.append(new StringSpan("Hello World", null));

        lines = text.extractLines();
        assertEquals(1, lines.length);

        extract = lines[0];
        assertNotNull(extract);
        assertEquals(1, extract.size());
        span = extract.get(0);
        assertEquals("Hello World", span.getText());
    }

    /*
     * Corner case: no content
     */
    public final void testExtractLinesNone() {
        final Text text;
        Extract[] lines;

        text = new Text();

        lines = text.extractLines();
        assertEquals(0, lines.length);
    }

    /*
     * Corner case: only a single CharacterSpan
     */
    public final void testExtractLinesChar() {
        final Text text;
        Extract[] lines;
        Extract extract;
        Span span;

        text = new Text();
        text.append(new CharacterSpan('H', null));

        lines = text.extractLines();
        assertEquals(1, lines.length);

        extract = lines[0];
        assertNotNull(extract);
        assertEquals(1, extract.size());
        span = extract.get(0);
        assertEquals("H", span.getText());
    }

    /*
     * Corner case: only a single span
     */
    public final void testExtractLinesTwoEmpty() {
        final Text text;
        Extract[] lines;
        Extract extract;

        text = new Text();
        text.append(new CharacterSpan('\n', null));

        lines = text.extractLines();
        assertEquals(2, lines.length);

        extract = lines[0];
        assertNotNull(extract);
        assertEquals(0, extract.size());
        extract = lines[1];
        assertNotNull(extract);
        assertEquals(0, extract.size());
    }
}
