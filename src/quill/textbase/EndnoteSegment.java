/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2010 Operational Dynamics Consulting, Pty Ltd
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

/**
 * The text of an endnote (or footnote?; not implemented).
 * 
 * @author Andrew Cowie
 */
public final class EndnoteSegment extends Segment
{
    // for loading
    public EndnoteSegment(Extract entire, String extra) {
        super(entire, extra, 0, 0, entire.getWidth());
    }

    // for insert segment
    public EndnoteSegment(Extract entire) {
        super(entire, "", 0, 0, entire.getWidth());
    }

    private EndnoteSegment(Extract entire, String extra, int offset, int removed, int inserted) {
        super(entire, extra, offset, removed, inserted);
    }

    public Segment createSimilar(Extract entire, int offset, int removed, int inserted) {
        final String extra;

        extra = super.getExtra();

        return new EndnoteSegment(entire, extra, offset, removed, inserted);
    }

    public Segment createSimilar(String extra) {
        final Extract entire;

        entire = super.getEntire();

        return new ImageSegment(entire, extra, 0, 0, 0);
    }

    public boolean isSequenceAllowed() {
        return true;
    }
}
