/*
 * ValidatePreformattedConversion.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package markerpen.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;
import markerpen.docbook.Document;
import markerpen.textbase.Change;
import markerpen.textbase.CharacterSpan;
import markerpen.textbase.Common;
import markerpen.textbase.InsertChange;
import markerpen.textbase.ParagraphSegment;
import markerpen.textbase.Preformat;
import markerpen.textbase.Segment;
import markerpen.textbase.Span;
import markerpen.textbase.StringSpan;
import markerpen.textbase.TextStack;

/**
 * BROKEN
 * 
 * This tested matters when we doing programlisting blocks as a Markup. The
 * infrastructure is good, though, so keep this around until we can use it
 * properly.
 * 
 * @author Andrew Cowie
 */
public class ValidatepPreformattedConversion extends TestCase
{
    private static String combine(String[] elements) {
        StringBuilder buf;

        buf = new StringBuilder(128);

        for (String element : elements) {
            buf.append(element);
            buf.append('\n');
        }

        return buf.toString();
    }

    public final void testWritePreformatting() throws IOException {
        final TextStack stack;
        final Span[] spans;
        int offset;
        Change change;
        final Segment segment;
        final DocBookConverter converter;
        final Document book;
        final ByteArrayOutputStream out;
        final String blob;

        /*
         * Build up a more complicated example
         */

        spans = new Span[] {
                new StringSpan(
                        "Consider the following simple and yet profound expression of quality program code:\n",
                        null),
                new StringSpan("public class Hello {\u2028", Preformat.NORMAL),
                new StringSpan("    public static void main(String[] args) {\u2028", Preformat.NORMAL),
                new StringSpan("        System.out.println(\"Hello World\");\u2028", Preformat.NORMAL),
                new StringSpan("    }\u2028", Preformat.NORMAL),
                new StringSpan("}", Preformat.NORMAL),
                new CharacterSpan('\n', null),
                new StringSpan("There really isn't anything like saying ", null),
                new StringSpan("Hello World", Common.ITALICS),
                new StringSpan(" to a nice friendly programmer.", null),
        };

        stack = new TextStack();
        offset = 0;

        for (Span span : spans) {
            change = new InsertChange(offset, span);
            stack.apply(change);
            offset += span.getWidth();
        }

        /*
         * Now run conversion process.
         */

        segment = new ParagraphSegment();
        segment.setText(stack);

        converter = new DocBookConverter();
        converter.append(segment);
        book = converter.result();

        assertNotNull(book);

        out = new ByteArrayOutputStream();
        book.toXML(out);

        blob = combine(new String[] {
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<book version=\"5.0\" xmlns=\"http://docbook.org/ns/docbook\">",
                "<chapter>",
                "<section>",
                "<para>",
                "Consider the following simple and yet profound",
                "expression of quality program code:",
                "</para>",
                "<programlisting xml:space=\"preserve\">",
                "public class Hello {",
                "    public static void main(String[] args) {",
                "        System.out.println(\"Hello World\");",
                "    }",
                "}",
                "</programlisting>",
                "<para>",
                "There really isn't anything like saying ",
                "<emphasis>Hello World</emphasis> to a nice",
                "friendly programmer.",
                "</para>",
                "</section>",
                "</chapter>",
                "</book>"
        });

        /*
         * WARNING. If the word wrap width of our save output changes, then
         * the expected result blob will need to be reformatted to for the
         * test to pass. That's acceptable.
         */
        assertEquals(blob, out.toString());
    }
}
