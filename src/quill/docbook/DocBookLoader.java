/*
 * DocBookLoader.java
 *
 * Copyright (c) 2008-2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 * 
 * Most of this logic came from a class called EfficientNoNodeFactory. 
 */
package quill.docbook;

import java.util.ArrayList;

import nu.xom.Document;
import quill.textbase.Common;
import quill.textbase.ComponentSegment;
import quill.textbase.HeadingSegment;
import quill.textbase.Markup;
import quill.textbase.NormalSegment;
import quill.textbase.PreformatSegment;
import quill.textbase.QuoteSegment;
import quill.textbase.Segment;
import quill.textbase.Series;
import quill.textbase.Span;
import quill.textbase.TextChain;

/**
 * Take a XOM tree (built using DocBookNodeFactory and so having our
 * DocBookElements) and convert it into our internal in-memory textchain
 * representation.
 * 
 * @author Andrew Cowie
 */
public class DocBookLoader
{
    private final ArrayList<Segment> list;

    /**
     * The (single) formatting applicable at this insertion point.
     */
    private Markup markup;

    /**
     * Did we just enter a block level element?
     */
    private boolean start;

    /**
     * The current block level element wrapper
     */
    private Segment segment;

    /**
     * The current text body we are building up.
     */
    private TextChain chain;

    /**
     * Are we in a block where line endings and other whitespace is preserved?
     */
    private boolean preserve;

    /**
     * Did we trim a whitespace character (newline, specifically) from the end
     * of the previous Text?
     */
    private boolean space;

    public DocBookLoader() {
        list = new ArrayList<Segment>(5);
        chain = null;
    }

    private void setSegment(Segment segment) {
        chain = new TextChain();
        segment.setText(chain);
        this.segment = segment;
        list.add(segment);
    }

    /*
     * TODO This assumes our documents have only one chapter in them. That's
     * probably not correct.
     */
    /*
     * This kinda assumes we only load one Document; if that's not the case,
     * and we don't force instantiating a new DocBookLoader, then clear the
     * processor state here.
     */
    public Series process(Document doc) {
        final Component chapter;
        final Division[] sections;
        int i, j;
        Block[] blocks;
        final Segment[] result;

        chapter = (Component) doc.getRootElement();
        processComponent(chapter);

        /*
         * Handle the "anonymous" Blocks before the first Section, if any.
         */

        blocks = chapter.getBlocks();

        for (j = 0; j < blocks.length; j++) {
            processBlock(blocks[j]);
        }

        /*
         * Now iterate over the Sections and then handle each Section's
         * Blocks.
         */

        sections = chapter.getDivisions();

        for (i = 0; i < sections.length; i++) {
            processDivision(sections[i]);

            blocks = sections[i].getBlocks();

            for (j = 0; j < blocks.length; j++) {
                processBlock(blocks[j]);
            }
        }

        /*
         * Finally, transpose the resultant collection of Segments into a
         * Series, and return it.
         */

        result = new Segment[list.size()];
        list.toArray(result);

        return new Series(result);
    }

    private void processComponent(Component component) {
        if (component instanceof Chapter) {
            markup = null;
            start = true;
            preserve = false;
            setSegment(new ComponentSegment());
        }
        // TODO Article?
    }

    private void processDivision(Division division) {
        if (division instanceof Section) {
            markup = null;
            start = true;
            preserve = false;
            setSegment(new HeadingSegment());
        }
    }

    private void processBlock(Block block) {
        final Block[] blocks;
        int i;

        if (block instanceof Paragraph) {
            markup = null;
            start = true;
            preserve = false;
            if (segment instanceof NormalSegment) {
                chain.append(Span.createSpan('\n', null));
            } else {
                setSegment(new NormalSegment());
            }
            processBody(block);
        } else if (block instanceof ProgramListing) {
            markup = null;
            start = true;
            preserve = true;
            if (!(segment instanceof PreformatSegment)) {
                setSegment(new PreformatSegment());
            }
            processBody(block);
        } else if (block instanceof Blockquote) {
            markup = null;
            start = true;
            preserve = false;
            setSegment(new QuoteSegment());

            blocks = ((Blockquote) block).getBlocks(); // yuk
            processBody(blocks[0]); // hm
            for (i = 1; i < blocks.length; i++) {
                chain.append(Span.createSpan('\n', null));

                start = true;
                processBody(blocks[i]);
            }
        } else if (block instanceof Title) {
            markup = null;
            start = true;
            preserve = false;
            if (segment instanceof HeadingSegment) {
                // kludge
                chain = new TextChain();
                segment.setText(chain);
            }
            processBody(block);
        } else {
            throw new IllegalStateException("\n" + "What kind of Block is " + block);
        }
    }

    private void processBody(Block block) {
        Inline[] spans;
        int i;

        spans = block.getSpans();
        for (i = 0; i < spans.length; i++) {
            handleSpan(spans[i]);
        }
    }

    private void handleSpan(Inline span) {
        if (span instanceof Function) {
            markup = Common.FUNCTION;
        } else if (span instanceof Filename) {
            markup = Common.FILENAME;
        } else if (span instanceof Type) {
            markup = Common.TYPE;
        } else if (span instanceof Literal) {
            markup = Common.LITERAL;
        } else if (span instanceof Command) {
            markup = Common.COMMAND;
        } else if (span instanceof Application) {
            markup = Common.APPLICATION;
            // } else if (span instanceof UserInput) { // TODO
            // markup = Preformat.USERINPUT;
        } else if (span instanceof Emphasis) {
            final Emphasis element;

            /*
             * Work out if this is italics or bold. This is not the cleanest
             * code. Should isBold() move to the Inline interface?
             */

            element = (Emphasis) span;

            if (element.isBold()) {
                markup = Common.BOLD;
            } else {
                markup = Common.ITALICS;
            }
        } else {
            /*
             * No need to warn, really. The structure tags don't count. But if
             * we're losing semantic data, this is where its happening.
             */
            markup = null;
        }

        if (markup != null) {
            start = false;
        }

        processText(span.getText());
    }

    /*
     * This will be the contiguous text body of the element until either a) an
     * nested (inline) element starts, or b) the end of the element is
     * reached. So we trim off the leading pretty-print whitespace then add a
     * single StringSpan with this content.
     */
    private void processText(String text) {
        final String trim, str;
        int len;
        char ch;

        len = text.length();

        /*
         * These two cases are common for structure tags
         */

        if (len == 0) {
            space = false;
            return; // empty
        }

        if (len == 1) {
            if (text.charAt(0) == '\n') {
                space = false;
                return; // empty
            }
        }

        /*
         * Do we think we're starting a block? If so, trim off the leading
         * newline. If we're starting an inline and we swollowed a trailing
         * space from the previous run of text, pad the inline with one space.
         */

        if (start) {
            start = false;
            space = false;
            text = text.substring(1);
            len--;
        } else if (space) {
            chain.append(Span.createSpan(' ', null));
        }

        /*
         * Trim the trailing newline (if there is one) as it could be the
         * break before a close-element tag. We replace it with a space and
         * prepend it if we find it is just a linebreak separator between a
         * Text and an Inline when making the next Text node.
         */

        ch = text.charAt(len - 1);
        if (ch == '\n') {
            trim = text.substring(0, len - 1);
            len--;
            space = true;
        } else {
            trim = text;
            space = false;
        }

        /*
         * If not preformatted text, turn any interior newlines into spaces,
         * then add.
         */

        if (preserve) {
            str = trim;
        } else {
            str = trim.replace('\n', ' ');
        }

        chain.append(Span.createSpan(str, markup));

        /*
         * And, having processed the inline, reset to normal.
         */

        markup = null;
    }
}