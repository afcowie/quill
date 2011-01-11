/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2009-2011 Operational Dynamics Consulting, Pty Ltd
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

import java.io.IOException;

import parchment.manuscript.Manuscript;
import quill.client.ImproperFilenameException;
import quill.textbase.Common;
import quill.textbase.Folio;
import quill.textbase.NormalSegment;
import quill.textbase.Segment;
import quill.textbase.Series;
import quill.textbase.Span;

import static quill.client.IOTestCase.ensureDirectory;
import static quill.textbase.Span.createSpan;

public class ValidateDocumentModified extends GraphicalTestCase
{
    private static final void insertThreeSpansIntoFirstSegment(final PrimaryWindow primary) {
        final SeriesEditorWidget parent;
        final EditorTextView editor;
        Folio folio;
        final Span[] spans;
        int i;
        Span span;
        Series series;
        Segment segment;

        folio = primary.getDocument();
        series = folio.getSeries(0);
        segment = series.getSegment(1);
        assertTrue(segment instanceof NormalSegment);

        parent = primary.testGetEditor();
        editor = parent.testGetEditor(1);

        spans = new Span[] {
            createSpan("Hello", null),
            createSpan(" ", null),
            createSpan("World", Common.BOLD)
        };

        for (i = 0; i < spans.length; i++) {
            span = spans[i];
            editor.testAppendSpan(span);
        }
    }

    public final void testChangeCausesModified() throws IOException {
        final Manuscript manuscript;
        final Folio folio;
        final PrimaryWindow primary;

        manuscript = new Manuscript();
        folio = manuscript.createDocument();

        primary = new PrimaryWindow();
        primary.displayDocument(folio);

        /*
         * An empty document is not modified.
         */

        assertFalse(primary.isModified());

        /*
         * Modify, then undo back to empty.
         */

        insertThreeSpansIntoFirstSegment(primary);

        assertTrue(primary.isModified());
        primary.undo();
        assertTrue(primary.isModified());
        primary.undo();
        assertTrue(primary.isModified());
        primary.undo();
        assertFalse(primary.isModified());
    }

    public final void testSaveClearsModified() throws IOException, ImproperFilenameException,
            SaveCancelledException {
        final Manuscript manuscript;
        final Folio folio;
        final PrimaryWindow primary;

        manuscript = new Manuscript();
        folio = manuscript.createDocument();

        primary = new PrimaryWindow();
        primary.displayDocument(folio);

        insertThreeSpansIntoFirstSegment(primary);

        assertTrue(primary.isModified());

        ensureDirectory("tmp/unittests/quill/ui/");
        manuscript.setFilename("tmp/unittests/quill/ui/ValidateDocumentModified.parchment");
        primary.saveDocument();
        assertFalse(primary.isModified());

        /*
         * Now walk the undo/redo stack backwards and forwards past the new
         * (non-zero) save point to see if the modified behaviour holds.
         */

        primary.undo();
        assertTrue(primary.isModified());

        primary.redo();
        assertFalse(primary.isModified());

        insertThreeSpansIntoFirstSegment(primary);
        assertTrue(primary.isModified());
        primary.undo();
        primary.undo();
        primary.undo();
        assertFalse(primary.isModified());
    }
}
