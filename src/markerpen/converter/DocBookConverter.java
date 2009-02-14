/*
 * DocBookConverter.java
 *
 * Copyright (c) 2008 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package markerpen.converter;

/*
 * A big reason to have this in its own package is so that we are using only
 * the public interface of our docbook module. Secondarily is that if other
 * output formats grow up, this will be the place their converters can live.
 */

import java.io.File;
import java.io.IOException;

import markerpen.docbook.Block;
import markerpen.docbook.Bold;
import markerpen.docbook.BookDocument;
import markerpen.docbook.Chapter;
import markerpen.docbook.Document;
import markerpen.docbook.Filename;
import markerpen.docbook.Function;
import markerpen.docbook.Inline;
import markerpen.docbook.Italics;
import markerpen.docbook.Paragraph;
import markerpen.docbook.Section;
import markerpen.docbook.Type;
import markerpen.textbase.CharacterSpan;
import markerpen.textbase.Common;
import markerpen.textbase.EfficientNoNodeFactory;
import markerpen.textbase.Extract;
import markerpen.textbase.Markup;
import markerpen.textbase.Span;
import markerpen.textbase.StringSpan;
import markerpen.textbase.TextStack;
import nu.xom.Builder;
import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.gnome.gtk.TextBuffer;
import org.gnome.gtk.TextIter;

import static org.gnome.gtk.TextBuffer.OBJECT_REPLACEMENT_CHARACTER;

/**
 * Build a DocBook XOM tree equivalent to the data in our textbase, ready for
 * subsequent serialization (and thence saving to disk).
 * 
 * 
 * @author Andrew Cowie
 */
/*
 * Build up Elements character by character. While somewhat plodding, this
 * allows us to create new Paragraphs etc as newlines are encountered.
 */
public class DocBookConverter
{
    private final Document book;

    private final StringBuilder buf;

    /**
     * Current Section we are appending Paragraphs to
     */
    private Section section;

    /**
     * Current block we are building up.
     */
    private Block block;

    private Inline inline;

    public DocBookConverter() {
        book = new BookDocument();
        buf = new StringBuilder();
    }

    /**
     * Append a Text, which represents a Chapter (probably).
     * 
     * FUTURE as of this writing we have no modelling of Sections in the UI,
     * so we'll assume there is one (and only one) for now.
     * 
     */
    public void append(TextStack text) {
        final Chapter chapter;
        final Extract entire;
        final int num;
        int i, j, len;
        Span span;
        String str;
        char ch;
        Markup previous, markup;

        chapter = new Chapter();
        book.add(chapter);

        section = new Section();
        chapter.add(section);

        start(new Paragraph());

        entire = text.extractAll();
        num = entire.size();
        previous = entire.get(0).getMarkup();

        for (i = 0; i < num; i++) {
            span = entire.get(i);

            markup = span.getMarkup();

            if (markup != previous) {
                flush();
                change(markup);
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

            previous = markup;
        }
        flush();
    }

    public Document result() {
        return book;
    }

    /**
     * Change to a new element. This is a somewhat complicated expression, as
     * it counts for the case of returning from Inline to Block as well as
     * nesting Inlines into Blocks.
     */
    private void change(Markup format) {
        if (format == null) {
            return;
        }
        if (format instanceof Common) {
            if (format == Common.FILENAME) {
                start(new Filename());
            } else if (format == Common.TYPE) {
                start(new Type());
            } else if (format == Common.FUNCTION) {
                start(new Function());
            } else if (format == Common.ITALICS) {
                start(new Italics());
            } else if (format == Common.BOLD) {
                start(new Bold());
            } else {
                // TODO boom?
            }
        }
    }

    /*
     * Assumes finish() has already been called.
     */
    private void start(Block block) {
        this.block = block;
        section.add(block);
    }

    /*
     * Assumes finish() has already been called.
     */
    private void start(Inline inline) {
        this.inline = inline;
        block.add(inline);
    }

    private void flush() {
        if (inline != null) {
            inline.add(buf.toString());
            inline = null;
        } else {
            block.add(buf.toString());
        }
        buf.setLength(0);
    }

    private void process(char ch) {
        if (ch == '\n') {
            flush();
            start(new Paragraph()); // won't work with different Block types
        } else {
            buf.append(ch);
        }
    }

    public static Document buildTree(TextBuffer buffer) {
        final Document book;
        final Chapter chapter;
        final Section section;
        final StringBuilder str;
        TextIter pointer;
        char ch;
        Paragraph para;

        book = new BookDocument();

        chapter = new Chapter();
        book.add(chapter);

        section = new Section();
        chapter.add(section);

        str = new StringBuilder();

        pointer = buffer.getIterStart();

        while (true) {
            /*
             * Close markup for formats that are now ending
             */
            // TODO
            if (pointer.endsLine()) {
                para = new Paragraph(str.toString());
                section.add(para);
                str.setLength(0);
            }

            if (pointer.isEnd()) {
                break;
            }

            /*
             * Open markup that represents formats that are now beginning.
             */
            // TODO
            /*
             * Finally, add the TextBuffer's content at this position, and
             * move to the next character... unless it's something special
             */

            ch = pointer.getChar();

            // TODO images
            if (ch == OBJECT_REPLACEMENT_CHARACTER) {
                continue;
            } else {
                str.append(ch);
            }

            pointer.forwardChar();
        }

        return book;
    }

    public static TextStack parseTree(File source) throws ValidityException, ParsingException,
            IOException {
        final Builder parser;
        final EfficientNoNodeFactory factory;

        factory = new EfficientNoNodeFactory();

        parser = new Builder(factory);
        parser.build(source);

        return factory.createText();
    }
}
