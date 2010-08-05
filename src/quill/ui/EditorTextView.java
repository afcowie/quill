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
package quill.ui;

import org.gnome.gdk.Cursor;
import org.gnome.gdk.EventButton;
import org.gnome.gdk.EventCrossing;
import org.gnome.gdk.EventKey;
import org.gnome.gdk.Keyval;
import org.gnome.gdk.ModifierType;
import org.gnome.gdk.MouseButton;
import org.gnome.gdk.Rectangle;
import org.gnome.gtk.Allocation;
import org.gnome.gtk.EventBox;
import org.gnome.gtk.InputMethod;
import org.gnome.gtk.Label;
import org.gnome.gtk.Menu;
import org.gnome.gtk.MenuItem;
import org.gnome.gtk.SimpleInputMethod;
import org.gnome.gtk.TextBuffer;
import org.gnome.gtk.TextIter;
import org.gnome.gtk.TextMark;
import org.gnome.gtk.TextTag;
import org.gnome.gtk.TextView;
import org.gnome.gtk.TextWindowType;
import org.gnome.gtk.Widget;
import org.gnome.gtk.WrapMode;

import quill.client.Quill;
import quill.textbase.Change;
import quill.textbase.Common;
import quill.textbase.DeleteTextualChange;
import quill.textbase.Extract;
import quill.textbase.FormatTextualChange;
import quill.textbase.FullTextualChange;
import quill.textbase.HeadingSegment;
import quill.textbase.InsertTextualChange;
import quill.textbase.MarkerSpan;
import quill.textbase.Markup;
import quill.textbase.NormalSegment;
import quill.textbase.PreformatSegment;
import quill.textbase.QuoteSegment;
import quill.textbase.Segment;
import quill.textbase.Series;
import quill.textbase.Span;
import quill.textbase.SpanVisitor;
import quill.textbase.Special;
import quill.textbase.SplitStructuralChange;
import quill.textbase.StructuralChange;
import quill.textbase.TextChain;
import quill.textbase.TextualChange;
import quill.textbase.WordVisitor;

import static org.gnome.gtk.TextWindowType.TEXT;
import static quill.ui.Format.spelling;
import static quill.ui.Format.tagForMarkup;

abstract class EditorTextView extends TextView
{
    protected final TextView view;

    protected TextBuffer buffer;

    private InputMethod input;

    private TextMark selectionBound, insertBound;

    private TextChain chain;

    /**
     * Cache of the offset into the TextBuffer of the insertBound TextMark.
     */
    private int insertOffset;

    private Markup insertMarkup;

    /**
     * The model element that we are representing.
     */
    private Segment segment;

    private final UserInterface ui;

    private final PrimaryWindow primary;

    private final ComponentEditorWidget parent;

    EditorTextView(ComponentEditorWidget parent, Segment segment) {
        super();
        this.view = this;
        this.ui = Quill.getUserInterface();
        this.primary = parent.getPrimary();
        this.parent = parent;
        this.segment = segment;

        setupTextView();
        setupInsertMenu();
        setupContextMenu();

        displaySegment();

        hookupKeybindings();
        hookupFormatManagement();
    }

    private void setupTextView() {
        buffer = new TextBuffer();

        selectionBound = buffer.getSelectionBound();
        insertBound = buffer.getInsert();

        view.setBuffer(buffer);
        view.setWrapMode(WrapMode.WORD);

        view.setMarginLeft(3);
        view.setBorderWidth(2);

        view.setAcceptsTab(true);
    }

    /**
     * Override this and return true if you want Tab characters to be inserted
     * rather than swollowed.
     */
    protected boolean isTabAllowed() {
        return false;
    }

    /**
     * Override this and return false if you want spell checking off
     */
    protected boolean isSpellChecked() {
        return true;
    }

    /**
     * Override this and return false if you need to disallow Enter keys
     * (headings, properties)
     */
    protected boolean isEnterAllowed() {
        return true;
    }

    private void hookupKeybindings() {
        input = new SimpleInputMethod();

        input.connect(new InputMethod.Commit() {
            public void onCommit(InputMethod source, String text) {
                insertText(text);
            }
        });

        view.connect(new Widget.KeyPressEvent() {
            public boolean onKeyPressEvent(Widget source, EventKey event) {
                final Keyval key;
                final ModifierType mod;

                /*
                 * This is magic, actually. Both normal keystrokes and
                 * composed sequences will be delivered to us above in the
                 * InputMethod.Commit handler.
                 */

                if (input.filterKeypress(event)) {
                    return true;
                }

                /*
                 * Otherwise, we begin the logic to check and see if we're
                 * going to handle it ourselves.
                 */

                key = event.getKeyval();

                if (key == Keyval.Compose) {
                    return false;
                }

                if (key == Keyval.Escape) {
                    // deliberate no-op
                    return true;
                }

                if (key == Keyval.Insert) {
                    popupInsertMenu();
                    return true;
                }

                /*
                 * Other special keys that we DO handle. The newline case is
                 * interesting. We let a \n fly but clear any current
                 * formatting first.
                 */
                if (key == Keyval.Return) {
                    if (isEnterAllowed()) {
                        insertMarkup = null;
                        insertText("\n");
                    }
                    return true;
                } else if (key == Keyval.Delete) {
                    deleteAt();
                    return true;
                } else if (key == Keyval.BackSpace) {
                    deleteBack();
                    return true;
                }

                /*
                 * Context menu!
                 */

                if (key == Keyval.Menu) {
                    offerSpellingSuggestions();

                    /*
                     * Inhibit TextView's default menu.
                     */

                    return true;
                }

                /*
                 * Let modifier keys through; input methods, cursor movement,
                 * and selection seems to depend on this.
                 */

                if ((key == Keyval.ShiftLeft) || (key == Keyval.ShiftRight) || (key == Keyval.AltLeft)
                        || (key == Keyval.AltRight) || (key == Keyval.ControlLeft)
                        || (key == Keyval.ControlRight) || (key == Keyval.SuperLeft)
                        || (key == Keyval.SuperRight)) {
                    return false;
                }

                /*
                 * Now on to processing special keystrokes.
                 */

                mod = event.getState();

                if (mod == ModifierType.NONE) {
                    /*
                     * Special cases in cursor movement, but only if
                     * unmodified (otherwise, let default handler do its
                     * thing, and constrain it to this TextView.
                     */

                    if (key == Keyval.Up) {
                        return handleCursorUp();
                    }
                    if (key == Keyval.Left) {
                        return handleCursorLeft();
                    }
                    if (key == Keyval.Down) {
                        return handleCursorDown();
                    }
                    if (key == Keyval.Right) {
                        return handleCursorRight();
                    }

                    if (key == Keyval.PageUp) {
                        return handlePageUp();
                    }
                    if (key == Keyval.PageDown) {
                        return handlePageDown();
                    }

                    /*
                     * Other than those, we [cancel] the vertical movement
                     * cache, and continue.
                     */

                    x = -1;

                    if ((key == Keyval.Home) || (key == Keyval.End)) {
                        return false;
                    }

                    /*
                     * Tab is a strange one. At first glance it is tempting to
                     * set the TextView to not accept them and to have Tab
                     * change focus, but there is the case of program code in
                     * a preformatted block which might need indent support.
                     * So we swollow it unless we're in a preformatted code
                     * block.
                     */

                    if (key == Keyval.Tab) {
                        if (isTabAllowed()) {
                            insertText("\t");
                        }
                        return true;
                    }

                    /*
                     * We're not really supposed to get here, but (deep
                     * breath) let the TextView handle it. This had better not
                     * mutate the TextBuffer.
                     */
                    return false;
                }

                if (mod == ModifierType.SHIFT_MASK) {
                    if (key == Keyval.BackTab) {
                        return true;
                    }

                    /*
                     * Other mutating keystrokes should have been absorbed, so
                     * pass through.
                     */
                    return false;
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
                    } else if (key == Keyval.v) {
                        pasteText();
                        return true;
                    } else if (key == Keyval.x) {
                        cutText();
                        return true;
                    } else if (key == Keyval.Home) {
                        return handleJumpHome();
                    } else if (key == Keyval.End) {
                        return handleJumpEnd();
                    } else if (key == Keyval.Up) {
                        return handleJumpUp();
                    } else if (key == Keyval.Down) {
                        return handleJumpDown();
                    } else {
                        /*
                         * No special keybinding in the editor; PrimaryWindow
                         * has already handled program wide accelerators. pass
                         * through Ctrl+navigation for word-wise movement.
                         */
                        return false;
                    }
                }

                if (mod.contains(ModifierType.CONTROL_MASK) && mod.contains(ModifierType.SHIFT_MASK)) {
                    if (key == Keyval.Space) {
                        clearFormat();
                        return true;
                    } else if (key == Keyval.A) {
                        toggleMarkup(Common.APPLICATION);
                        return true;
                    } else if (key == Keyval.L) {
                        toggleMarkup(Common.LITERAL);
                        return true;
                    } else if (key == Keyval.F) {
                        toggleMarkup(Common.FILENAME);
                        return true;
                    } else if (key == Keyval.H) {
                        toggleMarkup(Common.HIGHLIGHT);
                        return true;
                    } else if (key == Keyval.M) {
                        // function or _m_ethod
                        toggleMarkup(Common.FUNCTION);
                        return true;
                    } else if (key == Keyval.O) {
                        toggleMarkup(Common.COMMAND);
                        return true;
                    } else if (key == Keyval.T) {
                        toggleMarkup(Common.TYPE);
                        return true;
                    } else if (key == Keyval.U) {
                        /*
                         * Special to GTK's default input method, so pass
                         * through!
                         */
                        return false;
                    } else {
                        /*
                         * Nothing special, pass through.
                         */
                        return false;
                    }
                }

                /*
                 * Going through this is a big excessive, but it gives us a
                 * differentiator for debugging.
                 */

                if (mod.contains(ModifierType.LOCK_MASK) || mod.contains(ModifierType.WINDOW_MASK)
                        || mod.contains(ModifierType.ALT_MASK)
                        || mod.contains(ModifierType.BUTTON_LEFT_MASK)
                        || mod.contains(ModifierType.BUTTON_MIDDLE_MASK)
                        || mod.contains(ModifierType.BUTTON_RIGHT_MASK)) {
                    /*
                     * Absorb as a defensive measure.
                     */
                    return true;
                }

                /*
                 * We didn't handle it, and are assuming we're capable of
                 * handing all keyboard input. If something that gets through
                 * mutates the buffer it will cause a crash.
                 */

                throw new IllegalStateException("\n" + "Unhandled " + key + " with " + mod);
            }
        });

        view.connect(new Widget.KeyReleaseEvent() {
            public boolean onKeyReleaseEvent(Widget source, EventKey event) {
                if (input.filterKeypress(event)) {
                    return true;
                }
                return false;
            }
        });
    }

    private void insertText(String text) {
        final Extract removed;
        final Span span;
        final TextualChange change;
        final TextIter selection;
        final int selectionOffset, offset, width;

        span = Span.createSpan(text, insertMarkup);

        if (buffer.getHasSelection()) {
            selection = buffer.getIter(selectionBound);
            selectionOffset = selection.getOffset();

            offset = normalizeOffset(insertOffset, selectionOffset);
            width = normalizeWidth(insertOffset, selectionOffset);

            removed = chain.extractRange(offset, width);
            change = new FullTextualChange(chain, offset, removed, span);
        } else {
            change = new InsertTextualChange(chain, insertOffset, span);
        }

        primary.apply(change);
    }

    private void pasteText() {
        final Extract stash, removed;
        final TextualChange change;
        final TextIter selection;
        final int selectionOffset, offset, width;

        stash = ui.getClipboard();
        if (stash == null) {
            return;
        }

        if (buffer.getHasSelection()) {
            selection = buffer.getIter(selectionBound);
            selectionOffset = selection.getOffset();

            offset = normalizeOffset(insertOffset, selectionOffset);
            width = normalizeWidth(insertOffset, selectionOffset);

            removed = chain.extractRange(offset, width);
            change = new FullTextualChange(chain, offset, removed, stash);
        } else {
            change = new InsertTextualChange(chain, insertOffset, stash);
        }

        /*
         * Propegate the change. After this wends its way though the layers,
         * it will result in ComponentEditorWindow calling this.affect().
         */

        primary.apply(change);
    }

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
        final TextualChange change;

        alpha = start.getOffset();
        omega = end.getOffset();

        offset = normalizeOffset(alpha, omega);
        width = normalizeWidth(alpha, omega);

        range = chain.extractRange(offset, width);
        change = new DeleteTextualChange(chain, offset, range);

        primary.apply(change);
    }

    private void toggleMarkup(Markup format) {
        TextIter start, end;
        int alpha, omega, offset, width;
        final TextualChange change;
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

            original = chain.extractRange(offset, width);

            change = new FormatTextualChange(chain, offset, original, format);
            primary.apply(change);
            this.affect(change);

            /*
             * Force deselect the range - you want to SEE the markup you just
             * applied! Also ensure the cursor is at the end (double click
             * select seems to put insertBound at the beginning).
             */

            end = buffer.getIter(offset + width);
            buffer.placeCursor(end);
        } else {
            if (insertMarkup == format) {
                insertMarkup = null; // OR, something more block oriented?
            } else {
                insertMarkup = format;
            }
        }
    }

    private void insertImage() {}

    /**
     * Cause the given Change to be reflected in the TextView. The assumption
     * is made that the backing TextBuffer is in a state where applying this
     * Change makes sense.
     */
    void affect(Change change) {
        final StructuralChange structural;
        final TextualChange textual;
        final TextIter start, finish;
        final int offset;
        final int alpha, omega;
        Extract r;
        int i;

        if (change instanceof StructuralChange) {
            structural = (StructuralChange) change;

            offset = structural.getOffset();
            start = buffer.getIter(offset);
            finish = buffer.getIterEnd();
            buffer.delete(start, finish);

        } else if (change instanceof FormatTextualChange) {
            textual = (TextualChange) change;

            r = textual.getAdded();
            if (r == null) {
                return;
            }

            offset = textual.getOffset();
            alpha = offset;

            r.visit(new SpanVisitor() {
                private int offset = alpha;

                public boolean visit(Span s) {
                    final TextIter start, finish;
                    final TextTag tag;

                    start = buffer.getIter(offset);
                    offset += s.getWidth();
                    finish = buffer.getIter(offset);

                    /*
                     * FUTURE this is horribly inefficient compared to just
                     * adding or removing the tag that has changed. But it is
                     * undeniably easy to express. To do this properly we'll
                     * have to get the individual Markup and whether it was
                     * added or removed from the FormatChange.
                     */

                    buffer.removeAllTags(start, finish);
                    tag = tagForMarkup(s.getMarkup());
                    if (tag != null) {
                        buffer.applyTag(tag, start, finish);
                    }
                    return false;
                }
            });

            // BUG?
            omega = offset;

            checkSpellingRange(alpha, omega);

            /*
             * Toggling placed the cursor. Hm.
             */
        } else if (change instanceof TextualChange) {
            textual = (TextualChange) change;

            alpha = textual.getOffset();
            i = alpha;

            start = buffer.getIter(textual.getOffset());

            r = textual.getRemoved();
            if (r != null) {
                finish = buffer.getIter(textual.getOffset() + r.getWidth());
                buffer.delete(start, finish);
                // start = finish;
            }

            r = textual.getAdded();
            if (r != null) {
                r.visit(new SpanVisitor() {
                    public boolean visit(Span s) {
                        insertSpan(start, s);
                        return false;
                    }
                });
                i += r.getWidth();
            }
            omega = i;
            checkSpellingRange(alpha, omega);

            // start is at the end now, thaks to TextBuffer's insert()
            buffer.placeCursor(start);

        } else {
            throw new IllegalStateException("Unknown Change type");
        }
        view.grabFocus();
    }

    /**
     * Revert this Change, removing it's affect on the view. A
     * DeleteTextualChange will cause an insertion, etc.
     */
    void reverse(Change obj) {
        final StructuralChange structural;
        final TextualChange textual;
        final TextIter start, finish, pointer;
        int alpha, omega;
        Extract r;

        if (obj instanceof StructuralChange) {
            structural = (StructuralChange) obj;

            throw new UnsupportedOperationException("Not yet implemented " + structural); // FIXME

        } else if (obj instanceof TextualChange) {
            textual = (TextualChange) obj;

            /*
             * And now do what is necessary to reflect the change in this UI.
             */

            alpha = textual.getOffset();
            omega = alpha;

            start = buffer.getIter(alpha);

            r = textual.getAdded();
            if (r != null) {
                finish = buffer.getIter(alpha + r.getWidth());
                buffer.delete(start, finish);
            }

            r = textual.getRemoved();
            if (r != null) {
                r.visit(new SpanVisitor() {
                    public boolean visit(Span s) {
                        insertSpan(start, s);
                        return false;
                    }
                });
                omega += r.getWidth();
            }

            checkSpellingRange(alpha, omega);

            /*
             * There is an usual experience we'd like to create: when applying
             * a formatting, and then undoing it, you tend to want to apply a
             * DIFFERENT formatting, and so [re]selecting the range that was
             * picked makes that much easier. Otherwise we do what we do
             * elsewhere: de-select and put the cursor at the end of the
             * current operation range.
             */
            if (obj instanceof FormatTextualChange) {
                pointer = buffer.getIter(alpha);
                buffer.selectRange(pointer, start);
            } else {
                buffer.placeCursor(start);
            }
            view.grabFocus();
        }
    }

    private void copyText() {
        extractText(true);
    }

    private void cutText() {
        extractText(false);
    }

    private void extractText(boolean copy) {
        final TextIter start, end;
        int alpha, omega, offset, width;
        final Extract extract;
        final TextualChange change;

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

        extract = chain.extractRange(offset, width);
        ui.setClipboard(extract);

        if (copy) {
            return;
        }

        /*
         * And now delete the selected range, which makes this the "Cut"
         * behaviour.
         */

        change = new DeleteTextualChange(chain, offset, ui.getClipboard());
        primary.apply(change);
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
                final Rectangle rect;
                final Allocation alloc;

                /*
                 * Find out the styling appropriate to the proceeding
                 * character.
                 */

                pointer = buffer.getIter(insertBound);
                offset = pointer.getOffset();

                insertOffset = offset;
                if (offset != 0) {
                    offset--;
                }
                insertMarkup = chain.getMarkupAt(offset);

                /*
                 * Except, that if we're beside a footnote, then we need to
                 * not be inheriting that styling. Otherwise we extend the
                 * note ref, which is bad!
                 */

                if (insertMarkup instanceof Special) {
                    insertMarkup = null;
                }

                /*
                 * Now, make sure the complete line is on screen.
                 */

                rect = view.getLocation(pointer);
                alloc = view.getAllocation();

                primary.scrollEditorToShow(alloc.getY() + rect.getY(), rect.getHeight() + 5);
            }
        });

        /*
         * When you click in an EditorTextView, you may have come from
         * somewhere else, so update the enclosing ComponentEditorWidget's
         * idea of what the current segment is, thereby allowing it to drive
         * preview of the correct page.
         */
        view.connect(new Widget.ButtonPressEvent() {
            public boolean onButtonPressEvent(Widget source, EventButton event) {
                parent.setCursor(segment);
                return false;
            }
        });
    }

    private void clearFormat() {
        final TextIter start, end;
        final Extract original;
        final TextualChange change;
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

            original = chain.extractRange(offset, width);
            change = new FormatTextualChange(chain, offset, original);

            primary.apply(change);
            this.affect(change);
        }

        /*
         * Deactivate the any insert formatting.
         */

        insertMarkup = null;
    }

    /**
     * Make this EditorTextView reflect the contents of the Segment this
     * EditorTextView was initialized with.
     */
    private void displaySegment() {
        final Extract entire;
        final TextIter pointer;

        /*
         * Easy enough to just set the internal TextStack backing this editor
         * to the one belonging to the Segment passed in.
         */

        chain = segment.getText();

        /*
         * But now we need to cycle over its Spans and place its content into
         * the view.
         */

        entire = chain.extractAll();
        if (entire == null) {
            return;
        }

        pointer = buffer.getIterStart();

        entire.visit(new SpanVisitor() {
            public boolean visit(Span span) {
                insertSpan(pointer, span);
                return false;
            }
        });

        checkSpellingRange(0, chain.length());
    }

    private void insertSpan(TextIter pointer, Span span) {
        if (span instanceof MarkerSpan) {
            buffer.insert(pointer, createEndnote(span), view);
            /*
             * Strangely, adding a child Widget doesn't seem to result in a
             * cursor notification. So force it.
             */
            buffer.placeCursor(pointer);
        } else {
            buffer.insert(pointer, span.getText(), tagForMarkup(span.getMarkup()));
        }
    }

    private static Widget createEndnote(Span span) {
        final String ref;
        final Label label;
        final EventBox box;

        ref = span.getText();
        label = new Label(ref);

        box = new EventBox();
        box.setVisibleWindow(false);
        box.add(label);

        box.connect(new Widget.ButtonPressEvent() {
            public boolean onButtonPressEvent(Widget source, EventButton event) {
                // TODO
                return false;
            }
        });
        box.connect(new Widget.EnterNotifyEvent() {
            public boolean onEnterNotifyEvent(Widget source, EventCrossing event) {
                box.getWindow().setCursor(Cursor.LINK);
                return false;
            }
        });
        box.connect(new Widget.LeaveNotifyEvent() {
            public boolean onLeaveNotifyEvent(Widget source, EventCrossing event) {
                box.getWindow().setCursor(Cursor.TEXT);
                return false;
            }
        });

        box.showAll();
        return box;
    }

    private Menu split;

    private Class<?>[] types;

    private String[] texts;

    private MenuItem createMenuItem(final String label, final Class<?> type) {
        final MenuItem result;

        result = new MenuItem(label, new MenuItem.Activate() {
            public void onActivate(MenuItem source) {
                try {
                    handleInsertSegment((Segment) type.newInstance());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        return result;
    }

    /*
     * Yes it would be "better" to write this such that the two arrays weren't
     * coupled but instead strongly typed objects with two+ fields.
     */
    private void setupInsertMenu() {
        int i;
        MenuItem item;

        split = new Menu();

        types = new Class<?>[] {
                NormalSegment.class, PreformatSegment.class, QuoteSegment.class, HeadingSegment.class
        };

        texts = new String[] {
                "Normal _paragraphs", "Preformatted _code block", "Block _quote", "Section _heading"
        };

        for (i = 0; i < types.length; i++) {
            item = createMenuItem(texts[i], types[i]);
            split.append(item);
        }

        split.showAll();
    }

    /*
     * This is a bit hideous.
     */
    private void popupInsertMenu() {
        final Widget[] children;
        int i;
        final int len;
        final Segment next;
        final Series series;

        /*
         * Turn off the type that the current Segment is
         */

        children = split.getChildren();

        for (i = 0; i < types.length; i++) {
            if (types[i].isInstance(segment)) {
                children[i].setSensitive(false);
            } else {
                children[i].setSensitive(true);
            }
        }

        /*
         * if we're at the last character of an existing Segment, then turn
         * off the type that's already following
         */

        if (insertOffset == chain.length()) {
            series = parent.getSeries();
            i = series.indexOf(segment);
            len = series.size();

            if (i < len - 1) {
                i++;
                next = series.get(i);

                for (i = 0; i < types.length; i++) {
                    if (types[i].isInstance(next)) {
                        children[i].setSensitive(false);
                        break;
                    }
                }
            }

        }
        split.popup();
    }

    private void setupContextMenu() {
        /*
         * The default context menu created by TextView on a right click popup
         * is annoying in that it contains stuff about input methods and
         * unicode, all entirely unnecessary. The TextView API doesn't give us
         * anything to inhibit this nonsense [GtkSettings does] but in any
         * case we need to stop GTK from mutating our buffer without our
         * action. So we simply inhibit their context menu entirely.
         */

        view.connect(new Widget.ButtonPressEvent() {
            public boolean onButtonPressEvent(Widget source, EventButton event) {
                final MouseButton button;
                final int xe, ye, X, Y;
                final TextIter pointer;

                /*
                 * When the user clicks somewhere else, forget the cached
                 * up/down movement point.
                 */

                x = -1;

                button = event.getButton();

                if (button == MouseButton.RIGHT) {
                    /*
                     * We want to send the cursor to wherever the click was.
                     * FUTURE this isn't as bullet proof as we'd like, since
                     * if there is already a context menu up then the click
                     * will be lost dismissing the menu. What can we do about
                     * that?
                     */

                    xe = (int) event.getX();
                    ye = (int) event.getY();

                    X = view.convertWindowToBufferCoordsX(TEXT, xe);
                    Y = view.convertBufferToWindowCoordsY(TEXT, ye);

                    pointer = view.getIterAtLocation(X, Y);
                    buffer.placeCursor(pointer);

                    /*
                     * Now see if there is any spelling to be done: present
                     * context menu with suggestions.
                     */

                    offerSpellingSuggestions();

                    /*
                     * Inhibit TextView's default menu.
                     */
                    return true;
                }

                return false;
            }
        });
    }

    /**
     * Take the necessary actions to create a new Segment, which you pass in.
     * If we're at the end of the view we're appending. Jump the logic to the
     * user interface facades on PrimaryWindow.
     */
    private void handleInsertSegment(Segment addition) {
        final Change change;

        addition.setText(new TextChain());

        change = new SplitStructuralChange(segment, insertOffset, addition);

        primary.apply(change);
    }

    /*
     * Cursor key handling
     */

    /**
     * Horizontal position to place the cursor at when scrolling vertically,
     * in window co-ordinates. A value of -1 means unset.
     */
    /*
     * This is, potentially, rather poorly named, but so far it's the only
     * usage, and while I am reluctant to consume a single letter utility
     * variable name, lower case x is the window co-ordinates compliment of
     * upper case X which is the buffer co-ordinate we get and send in the
     * following methods.
     */
    private int x;

    void placeCursorFirstLine(int requested) {
        TextIter pointer;
        Rectangle position;
        int X, Y;

        pointer = buffer.getIterStart();

        if (requested != 0) {
            position = view.getLocation(pointer);
            X = view.convertWindowToBufferCoordsX(TEXT, requested);

            Y = position.getY();

            pointer = view.getIterAtLocation(X, Y);
        }
        x = requested;

        buffer.placeCursor(pointer);
    }

    /*
     * The value of -1 is overloaded here in the "moved left" case, and
     * interpreted to mean go to the last character of the last line. It works
     * out nicely because during left movement we would need to reset to -1
     * anyway.
     */
    void placeCursorLastLine(int requested) {
        final int length;
        TextIter pointer;
        final Rectangle position;
        int X, Y;

        length = chain.length();
        pointer = buffer.getIter(length);

        if (requested != -1) {
            position = view.getLocation(pointer);
            X = view.convertWindowToBufferCoordsX(TEXT, requested);

            Y = position.getY();

            pointer = view.getIterAtLocation(X, Y);
        }
        x = requested;

        buffer.placeCursor(pointer);
    }

    void placeCursorAtLocation(final int requested, final int target) {
        final Allocation alloc;
        final TextIter pointer;
        final int X, Y, y;

        X = view.convertWindowToBufferCoordsX(TEXT, requested);
        x = requested;

        alloc = this.getAllocation();
        y = target - alloc.getY();
        Y = view.convertWindowToBufferCoordsY(TEXT, y);

        pointer = view.getIterAtLocation(X, Y);

        buffer.placeCursor(pointer);
    }

    private boolean handleCursorUp() {
        TextIter pointer;
        Rectangle position;
        int X, Y;

        pointer = buffer.getIter(insertOffset);

        if (x == -1) {
            position = view.getLocation(pointer);
            X = position.getX();
            x = view.convertBufferToWindowCoordsX(TextWindowType.TEXT, X);
        }

        if (pointer.backwardDisplayLine(view)) {
            X = view.convertWindowToBufferCoordsX(TEXT, x);

            position = view.getLocation(pointer);
            Y = position.getY();

            pointer = view.getIterAtLocation(X, Y);

            buffer.placeCursor(pointer);
            return true;
        } else {
            parent.moveCursorUp(view, x);
            return true;
        }
    }

    private boolean handleCursorLeft() {

        x = -1;

        if (insertOffset == 0) {
            parent.moveCursorUp(view, -1);
            return true;
        } else {
            return false;
        }
    }

    private boolean handleCursorDown() {
        TextIter pointer;
        Rectangle position;
        int X, Y;

        pointer = buffer.getIter(insertOffset);

        if (x == -1) {
            position = view.getLocation(pointer);
            X = position.getX();
            x = view.convertBufferToWindowCoordsX(TextWindowType.TEXT, X);
        }

        /*
         * Although forwardDisplayLine() does the right thing, the weird GTK
         * behaviour is that it returns false if it becomes the end iterator,
         * even if it changed the TextIter! That screws us up if you cursor
         * down to a blank line as last line. So we do the dance of going to
         * the end of the display line we're on; if we're not at the end of
         * the buffer then we know we can go down at least one more display
         * line. forwardChar() is cheaper than forwardDisplayLine(); we're at
         * the end now and all we want to do is get the cursor onto the next
         * display line so we can measure it's Y location.
         */

        pointer.forwardDisplayLineEnd(view);
        if (!pointer.isEnd()) {
            pointer.forwardChar(); // move to next line

            X = view.convertWindowToBufferCoordsX(TEXT, x);

            position = view.getLocation(pointer);
            Y = position.getY();

            pointer = view.getIterAtLocation(X, Y);

            buffer.placeCursor(pointer);
            return true;
        } else {
            parent.moveCursorDown(view, x);
            return true;
        }
    }

    private boolean handleCursorRight() {
        final int len;

        x = -1;

        len = chain.length();

        if (insertOffset == len) {
            parent.moveCursorDown(view, 0);
            return true;
        } else {
            return false;
        }
    }

    // duplicate of page down
    private boolean handlePageUp() {
        final TextIter pointer;
        final Rectangle rect;
        final Allocation alloc;
        final int y, Y, X;

        pointer = buffer.getIter(insertOffset);
        rect = view.getLocation(pointer);
        alloc = view.getAllocation();

        if (x == -1) {
            X = rect.getX();
            x = view.convertBufferToWindowCoordsX(TextWindowType.TEXT, X);
        }

        Y = alloc.getY() + rect.getY();
        y = view.convertBufferToWindowCoordsY(TEXT, Y);

        parent.movePageUp(x, y);

        return true;
    }

    private boolean handlePageDown() {
        final TextIter pointer;
        final Rectangle rect;
        final Allocation alloc;
        final int y, Y, X;

        pointer = buffer.getIter(insertOffset);
        rect = view.getLocation(pointer);
        alloc = view.getAllocation();

        if (x == -1) {
            X = rect.getX();
            x = view.convertBufferToWindowCoordsX(TextWindowType.TEXT, X);
        }

        Y = alloc.getY() + rect.getY();
        y = view.convertBufferToWindowCoordsY(TEXT, Y);

        parent.movePageDown(x, y);

        return true;
    }

    private boolean handleJumpHome() {
        parent.moveCursorStart();

        return true;
    }

    private boolean handleJumpEnd() {
        parent.moveCursorEnd();

        return true;
    }

    private boolean handleJumpUp() {
        final TextIter pointer;

        if (insertOffset == 0) {
            /*
             * TODO. It would be cool to place the cursor at the start of the
             * preceeding editor's last paragraph.
             */
            parent.moveCursorUp(view, -1);
        } else {
            pointer = buffer.getIter(insertOffset);

            if (pointer.startsLine()) {
                pointer.backwardLine();
            }
            while (!pointer.startsLine()) {
                pointer.backwardChar();
            }

            buffer.placeCursor(pointer);
        }

        return true;
    }

    private boolean handleJumpDown() {
        final TextIter pointer;

        if (insertOffset == chain.length()) {
            /*
             * TODO. It would be cool to place the cursor at the end of the
             * following editor's first paragraph.
             */
            parent.moveCursorDown(view, 0);
        } else {
            pointer = buffer.getIter(insertOffset);

            if (pointer.endsLine()) {
                pointer.forwardLine();
            }
            while (!pointer.endsLine()) {
                pointer.forwardChar();
            }

            buffer.placeCursor(pointer);
        }

        return true;
    }

    /**
     * Iterate over words from before(begin) to after(end)
     */
    private void checkSpellingRange(int begin, final int end) {
        int alpha, omega;
        final TextIter start, finish;

        /*
         * Some blocks (ie PreformatSegment as presented by
         * PreformatEditorTextView) are, by design, entirely unchecked, so
         * bail out if so.
         */

        if (!isSpellChecked()) {
            return;
        }

        /*
         * There's a corner case where if you type space to complete or split
         * a word, you've left the previous word behind without checking it.
         * So push back one (so long as there is room).
         * 
         * This is somewhat ironic after all the work we put in to getting the
         * definition of word boundaries right in Node.
         */

        if (begin > 0) {
            begin--;
        }

        /*
         * Seek backwards and forwards to find the beginning and end of the
         * word(s) in the given range.
         */

        alpha = chain.wordBoundaryBefore(begin);
        omega = chain.wordBoundaryAfter(end);

        /*
         * There's an annoying bug whereby if you type Space in the middle of
         * a mispelled word the space ends up being marked as misspelled,
         * likely just a result of the TextTag being propegated with right
         * gravity. Work around this by just nuking all the error markup along
         * the range being checked, and then only [re]highlighting words that
         * need it.
         */

        start = buffer.getIter(alpha);
        finish = buffer.getIter(omega);
        buffer.removeTag(spelling, start, finish);

        /*
         * Then iterate forward across the range and mark mispelled words!
         */

        chain.visit(new WordVisitor() {
            public boolean visit(String word, boolean skip, int begin, int end) {
                final TextIter start, finish;

                if (skip) {
                    return false;
                }

                if (!ui.dict.check(word)) {
                    start = buffer.getIter(begin);
                    finish = buffer.getIter(end);
                    buffer.applyTag(spelling, start, finish);
                }

                return false;
            }
        }, alpha, omega);
    }

    /**
     * Find the word under the cursor, feed it to Enchant, and then popup an
     * window with suggestions.
     * 
     * I experimented with selecting the word being considered [which is what
     * Writer does] but after some consideration I decided it's more
     * distracting than not. If you're asking for suggestions, you already
     * have a good idea what word it is you were working on.
     */
    /*
     * Implemented using a WordVisitor (even though we only need the first
     * word and only expect [and accept] one) since it allows us to access the
     * logic around whether or not a word should be spell checked based on its
     * Markup. We were using TextChain's getWordAt() but we need to make the
     * boundary calls anyway in order to be able to replace the word with a
     * suggestion.
     */
    private void offerSpellingSuggestions() {
        final int alpha, omega;

        /*
         * Seek backwards and forwards to find the beginning and end of the
         * word(s) in the given range.
         */

        alpha = chain.wordBoundaryBefore(insertOffset - 1);
        omega = chain.wordBoundaryAfter(insertOffset);

        chain.visit(new SuggestionsWordVisitor(), alpha, omega);
    }

    /*
     * Convenience for grouping the calls by offerSpellingSuggestions().
     */
    private class SuggestionsWordVisitor implements WordVisitor, SuggestionsPopupMenu.WordSelected
    {
        private int offset;

        private int wide;

        private SuggestionsWordVisitor() {}

        public boolean visit(final String word, final boolean skip, final int begin, final int end) {
            final TextIter pointer;
            final SuggestionsPopupMenu popup;
            final Rectangle rect;
            final int x, y, h, X, Y, xP, yP;
            final org.gnome.gdk.Window underlying;

            if ((word == null) || (word.equals(""))) {
                return false;
            }

            popup = new SuggestionsPopupMenu();
            popup.populateSuggestions(word);

            /*
             * This controls the positioning of the popup context menu. It was
             * tempting to use the word beginning, but it seems conventional
             * across the desktop that the popup is placed at mouse [normal]
             * or cursor position [enlightened editors].
             * 
             * It is necessary to go to all the effort to work out the
             * position of the cursor because if we use the default popup()
             * handler and press Menu but with the mouse elsewhere, then the
             * context menu will be at mouse pointer, not at the word where
             * the cursor is.
             */

            pointer = buffer.getIter(insertOffset);

            rect = view.getLocation(pointer);
            X = rect.getX();
            Y = rect.getY();
            h = rect.getHeight();
            x = view.convertBufferToWindowCoordsX(TextWindowType.TEXT, X);
            y = view.convertBufferToWindowCoordsY(TextWindowType.TEXT, Y);

            underlying = view.getWindow();
            xP = underlying.getOriginX();
            yP = underlying.getOriginY();

            popup.presentAt(x + xP, y + yP, h);

            offset = begin;
            wide = end - begin;
            popup.connect(this);

            return true;
        }

        // similar to insertText()
        public void onWordSelected(String word, boolean replacement) {
            final Extract removed;
            final Span span;
            final Change change;
            final TextIter start, finish;

            if (replacement) {
                // new text, so mutate, which will result in a recheck
                removed = chain.extractRange(offset, wide);
                span = Span.createSpan(word, insertMarkup);
                change = new FullTextualChange(chain, offset, removed, span);

                primary.apply(change);
            } else {
                // the word has been added, so we need to unmark it.
                start = buffer.getIter(offset);
                finish = buffer.getIter(offset + wide);
                buffer.removeTag(spelling, start, finish);
            }
        }
    }

    int getInsertOffset() {
        return insertOffset;
    }
}
