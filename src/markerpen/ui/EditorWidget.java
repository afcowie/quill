/*
 * EditorWidget.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package markerpen.ui;

import java.io.IOException;

import markerpen.converter.DocBookConverter;
import markerpen.docbook.Document;
import markerpen.textbase.Change;
import markerpen.textbase.CharacterSpan;
import markerpen.textbase.Common;
import markerpen.textbase.DeleteChange;
import markerpen.textbase.Extract;
import markerpen.textbase.FormatChange;
import markerpen.textbase.InsertChange;
import markerpen.textbase.Markup;
import markerpen.textbase.Span;
import markerpen.textbase.StringSpan;
import markerpen.textbase.TextStack;
import markerpen.textbase.TextualChange;

import org.gnome.gdk.EventKey;
import org.gnome.gdk.EventOwnerChange;
import org.gnome.gdk.Keyval;
import org.gnome.gdk.ModifierType;
import org.gnome.gdk.Rectangle;
import org.gnome.gtk.Allocation;
import org.gnome.gtk.Clipboard;
import org.gnome.gtk.TextBuffer;
import org.gnome.gtk.TextIter;
import org.gnome.gtk.TextMark;
import org.gnome.gtk.TextTag;
import org.gnome.gtk.TextView;
import org.gnome.gtk.Widget;
import org.gnome.gtk.WrapMode;
import org.gnome.pango.FontDescription;

import static markerpen.ui.Format.tagForMarkup;
import static markerpen.ui.Format.tagsForMarkup;

class EditorWidget extends TextView
{
    private final TextView view;

    private TextBuffer buffer;

    private TextMark selectionBound, insertBound;

    private TextStack stack;

    /**
     * Cache of the offset into the TextBuffer of the insertBound TextMark.
     */
    private int insertOffset;

    private Markup insertMarkup;

    private Clipboard clipboard;

    /**
     * Do we [think] we're the owner of the clipboard?
     */
    private boolean owner;

    EditorWidget() {
        super();
        view = this;

        setupTextView();
        setupInternalStack();

        hookupKeybindings();
        hookupFormatManagement();
        hookupExternalClipboard();
    }

    private void setupTextView() {
        final FontDescription desc;

        desc = new FontDescription("DejaVu Serif, Book 11");
        buffer = new TextBuffer();

        selectionBound = buffer.getSelectionBound();
        insertBound = buffer.getInsert();

        view.setBuffer(buffer);
        view.modifyFont(desc);

        view.setWrapMode(WrapMode.WORD);

        view.setPaddingBelowParagraph(18);
    }

    private void setupInternalStack() {
        stack = new TextStack();
        stash = null;
    }

    private void hookupKeybindings() {
        view.connect(new Widget.KeyPressEvent() {
            public boolean onKeyPressEvent(Widget source, EventKey event) {
                final Keyval key;
                final ModifierType mod;
                final char ch;

                key = event.getKeyval();

                /*
                 * Let default keybindings handle cursor movement keys and for
                 * a few other special keys we don't need to handle.
                 */

                if ((key == Keyval.Up) || (key == Keyval.Down) || (key == Keyval.Left)
                        || (key == Keyval.Right) || (key == Keyval.Home) || (key == Keyval.End)
                        || (key == Keyval.PageUp) || (key == Keyval.PageDown)) {
                    return false;
                }

                if ((key == Keyval.Escape) || (key == Keyval.Insert)) {
                    // deliberate no-op
                    return true;
                }

                /*
                 * Other special keys that we DO handle. The newline case is
                 * interesting. We let a \n fly but clear any current
                 * formatting first.
                 */
                if (key == Keyval.Return) {
                    insertMarkup = null;
                    insert('\n');
                    return true;
                } else if (key == Keyval.Delete) {
                    deleteAt();
                    return true;
                } else if (key == Keyval.BackSpace) {
                    deleteBack();
                    return true;
                }

                /*
                 * Ignore initial press of modifier keys (for now)
                 */

                if ((key == Keyval.ShiftLeft) || (key == Keyval.ShiftRight) || (key == Keyval.AltLeft)
                        || (key == Keyval.AltRight) || (key == Keyval.ControlLeft)
                        || (key == Keyval.ControlRight) || (key == Keyval.SuperLeft)
                        || (key == Keyval.SuperRight)) {
                    // deliberate no-op
                    return true;
                }

                /*
                 * Tab is a strange one. At first glance it is tempting to set
                 * the TextView to not accept them and to have Tab change
                 * focus, but there is the case of program code in a
                 * preformatted block which might need indent support.
                 */

                if (key == Keyval.Tab) {
                    insert('\t');
                    return true;
                }

                /*
                 * Now on to processing normal keystrokes.
                 */

                mod = event.getState();

                if ((mod == ModifierType.NONE) || (mod == ModifierType.SHIFT_MASK)) {
                    ch = key.toUnicode();

                    if (ch == 0) {
                        /*
                         * Don't know what this is. If it's a modifier, we
                         * ought to have skipped it explicitly above. If it
                         * results in a character being inserted into the
                         * TextBuffer things will break. So, needs fixing!
                         */
                        throw new UnsupportedOperationException();
                    }

                    insert(ch);
                    return true;
                }
                if (mod == ModifierType.CONTROL_MASK) {
                    if (key == Keyval.a) {
                        // select all; pass through
                        return false;
                    } else if (key == Keyval.b) {
                        toggleMarkup(Common.BOLD);
                        return true;
                    } else if (key == Keyval.c) {
                        copyText();
                        return true;
                    } else if (key == Keyval.g) {
                        insertImage();
                        return true;
                    } else if (key == Keyval.i) {
                        toggleMarkup(Common.ITALICS);
                        return true;
                    } else if (key == Keyval.s) {
                        exportContents();
                        return true;
                    } else if (key == Keyval.v) {
                        pasteText();
                        return true;
                    } else if (key == Keyval.x) {
                        cutText();
                        return true;
                    } else if (key == Keyval.y) {
                        redo();
                        return true;
                    } else if (key == Keyval.z) {
                        undo();
                        return true;
                    } else {
                        /*
                         * No keybinding
                         */
                        return true;
                    }
                } else if (mod.contains(ModifierType.CONTROL_MASK)
                        && mod.contains(ModifierType.SHIFT_MASK)) {
                    if (key == Keyval.Space) {
                        clearFormat();
                        return true;
                    } else if (key == Keyval.F) {
                        toggleMarkup(Common.FILENAME);
                        return true;
                    } else if (key == Keyval.M) {
                        // function or _m_ethod
                        toggleMarkup(Common.FUNCTION);
                        return true;
                    } else if (key == Keyval.T) {
                        toggleMarkup(Common.TYPE);
                        return true;
                    } else {
                        /*
                         * No keybinding
                         */
                        return true;
                    }
                }

                /*
                 * We didn't handle it, and are assuming we're capable of
                 * handing all keyboard input. Boom :(
                 */

                throw new IllegalStateException("\n" + "Unhandled " + key + " with " + mod);
            }
        });
    }

    /**
     * Create a Span and insert into our internal representation. Then apply()
     * to send the character to the TextBuffer, updating the TextView to
     * reflect the user's input.
     */
    private void insert(char ch) {
        final Span span;
        final Extract removed;
        final Change change;
        final TextIter other;
        final int otherOffset, offset, width;

        span = new CharacterSpan(ch, insertMarkup);

        if (buffer.getHasSelection()) {
            other = buffer.getIter(selectionBound);
            otherOffset = other.getOffset();

            offset = normalizeOffset(insertOffset, otherOffset);
            width = normalizeWidth(insertOffset, otherOffset);

            removed = stack.extractRange(offset, width);
            change = new TextualChange(offset, removed, span);
        } else {
            change = new InsertChange(insertOffset, span);
        }

        stack.apply(change);
        this.affect(change);
    }

    /*
     * Note that this code is almost identical to insert(char). That's pretty
     * lame, but several attempt to clear this up have not been successful.
     * It'll do for now.
     */

    private void pasteText() {
        final Extract removed;
        final Change change;
        final TextIter selection;
        final int selectionOffset, offset, width;

        if (stash == null) {
            return;
        }

        if (buffer.getHasSelection()) {
            selection = buffer.getIter(selectionBound);
            selectionOffset = selection.getOffset();

            offset = normalizeOffset(insertOffset, selectionOffset);
            width = normalizeOffset(insertOffset, selectionOffset);

            removed = stack.extractRange(offset, width);
            change = new TextualChange(offset, removed, stash);
        } else {
            change = new InsertChange(insertOffset, stash);
        }

        stack.apply(change);
        this.affect(change);
    }

    /**
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
     * ends and have an offset greater than insertBound's, resulting in a
     * negative width.
     */

    private void deleteBack() {
        final TextIter start, end;

        end = buffer.getIter(insertBound);

        if (buffer.getHasSelection()) {
            start = buffer.getIter(selectionBound);
        } else {
            if (end.isStart()) {
                return;
            }
            start = end.copy();
            start.backwardChar();
        }

        deleteRange(start, end);
    }

    private void deleteAt() {
        final TextIter start, end;

        start = buffer.getIter(insertBound);

        if (buffer.getHasSelection()) {
            end = buffer.getIter(selectionBound);
        } else {
            if (start.isEnd()) {
                return;
            }
            end = start.copy();
            end.forwardChar();
        }

        deleteRange(start, end);
    }

    /**
     * Effect a deletion from start to end.
     */
    private void deleteRange(TextIter start, TextIter end) {
        int alpha, omega, offset, width;
        final Extract range;
        final Change change;

        alpha = start.getOffset();
        omega = end.getOffset();

        offset = normalizeOffset(alpha, omega);
        width = normalizeWidth(alpha, omega);

        range = stack.extractRange(offset, width);
        change = new DeleteChange(offset, range);

        stack.apply(change);
        this.affect(change);
    }

    private void toggleMarkup(Markup format) {
        TextIter start, end;
        int alpha, omega, offset, width;
        final Change change;
        final Extract original;

        /*
         * If there is a selection then toggle the markup applied there.
         * Otherwise, change the current insertion point formats.
         */

        if (buffer.getHasSelection()) {
            start = selectionBound.getIter();
            end = insertBound.getIter();

            alpha = start.getOffset();
            omega = end.getOffset();

            offset = normalizeOffset(alpha, omega);
            width = normalizeWidth(alpha, omega);

            original = stack.extractRange(offset, width);

            change = new FormatChange(offset, original, format);
            stack.apply(change);
            this.affect(change);

        } else {
            if (insertMarkup == format) {
                insertMarkup = null; // OR, something more block oriented?
            } else {
                insertMarkup = format;
            }
        }
    }

    private void insertImage() {}

    private void undo() {
        final Change change;

        change = stack.undo();
        if (change == null) {
            return;
        }

        reverse(change);
    }

    private void redo() {
        final Change change;

        change = stack.redo();
        if (change == null) {
            return;
        }

        affect(change);
    }

    /**
     * Cause the given Change to be reflected in the TextView. The assumption
     * is made that the backing TextBuffer is in a state where applying this
     * Change makes sense.
     */
    private void affect(Change change) {
        TextIter start, end;
        Extract r;
        int i, offset;
        Span s;
        TextTag[] tags;

        start = buffer.getIter(change.getOffset());

        if ((change instanceof InsertChange) || (change instanceof DeleteChange)
                || (change instanceof TextualChange)) {
            r = change.getRemoved();
            if (r != null) {
                end = buffer.getIter(change.getOffset() + r.getWidth());
                buffer.delete(start, end);
                start = end;
            }

            r = change.getAdded();
            if (r != null) {
                r = change.getAdded();
                for (i = 0; i < r.size(); i++) {
                    s = r.get(i);
                    buffer.insert(start, s.getText(), tagForMarkup(s.getMarkup()));
                }
            }
        } else if (change instanceof FormatChange) {
            r = change.getAdded();
            offset = change.getOffset();

            for (i = 0; i < r.size(); i++) {
                s = r.get(i);

                start = buffer.getIter(offset);
                offset += s.getWidth();
                end = buffer.getIter(offset);

                /*
                 * FUTURE this is horribly inefficient compared to just adding
                 * or removing the tag that has changed. But it is undeniably
                 * easy to express. To do this properly we'll have to get the
                 * individual Markup and whether it was added or removed from
                 * the FormatChange.
                 */

                buffer.removeAllTags(start, end);

                tags = tagsForMarkup(s.getMarkup());
                buffer.applyTag(tags, start, end);
            }
        }
    }

    /**
     * Revert this Change, removing it's affect on the view. A DeleteChange
     * will cause an insertion, etc.
     */
    private void reverse(Change change) {
        final TextIter start, end;
        Extract r;
        int i;
        Span s;

        start = buffer.getIter(change.getOffset());

        r = change.getAdded();
        if (r != null) {
            end = buffer.getIter(change.getOffset() + r.getWidth());
            buffer.delete(start, end);
        }

        r = change.getRemoved();
        if (r != null) {
            for (i = 0; i < r.size(); i++) {
                s = r.get(i);
                buffer.insert(start, s.getText(), tagForMarkup(s.getMarkup()));
            }
        }
    }

    /**
     * When text is cut or copied out, it will be cached here.
     */
    /*
     * TODO move to an application level reference.
     */
    private Extract stash;

    private void copyText() {
        extractText(true);
    }

    private void cutText() {
        extractText(false);
    }

    private void extractText(boolean copy) {
        final TextIter start, end;
        int alpha, omega, offset, width;
        final Change change;

        /*
         * If there's no selection, we can't "Copy" or "Cut"
         */

        if (!buffer.getHasSelection()) {
            return;
        }

        start = buffer.getIter(selectionBound);
        end = buffer.getIter(insertBound);

        alpha = start.getOffset();
        omega = end.getOffset();

        offset = normalizeOffset(alpha, omega);
        width = normalizeWidth(alpha, omega);

        /*
         * Copy the range to clipboard, being the "Copy" behviour.
         */

        stash = stack.extractRange(offset, width);

        /*
         * Put the extracted text into the system clipboard. TODO move to an
         * Application class.
         */

        owner = true;
        clipboard.setText(stash.getText());

        if (copy) {
            return;
        }

        /*
         * And now delete the selected range, which makes this the "Cut"
         * behaviour.
         */

        change = new DeleteChange(offset, stash);
        stack.apply(change);
        this.affect(change);
    }

    private static int normalizeOffset(int alpha, int omega) {
        if (omega > alpha) {
            return alpha;
        } else {
            return omega;
        }
    }

    private static int normalizeWidth(int alpha, int omega) {
        final int width;

        width = omega - alpha;

        if (width < 0) {
            return -width;
        } else {
            return width;
        }
    }

    private void exportContents() {
        final DocBookConverter converter;
        final Document book;

        converter = new DocBookConverter();
        converter.append(stack);
        book = converter.result();
        try {
            book.toXML(System.out);
        } catch (IOException ioe) {
            throw new Error(ioe);
        }
    }

    /**
     * Hookup signals to aggregate formats to be used on a subsequent
     * insertion. The insertMarkup array starts empty, and builds up as
     * formats are toggled by the user. When the cursor moves, the Set is
     * changed to the formatting applying on character back.
     */
    private void hookupFormatManagement() {
        insertMarkup = null;

        buffer.connect(new TextBuffer.NotifyCursorPosition() {
            public void onNotifyCursorPosition(TextBuffer source) {
                final TextIter pointer;
                int offset;

                pointer = buffer.getIter(insertBound);
                offset = pointer.getOffset();

                insertOffset = offset;

                insertMarkup = stack.getMarkupAt(offset);

                // DEBUG

                Rectangle location;
                Allocation alloc;

                location = view.getLocationOf(pointer);
                alloc = view.getAllocation();

                System.out.format("\t%4d + %4d = %4d\n", alloc.getY(), location.getY(), alloc.getY()
                        + location.getY());
                System.out.println();
            }
        });
    }

    private void clearFormat() {
        final TextIter start, end;
        final Extract original;
        final Change change;
        int alpha, omega, offset, width;

        /*
         * If there is a selection then clear the markup applied there. This
         * may not be the correct implementation; there could be Markups which
         * are structural and not block or inline.
         */

        if (buffer.getHasSelection()) {
            start = selectionBound.getIter();
            end = insertBound.getIter();

            alpha = start.getOffset();
            omega = end.getOffset();

            offset = normalizeOffset(alpha, omega);
            width = normalizeWidth(alpha, omega);

            original = stack.extractRange(offset, width);
            change = new FormatChange(offset, original);
            stack.apply(change);
            this.affect(change);
        }

        /*
         * Deactivate the any insert formatting.
         */

        insertMarkup = null;
    }

    private void hookupExternalClipboard() {
        clipboard = Clipboard.getDefault();

        /*
         * This gets hit whenever the clipboard is written to. In our case, we
         * toggle the owner state variable to true when we know we're about to
         * programatically set the clipboard; this handler gets called once,
         * and we can ignore it. Any other receipt of this event and we have
         * to get the text from the system.
         */

        clipboard.connect(new Clipboard.OwnerChange() {
            public void onOwnerChange(Clipboard source, EventOwnerChange event) {
                final Span span;
                final String str;

                if (owner) {
                    owner = false;
                } else {
                    str = clipboard.getText();
                    if (str == null) {
                        /*
                         * If the data in the system clipboard was put there
                         * in a a form other than plain text we get null back.
                         */
                        return;
                    }
                    span = new StringSpan(str, null);
                    stash = stack.extractFor(span);
                }
            }
        });
    }

    /*
     * Temporary!
     */
    void loadText(TextStack load) {
        final Extract entire;
        int i, x;
        Span span;
        Change change;

        entire = load.extractAll();

        x = 0;
        for (i = 0; i < entire.size(); i++) {
            span = entire.get(i);
            change = new InsertChange(x, span);

            stack.apply(change);
            this.affect(change);

            x += span.getWidth();
        }
        // TODO LoadChange?
    }
}
