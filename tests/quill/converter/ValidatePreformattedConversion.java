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
package quill.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import junit.framework.TestCase;
import quill.docbook.DocBookConverter;
import quill.textbase.Change;
import quill.textbase.Common;
import quill.textbase.DataLayer;
import quill.textbase.InsertTextualChange;
import quill.textbase.NormalSegment;
import quill.textbase.Preformat;
import quill.textbase.Segment;
import quill.textbase.Span;
import quill.textbase.TextChain;

import static quill.textbase.Span.createSpan;

/**
 * BROKEN
 * 
 * This tested matters when we doing programlisting blocks as a Markup. The
 * infrastructure is good, though, so keep this around until we can use it
 * properly.
 * 
 * @author Andrew Cowie
 */
public class ValidatePreformattedConversion extends TestCase
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
        final DataLayer data;
        final TextChain chain;
        final Span[] spans;
        int offset;
        Change change;
        final Segment segment;
        final DocBookConverter converter;
        final ByteArrayOutputStream out;
        final String blob;

        /*
         * Build up a more complicated example
         */

        spans = new Span[] {
                createSpan(
                        "Consider the following simple and yet profound expression of quality program code:\n",
                        null),
                createSpan("public class Hello {\u2028", Preformat.NORMAL),
                createSpan("    public static void main(String[] args) {\u2028", Preformat.NORMAL),
                createSpan("        System.out.println(\"Hello World\");\u2028", Preformat.NORMAL),
                createSpan("    }\u2028", Preformat.NORMAL),
                createSpan("}", Preformat.NORMAL),
                createSpan('\n', null),
                createSpan("There really isn't anything like saying ", null),
                createSpan("Hello World", Common.ITALICS),
                createSpan(" to a nice friendly programmer.", null),
        };

        data = new DataLayer();
        chain = new TextChain();
        offset = 0;

        for (Span span : spans) {
            change = new InsertTextualChange(chain, offset, span);
            data.apply(change);
            offset += span.getWidth();
        }

        /*
         * Now run conversion process.
         */

        segment = new NormalSegment();
        segment.setText(chain);

        converter = new DocBookConverter();
        converter.append(segment);

        out = new ByteArrayOutputStream();
        converter.writeChapter(out);

        blob = combine(new String[] {
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<chapter version=\"5.0\" xmlns=\"http://docbook.org/ns/docbook\">",
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
                "</chapter>"
        });

        /*
         * WARNING. If the word wrap width of our save output changes, then
         * the expected result blob will need to be reformatted to for the
         * test to pass. That's acceptable.
         */
        assertEquals(blob, out.toString());
    }
}
