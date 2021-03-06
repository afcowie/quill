/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2008-2010 Operational Dynamics Consulting, Pty Ltd
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
package quill.textbase;

import java.util.ArrayList;

import static quill.textbase.Extract.isWhitespace;

/**
 * A mutable buffer of unicode text which manages a binary tree of Spans in
 * order to maximize sharing of character array storage while giving us
 * efficient lookup by offset.
 * 
 * @author Andrew Cowie
 */
public class TextChain
{
    Node root;

    public TextChain() {
        root = Node.createNode();
    }

    TextChain(final String str) {
        final Span span;

        span = Span.createSpan(str, null);
        root = Node.createNode(span);
    }

    TextChain(Span initial) {
        root = Node.createNode(initial);
    }

    public TextChain(Extract all) {
        if (all instanceof Node) {
            root = (Node) all;
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * The length of this Text, in characters.
     */
    public int length() {
        return root.getWidth();
    }

    /**
     * This is ineffecient! Use for debugging purposes only.
     */
    public String toString() {
        final StringBuilder str;

        str = new StringBuilder();

        root.visit(new CharacterVisitor() {
            public boolean visit(int character, Markup markup) {
                str.appendCodePoint(character);
                return false;
            }
        });

        return str.toString();
    }

    /*
     * This is an inefficient implementation!
     */
    public void append(Span addition) {
        root = root.append(addition);
    }

    /*
     * TODO if we change to an EmptyNode singleton, then it should be returned
     * if empty.
     */
    Node getTree() {
        return root;
    }

    public void setTree(Extract entire) {
        root = (Node) entire;
    }

    /**
     * Get the Span at a given offset, for testing purposes.
     */
    Span spanAt(int offset) {
        return root.getSpanAt(offset);
    }

    /**
     * Insert the given Java String at the specified offset.
     */
    void insert(int offset, String what) {
        insert(offset, Span.createSpan(what, null));
    }

    /**
     * Insert the given tree of Spans at the specified offset.
     */
    public void insert(int offset, Extract extract) {
        final Node tree;

        if (offset < 0) {
            throw new IllegalArgumentException();
        }

        tree = (Node) extract;

        /*
         * Create the insertion point
         */
        root = root.insertTreeAt(offset, tree);
    }

    /**
     * Splice a Span into the TextChain.
     */
    public void insert(int offset, Span addition) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException();
        }

        root = root.insertSpanAt(offset, addition);
    }

    /*
     * FUTURE replace with use of TextChain's getTree() instead?
     */
    public Node extractAll() {
        return root;
    }

    /**
     * Delete a width wide segment starting at offset. People often have to
     * call extractRange() right before this so this will duplicate effort.
     * So, TODO create one which passes in a tree of the known bit to be
     * removed?
     */
    public void delete(final int offset, final int wide) {
        final Node preceeding, following;
        final int start, across;

        if (root == Node.EMPTY) {
            throw new IllegalStateException("Can't delete when already emtpy");
        }
        if (wide == 0) {
            throw new IllegalArgumentException("Can't delete nothing");
        }

        /*
         * Handle the special case of deleting everything.
         */

        if ((offset == 0) && (wide == root.getWidth())) {
            root = Node.EMPTY;
            return;
        }

        /*
         * Create subtrees for everything before and after the deletion range
         */

        preceeding = root.subset(0, offset);

        start = offset + wide;
        across = root.getWidth() - start;
        following = root.subset(start, across);

        /*
         * Now combine these subtrees to effect the deletion.
         */

        root = Node.createNode(preceeding, following);
    }

    /**
     * Add or remove a Markup format from a range of text.
     */
    /*
     * This is tested, but obviously calling code that used to live somewhere
     * else. Move the logic from FormatTextualChange here? And, can we clean
     * up the tree re-creation at all?
     */
    public void format(int offset, int wide, Markup format) {
        final Node preceeding, center, following;
        final int start, across;
        final Node replacement, node;

        /*
         * Create subtrees for everything before and after the changed range
         */

        preceeding = root.subset(0, offset);

        start = offset + wide;
        across = root.getWidth() - start;
        following = root.subset(start, across);

        /*
         * Accumulate the text in the given range, and then create a new Span
         * with the supplied new Markup.
         */

        center = root.subset(offset, wide);

        replacement = (Node) FormatTextualChange.toggleMarkup(center, format);

        /*
         * Now combine these subtrees to effect the format change.
         */

        node = Node.createNode(preceeding, following);
        root = node.insertTreeAt(offset, replacement);
    }

    public Markup getMarkupAt(int offset) {
        Span span;

        if (root == Node.EMPTY) {
            return null;
        }

        span = root.getSpanAt(offset);

        if (span == null) {
            return null;
        } else {
            return span.getMarkup();
        }
    }

    /**
     * Gets the Spans that represent the characters and formatting width wide
     * from start. The result is returned wrapped in a read-only Extract
     * object.
     * 
     * <p>
     * If width is negative, start will be decremented by that amount and the
     * range will be
     * 
     * <pre>
     * extractRange(start-width, |width|)
     * </pre>
     * 
     * This accounts for the common but subtle bug that if you have selected
     * moving backwards, selectionBound will be at a point where the range
     * ends - and greater than insertBound.
     */
    /*
     * Having exposed this so that external APIs can get an Extract to pass
     * when constructing a DeleteChange, we probably end up duplicating a lot
     * of work when actually calling delete() after this here.
     */
    public Extract extractRange(int start, int wide) {
        if (wide < 0) {
            throw new IllegalArgumentException();
        }
        if (wide == 0) {
            return null;
        }

        return root.subset(start, wide);
    }

    /**
     * Generate an array of Extracts, one for each \n separated paragraph.
     */
    public Extract[] extractParagraphs() {
        final ArrayList<Integer> paragraphs;
        final Node[] nodes;
        int num, i, offset, wide;

        if (root == Node.EMPTY) {
            return new Extract[] {};
        }

        /*
         * First work out how many lines are in this Text as it stands right
         * now. There is one paragraph if we're not empty.
         */

        paragraphs = new ArrayList<Integer>(8);
        paragraphs.add(0);

        root.visit(new CharacterVisitor() {
            private int offset = 0;

            public boolean visit(int character, Markup markup) {
                if (character == '\n') {
                    paragraphs.add(offset + 1);
                }
                offset++;
                return false;
            }
        });

        /*
         * Now form the array of Nodes which represent the ranges of each
         * paragraph. Note there is an assumption that there is not a newline
         * character at the end of the TextChain.
         */

        num = paragraphs.size();
        nodes = new Node[num];

        if (num == 1) {
            nodes[0] = root;
        } else {
            offset = 0;
            wide = paragraphs.get(1);

            for (i = 0; i < num - 1; i++) {
                offset = paragraphs.get(i);
                wide = paragraphs.get(i + 1) - 1 - paragraphs.get(i);

                nodes[i] = root.subset(offset, wide);
            }

            offset = 1 + offset + wide;
            wide = root.getWidth() - offset;
            nodes[i] = root.subset(offset, wide);
        }

        /*
         * Since Node is now Extract, we can just return our temporary array -
         * except that we don't want to have null entries, so we supplant
         * those if any exist (representing blank lines) with empty trees.
         */

        for (i = 0; i < num; i++) {
            if (nodes[i] == null) {
                nodes[i] = Node.createNode();
            }
        }

        return nodes;
    }

    // FIXME doc
    public int wordBoundaryBefore(final int offset) {
        final int result;

        if (root == Node.EMPTY) {
            return 0;
        }

        result = root.getWordBoundaryBefore(offset);

        if (result == -1) {
            return 0;
        } else {
            return result;
        }
    }

    // FIXME doc
    public int wordBoundaryAfter(final int offset) {
        final int result;

        if (root == Node.EMPTY) {
            return 0;
        }

        result = root.getWordBoundaryAfter(offset);

        if (result == -1) {
            return root.getWidth();
        } else {
            return result;
        }
    }

    /**
     * Given a cursor location in offset, work backwards to find a word
     * boundary, and then forwards to the next word boundary, and return the
     * word contained between those two points.
     */
    /*
     * After a huge amount of work, the current implementation in
     * EditorTextView doesn't call this, but needs the same boundary lookups,
     * and meanwhile this is heavily tested.
     */
    public String getWordAt(final int offset) {
        int begin, end;
        final StringBuilder str;
        final String result;

        if (offset == root.getWidth()) {
            return null;
        }

        str = new StringBuilder();

        /*
         * Seek backwards from the current offset to find a word boundary.
         */

        begin = this.wordBoundaryBefore(offset);
        end = this.wordBoundaryAfter(offset);

        /*
         * Iterate forward over the characters to get the word.
         */

        root.visit(new CharacterVisitor() {
            public boolean visit(int character, Markup markup) {
                str.appendCodePoint(character);
                return false;
            }
        }, begin, end - begin);

        /*
         * Pull out the word
         */

        result = str.toString();
        if (result.equals("")) {
            return null;
        } else {
            return result;
        }
    }

    /**
     * Iterate over a given range and build encountered characters into words.
     * 
     * @author Andrew Cowie
     */
    private static class WordBuildingCharacterVisitor implements CharacterVisitor
    {
        /*
         * The WordVisitor passed in to TextChain's visit() that will be
         * invoked here as words are accumulated.
         */
        private WordVisitor tourist;

        private StringBuilder str;

        private int begin;

        private int end;

        private boolean skip;

        /**
         * Marker to allow us to avoid calling on the last word if the tourist
         * returned true while visiting.
         */
        private boolean stop;

        /*
         * If a word has any range of non-spell checkable markup, then the
         * whole word is not to be checkable.
         */
        private static boolean skipSpellCheck(Markup markup) {
            if (markup == null) {
                return false; // normal
            }
            if (markup.isSpellCheckable()) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * @param from
         *            Because we visit words over a range, we need to bump our
         *            starting offset by whatever the start of the range was
         *            given in the call to TextChain's visit().
         */
        private WordBuildingCharacterVisitor(final WordVisitor visitor, final int from) {
            tourist = visitor;
            str = new StringBuilder();
            begin = from;
            end = from;
            skip = false;
            stop = false;
        }

        public boolean visit(final int character, final Markup markup) {
            if (!skip) {
                if (skipSpellCheck(markup)) {
                    skip = true;
                }
            }

            if (!isWhitespace(character)) {
                str.appendCodePoint(character);
                end++;
                return false;
            }

            if (handleWord()) {
                stop = true;
                return true;
            }

            str.setLength(0);
            end++;
            begin = end;
            skip = false;

            return false;
        }

        /**
         * Action the accumulated word.
         */
        /*
         * Seperate code so that it can be invoked on the last word by the
         * calling visit() method (the CharacterVisitor doesn't know when it's
         * done, and so can't call this one last time).
         */
        private boolean handleWord() {
            final String word;

            if (stop) {
                return true;
            }

            word = str.toString();
            if (tourist.visit(word, skip, begin, end)) {
                stop = true;
                return true;
            }

            return false;
        }
    }

    /**
     * Visit through the given range and, as complete words are accumulated,
     * invoke tourist's visit() method. Used in spell checking!
     */
    public void visit(final WordVisitor tourist, final int begin, final int end) {
        final WordBuildingCharacterVisitor builder;

        if (root == Node.EMPTY) {
            return;
        }

        builder = new WordBuildingCharacterVisitor(tourist, begin);

        root.visit(builder, begin, end - begin);
        builder.handleWord();
    }
}
