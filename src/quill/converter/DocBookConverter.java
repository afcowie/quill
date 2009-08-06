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
package quill.converter;

/*
 * A big reason to have this in its own package is so that we are using only
 * the public interface of our docbook module. Secondarily is that if other
 * output formats grow up, this will be the place their converters can live.
 */

import quill.docbook.Application;
import quill.docbook.Article;
import quill.docbook.Block;
import quill.docbook.Blockquote;
import quill.docbook.Book;
import quill.docbook.Chapter;
import quill.docbook.Command;
import quill.docbook.Component;
import quill.docbook.Division;
import quill.docbook.Emphasis;
import quill.docbook.Filename;
import quill.docbook.Function;
import quill.docbook.Inline;
import quill.docbook.Literal;
import quill.docbook.Paragraph;
import quill.docbook.ProgramListing;
import quill.docbook.Section;
import quill.docbook.Structure;
import quill.docbook.Title;
import quill.docbook.Type;
import quill.textbase.CharacterSpan;
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
import quill.textbase.StringSpan;
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
    private Component chapter;

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
            chapter = new Chapter();
            section = null;
            parent = chapter;
            block = new Title();
        } else if (segment instanceof HeadingSegment) {
            section = new Section();
            chapter.add(section);
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
                parent = chapter;
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
        String str;
        char ch;
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

            if (span instanceof CharacterSpan) {
                ch = span.getChar();
                process(ch);
            } else if (span instanceof StringSpan) {
                str = span.getText();
                len = str.length();
                for (j = 0; j < len; j++) {
                    process(str.charAt(j));
                }
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
     * Create a <code>&lt;book&gt;</code> object based on what has been fed to
     * the converter.
     */
    public Book createBook() {
        final Book book;

        book = new Book();
        book.add(chapter);

        return book;
    }

    public Article createArticle() {
        // return chapter?
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
