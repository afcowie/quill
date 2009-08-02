/*
 * ValidateChangePropagation.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the suite it is a part of, are made available
 * to you by the authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.gnome.gtk.Container;
import org.gnome.gtk.TextBuffer;
import org.gnome.gtk.TextIter;
import org.gnome.gtk.Widget;

import quill.textbase.Change;
import quill.textbase.CharacterSpan;
import quill.textbase.DataLayer;
import quill.textbase.Folio;
import quill.textbase.InsertTextualChange;
import quill.textbase.Segment;
import quill.textbase.Span;
import quill.textbase.StringSpan;
import quill.textbase.TextChain;

import static quill.client.Quill.ui;

public class ValidateChangePropagation extends GraphicalTestCase
{

    public final void testSetupBlank() throws ValidityException, ParsingException, IOException {
        final DataLayer data;
        final Folio folio1, folio2;

        data = new DataLayer();

        data.createDocument();
        folio1 = data.getActiveDocument();
        assertNotNull(folio1);

        data.createDocument();
        folio2 = data.getActiveDocument();
        assertNotNull(folio2);

        assertNotSame(folio1, folio2);
    }

    public final void testInsertText() throws ValidityException, ParsingException, IOException {
        final DataLayer data;
        final Folio folio;
        final Segment segment;
        final TextChain chain;
        final Change change;
        final Span span;
        final OutputStream out;
        final String expected;

        data = new DataLayer();
        ui = new UserInterface(data);

        data.createDocument();
        folio = data.getActiveDocument();
        ui.displayDocument(folio);

        segment = folio.get(0).get(1);
        chain = segment.getText();
        span = new CharacterSpan('h', null);

        change = new InsertTextualChange(chain, 0, span);
        ui.apply(change);

        out = new ByteArrayOutputStream();
        data.saveDocument(out);

        expected = combine(new String[] {
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<book version=\"5.0\" xmlns=\"http://docbook.org/ns/docbook\">",
                "<chapter>",
                "<para>",
                "h",
                "</para>",
                "</chapter>",
                "</book>"
        });
        assertEquals(expected, out.toString());
    }

    public final void testReplaceText() throws ValidityException, ParsingException, IOException {
        final DataLayer data;
        final Folio folio;
        final Segment segment;
        final TextChain chain;
        Change change;
        final Span span;
        OutputStream out;
        String expected;
        final EditorTextView editor;
        final TextBuffer buffer;
        final TextIter start, end;

        data = new DataLayer();
        ui = new UserInterface(data);

        data.createDocument();
        folio = data.getActiveDocument();
        ui.displayDocument(folio);

        /*
         * Establish some starting text.
         */

        segment = folio.get(0).get(1);
        chain = segment.getText();
        span = new StringSpan("This is a test of the emergency broadcast system", null);

        change = new InsertTextualChange(chain, 0, span);
        ui.apply(change);

        out = new ByteArrayOutputStream();
        data.saveDocument(out);

        expected = combine(new String[] {
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<book version=\"5.0\" xmlns=\"http://docbook.org/ns/docbook\">",
                "<chapter>",
                "<para>",
                "This is a test of the emergency broadcast",
                "system",
                "</para>",
                "</chapter>",
                "</book>"
        });
        assertEquals(expected, out.toString());

        /*
         * Now attempt to simulate the user replacing some of the text by
         * doing a recursive descent to find the editor then using the
         * interactive methods on its TextBuffer. Our code makes the
         * assumption that calls within the user-action pairs result from,
         * well, user action :) and so this causes the editor to raise and
         * apply Changes.
         */

        editor = (EditorTextView) findEditor(ui.primary.getChild());

        buffer = editor.getBuffer();
        start = buffer.getIter(9);
        end = buffer.getIter(21);
        buffer.selectRange(start, end);
        assertEquals(" test of the", buffer.getText(start, end, true));

        buffer.beginUserAction();
        buffer.delete(start, end);
        buffer.insert(start, "n");
        buffer.endUserAction();

        out = new ByteArrayOutputStream();
        data.saveDocument(out);

        expected = combine(new String[] {
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<book version=\"5.0\" xmlns=\"http://docbook.org/ns/docbook\">",
                "<chapter>",
                "<para>",
                "This is an emergency broadcast system",
                "</para>",
                "</chapter>",
                "</book>"
        });
        assertEquals(expected, out.toString());
    }

    // recursive
    private static Widget findEditor(Widget widget) {
        final Container container;
        final Widget[] children;
        Widget child, result;
        int i;

        assertTrue(widget instanceof Container);
        container = (Container) widget;
        children = container.getChildren();

        for (i = 0; i < children.length; i++) {
            child = children[i];

            if (child instanceof NormalEditorTextView) {
                return child;
            }

            if (child instanceof Container) {
                result = findEditor(child);
                if (result != null) {
                    return result;
                }
            }

        }
        return null;
    }

    private static String combine(String[] elements) {
        StringBuilder buf;

        buf = new StringBuilder(128);

        for (String element : elements) {
            buf.append(element);
            buf.append('\n');
        }

        return buf.toString();
    }
}
