/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2009-2010 Operational Dynamics Consulting, Pty Ltd
 *
 * The code in this file, and the program it is a part of, is made available
 * to you by its authors as open source software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License version
 * 2 ("GPL") as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GPL for more details.
 *
 * You should have received a copy of the GPL along with this program. If not,
 * see http://www.gnu.org/licenses/. The authors of this program may be
 * contacted through http://research.operationaldynamics.com/projects/quill/.
 */
package parchment.quack;

/*
 * Forked from src/quill/docbook/DocBookConverter.java
 */

import java.io.IOException;
import java.io.OutputStream;

import nu.xom.Document;
import quill.textbase.AttributionSegment;
import quill.textbase.ChapterSegment;
import quill.textbase.Common;
import quill.textbase.DivisionSegment;
import quill.textbase.EndnoteSegment;
import quill.textbase.Extract;
import quill.textbase.HeadingSegment;
import quill.textbase.ImageSegment;
import quill.textbase.LeaderSegment;
import quill.textbase.ListitemSegment;
import quill.textbase.MarkerSpan;
import quill.textbase.Markup;
import quill.textbase.NormalSegment;
import quill.textbase.PoeticSegment;
import quill.textbase.Preformat;
import quill.textbase.PreformatSegment;
import quill.textbase.QuoteSegment;
import quill.textbase.ReferenceSegment;
import quill.textbase.Segment;
import quill.textbase.Span;
import quill.textbase.SpanVisitor;
import quill.textbase.Special;
import quill.textbase.SpecialSegment;

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
public class QuackConverter
{
    private Root component;

    private final StringBuilder buf;

    /**
     * The current internal block we are working through
     */
    private Segment segment;

    /**
     * Current output block we are building up.
     */

    private Block block;

    private Inline inline;

    private Markup previous = null;

    public QuackConverter() {
        buf = new StringBuilder();
        component = new RootElement();
    }

    /**
     * Append a Segment.
     */
    public void append(final Segment segment) {
        final Extract entire;
        final String value;

        this.segment = segment;

        if (segment instanceof ChapterSegment) {
            block = new ChapterElement();
            value = segment.getExtra();
            if ((value != null) && (value.length() != 0)) {
                block.add(new LabelAttribute(value));
            }
        } else if (segment instanceof DivisionSegment) {
            block = new DivisionElement();
            value = segment.getExtra();
            if ((value != null) && (value.length() != 0)) {
                block.add(new LabelAttribute(value));
            }
        } else if (segment instanceof HeadingSegment) {
            block = new HeadingElement();
            value = segment.getExtra();
            if ((value != null) && (value.length() != 0)) {
                block.add(new LabelAttribute(value));
            }
        } else if (segment instanceof PreformatSegment) {
            block = new CodeElement();
        } else if (segment instanceof QuoteSegment) {
            block = new QuoteElement();
        } else if (segment instanceof NormalSegment) {
            block = new TextElement();
        } else if (segment instanceof PoeticSegment) {
            block = new PoemElement();
        } else if (segment instanceof ListitemSegment) {
            block = new ListElement();
            value = segment.getExtra();
            if ((value != null) && (value.length() != 0)) {
                block.add(new LabelAttribute(value));
            }
        } else if (segment instanceof AttributionSegment) {
            block = new CreditElement();
        } else if (segment instanceof ImageSegment) {
            block = new ImageElement();
            value = segment.getExtra();
            block.add(new SourceAttribute(value));
        } else if (segment instanceof EndnoteSegment) {
            block = new EndnoteElement();
            value = segment.getExtra();
            block.add(new NameAttribute(value));
        } else if (segment instanceof ReferenceSegment) {
            block = new ReferenceElement();
            value = segment.getExtra();
            block.add(new NameAttribute(value));
        } else if (segment instanceof LeaderSegment) {
            block = new LeaderElement();
        } else if (segment instanceof SpecialSegment) {
            block = new SpecialElement();
            value = segment.getExtra();
            block.add(new TypeAttribute(value));
        } else {
            throw new IllegalStateException("Unhandled segment type " + segment);
        }
        inline = null;

        /*
         * Special case for empty blocks.
         */

        if (block instanceof Empty) {
            component.add(block);
            return;
        }

        /*
         * If there is no content then we jettison the block (this turns our
         * to be our temporary workaround for not being able to delete empty
         * Segments with the UI, but it's a good idea anyway). Otherwise it's
         * normal; add it to root element and set append its content
         */

        entire = segment.getEntire();
        if ((entire.getWidth() > 0) || (block instanceof Optional)) {
            component.add(block);
            append(entire);
        } else {
            // segment with no content; skip it!
        }
    }

    private void append(final Extract entire) {

        if (entire == null) {
            // FIXME ever?
            return;
        }

        // start(previous) // how?

        entire.visit(new SpanVisitor() {
            public boolean visit(Span span) {
                final Markup markup;
                final int len;
                int j;

                markup = span.getMarkup();
                if (markup != previous) {
                    finish();
                    start(markup);
                    previous = markup;
                }

                if (span instanceof MarkerSpan) {
                    process(span.getText());
                } else {
                    len = span.getWidth();
                    for (j = 0; j < len; j++) {
                        process(span.getChar(j));
                    }
                }

                return false;
            }
        });

        /*
         * Finally, we need to deal with the fact that TextChains (like the
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
         * We're either starting a new block or a new inline. Deal with the
         * Block cases first:
         */

        if (format == null) {
            return;
        }

        /*
         * Failing that, we cover off all the the Inline cases:
         */

        if (format == Common.FILENAME) {
            inline = new FilenameElement();
        } else if (format == Common.TYPE) {
            inline = new TypeElement();
        } else if (format == Common.FUNCTION) {
            inline = new FunctionElement();
        } else if (format == Common.ITALICS) {
            inline = new ItalicsElement();
        } else if (format == Common.BOLD) {
            inline = new BoldElement();
        } else if (format == Common.LITERAL) {
            inline = new LiteralElement();
        } else if (format == Common.PROJECT) {
            inline = new ProjectElement();
        } else if (format == Common.COMMAND) {
            inline = new CommandElement();
        } else if (format == Common.HIGHLIGHT) {
            inline = new HighlightElement();
        } else if (format == Common.TITLE) {
            inline = new TitleElement();
        } else if (format == Common.KEYBOARD) {
            inline = new KeyboardElement();
        } else if (format == Common.ACRONYM) {
            inline = new AcronymElement();
        } else if (format == Preformat.USERINPUT) {
            // boom?
        } else if (format == Special.NOTE) {
            inline = new NoteElement();
        } else if (format == Special.CITE) {
            inline = new CiteElement();
        } else {
            throw new IllegalStateException();
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
            inline = null;
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
            start(previous);
            if (segment instanceof NormalSegment) {
                block = new TextElement();
            } else if (segment instanceof PreformatSegment) {
                buf.append('\n');
                return;
            } else if (segment instanceof QuoteSegment) {
                block = new QuoteElement();
            } else if (segment instanceof PoeticSegment) {
                buf.append('\n');
                return;
            } else if (segment instanceof ListitemSegment) {
                block = new ListElement();
            } else if (segment instanceof AttributionSegment) {
                block = new CreditElement();
            } else {
                throw new IllegalStateException("\n" + "Newlines aren't allowed in " + block.toString());
            }
            component.add(block);
        } else {
            buf.appendCodePoint(ch);
        }
    }

    /**
     * Special case for handling the bodies of MarkerSpans -> empty Elements'
     * attributes.
     */
    private void process(String str) {
        buf.append(str);
    }

    /**
     * Create a <code>&lt;chapter&gt;</code> object based on what has been fed
     * to the converter, and write it to the given stream.
     */
    public void writeChapter(OutputStream out) throws IOException {
        final RootElement chapter;
        final Document doc;
        final QuackSerializer s;

        chapter = (RootElement) component;
        doc = new Document(chapter);

        s = new QuackSerializer(out);
        s.write(doc);
    }

    /**
     * This is legacy, but it shows the heritage of when we had more than one
     * kind of "component", namely <code>&lt;chapter&gt;</code>s and
     * <code>&lt;article&gt;</code>s.
     * 
     * @deprecated
     * @param out
     */
    public void writeArticle(OutputStream out) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
