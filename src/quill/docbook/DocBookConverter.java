/*
 * DocBookConverter.java
 *
 * Copyright (c) 2008-2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.docbook;

import java.io.IOException;
import java.io.OutputStream;

import quill.textbase.Common;
import quill.textbase.ComponentSegment;
import quill.textbase.Extract;
import quill.textbase.HeadingSegment;
import quill.textbase.Markup;
import quill.textbase.NormalSegment;
import quill.textbase.Preformat;
import quill.textbase.PreformatSegment;
import quill.textbase.QuoteSegment;
import quill.textbase.Segment;
import quill.textbase.Span;
import quill.textbase.TextChain;

/**
 * Build a DocBook XOM tree equivalent to the data in our textbase, ready for
 * subsequent serialization (and thence saving to disk).
 * 
 * @author Andrew Cowie
 */
/*
 * Build up Elements character by character. While somewhat plodding, this
 * allows us to create new Paragraphs etc as newlines are encountered.
 */
public class DocBookConverter
{
    private Component component;

    private final StringBuilder buf;

    /**
     * Current Section we are appending Paragraphs to
     */
    private Division section;

    /**
     * The current internal block we are working through
     */
    private Segment segment;

    private Structure parent;

    /**
     * Current output block we are building up.
     */

    private Block block;

    private Inline inline;

    public DocBookConverter() {
        buf = new StringBuilder();
    }

    /**
     * Append a Segment.
     */
    public void append(final Segment segment) {
        final TextChain text;
        final Blockquote quote;

        this.segment = segment;

        if (segment instanceof ComponentSegment) {
            component = new Chapter();
            section = null;
            parent = component;
            block = new Title();
        } else if (segment instanceof HeadingSegment) {
            section = new Section();
            component.add(section);
            parent = section;
            block = new Title();
        } else if (segment instanceof PreformatSegment) {
            block = new ProgramListing();
        } else if (segment instanceof QuoteSegment) {
            quote = new Blockquote();
            parent.add(quote);
            parent = quote;
            block = new Paragraph();
        } else if (segment instanceof NormalSegment) {
            block = new Paragraph();
        }

        text = segment.getText();
        if ((text == null) || (text.length() == 0)) {
            return;
        }

        parent.add(block);
        append(text);

        if (parent instanceof Blockquote) {
            if (section == null) {
                parent = component;
            } else {
                parent = section;
            }
        }
    }

    private void append(final TextChain chain) {
        final Extract entire;
        final int num;
        int i, j, len;
        Span span;
        Markup previous, markup;

        if (chain == null) {
            return;
        }

        entire = chain.extractAll();
        if (entire == null) {
            return;
        }

        num = entire.size();
        previous = entire.get(0).getMarkup();
        start(previous);

        for (i = 0; i < num; i++) {
            span = entire.get(i);

            markup = span.getMarkup();
            if (markup != previous) {
                finish();
                start(markup);
                previous = markup;
            }

            len = span.getWidth();
            for (j = 0; j < len; j++) {
                process(span.getChar(j));
            }
        }

        /*
         * Finally, we need to deal with the fact that TextStacks (like the
         * TextBuffers they back) do not end with a paragraph separator, so we
         * need to act to close out the last block.
         */
        finish();
    }

    /**
     * Start a new element. This is a somewhat complicated expression, as it
     * counts for the case of returning from Inline to Block as well as
     * nesting Inlines into Blocks.
     */
    private void start(Markup format) {
        /*
         * Are we returning from an inline to block level? If so, we're
         * already nested and can just reset the state and escape.
         */
        if (inline != null) {
            inline = null;
            return;
        }

        /*
         * Otherwise, we're either starting a new block or a new inline. Deal
         * with the Block cases first:
         */

        if (format == null) {
            return;
        }

        /*
         * Failing that, we cover off all the the Inline cases:
         */

        if (inline == null) {
            if (format == Common.FILENAME) {
                inline = new Filename();
            } else if (format == Common.TYPE) {
                inline = new Type();
            } else if (format == Common.FUNCTION) {
                inline = new Function();
            } else if (format == Common.ITALICS) {
                inline = new Emphasis();
            } else if (format == Common.BOLD) {
                final Emphasis element;

                element = new Emphasis();
                element.setBold();

                inline = element;
            } else if (format == Common.LITERAL) {
                inline = new Literal();
            } else if (format == Common.APPLICATION) {
                inline = new Application();
            } else if (format == Common.COMMAND) {
                inline = new Command();
            } else if (format == Preformat.USERINPUT) {
                // boom?
            } else {
                // boom!
            }
        }
    }

    /**
     * Add accumulated text to the pending element. Reset the accumulator.
     */
    private void finish() {
        if (buf.length() == 0) {
            /*
             * At the moment we have no empty tags, and so nothing that would
             * cause us to flush something with no content. When we do, we'll
             * handle it here.
             */
            return;
        }

        if (inline != null) {
            inline.add(buf.toString());
            block.add(inline);
        } else {
            block.add(buf.toString());
        }
        buf.setLength(0);
    }

    /**
     * Accumulate a character. If the character is '\n' and we're not in a
     * PreformatSegment then we create a new Block. Setting segment to null is
     * how we signal the end.
     */
    private void process(int ch) {
        if (ch == '\n') {
            finish();
            if (inline != null) {
                inline = null;
            }
            if (segment instanceof PreformatSegment) {
                buf.append('\n');
                return;
            }
            if (block instanceof Title) {
                throw new IllegalStateException("\n" + "Newlines aren't allowed in titles");
            }
            block = new Paragraph();
            parent.add(block);
        } else {
            buf.appendCodePoint(ch);
        }
    }

    /**
     * Create a <code>&lt;chapter&gt;</code> object based on what has been fed
     * to the converter, and write it to the given stream.
     */
    public void writeChapter(OutputStream out) throws IOException {
        final Chapter chapter;

        chapter = (Chapter) component;
        chapter.toXML(out);
    }

    public void writeArticle(OutputStream out) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}