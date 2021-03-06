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
package parchment.quack;

import java.io.IOException;

import nu.xom.ParsingException;
import nu.xom.ValidityException;
import parchment.manuscript.InvalidDocumentException;
import quill.client.ImproperFilenameException;
import quill.textbase.ChapterSegment;
import quill.textbase.Component;
import quill.textbase.Extract;
import quill.textbase.MarkerSpan;
import quill.textbase.NormalSegment;
import quill.textbase.Segment;
import quill.textbase.Series;
import quill.textbase.Span;
import quill.textbase.SpanVisitor;
import quill.textbase.Special;
import quill.textbase.StringSpan;

public class ValidateCitationConversion extends QuackTestCase
{
    public final void testMarkerSpan() {
        Span span;

        span = Span.createMarker("[Penrose 1989]", Special.CITE);
        assertTrue(span instanceof MarkerSpan);
        assertEquals("[Penrose 1989]", span.getText());
        assertEquals(Special.CITE, span.getMarkup());
    }

    public final void testInlineCite() throws IOException, ValidityException, ParsingException,
            ImproperFilenameException, InvalidDocumentException {
        final Component component;
        final Series series;
        Segment segment;
        final Extract entire;

        component = loadDocument("tests/parchment/quack/Citation.xml");
        series = component.getSeriesMain();

        assertEquals(2, series.size());

        segment = series.getSegment(0);
        assertTrue(segment instanceof ChapterSegment);
        segment = series.getSegment(1);
        assertTrue(segment instanceof NormalSegment);

        entire = segment.getEntire();
        entire.visit(new SpanVisitor() {
            private int i = 0;

            public boolean visit(Span span) {
                switch (i) {
                case 0:
                    assertTrue(span instanceof StringSpan);
                    break;
                case 1:
                    assertTrue(span instanceof MarkerSpan);
                    assertEquals(Special.CITE, span.getMarkup());
                    break;
                default:
                    fail();
                }
                i++;
                return false;
            }
        });

        compareDocument(component);
    }
}
