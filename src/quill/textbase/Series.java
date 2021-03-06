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
package quill.textbase;

import java.util.List;

/**
 * A collection of Segments, comprising a visible section of a document. Like
 * other areas in textbase, this started lifea as a wrapper around an array,
 * but has evolved to handle various application specific use cases.
 * 
 * Series also has information about what changed from the previous Series, to
 * facilitate undo and redo.
 * 
 * @author Andrew Cowie
 */
// immutable
public class Series
{
    private final Segment[] segments;

    private final int updated;

    private final int added;

    private final int third;

    private final int deleted;

    public Series(List<Segment> segments) {
        final Segment[] result;

        result = new Segment[segments.size()];
        segments.toArray(result);

        this.segments = result;

        this.updated = -1;
        this.added = -1;
        this.third = -1;
        this.deleted = -1;
    }

    // for real
    private Series(Segment[] segments, int deleted, int updated, int added, int third) {
        this.segments = segments;

        this.deleted = deleted;
        this.updated = updated;
        this.added = added;
        this.third = third;
    }

    // for testing
    Series(Segment[] segments) {
        this.segments = segments;

        this.updated = -1;
        this.added = -1;
        this.third = -1;
        this.deleted = -1;
    }

    public int size() {
        return segments.length;
    }

    public Segment getSegment(int index) {
        return segments[index];
    }

    /**
     * Create a new Series by changing the Segment at position.
     */
    public Series update(int position, Segment segment) {
        final Segment[] original, replacement;

        original = this.segments;

        replacement = new Segment[original.length];

        System.arraycopy(original, 0, replacement, 0, position);
        replacement[position] = segment;
        System.arraycopy(original, position + 1, replacement, position + 1, original.length - position
                - 1);

        return new Series(replacement, -1, position, 0, 0);
    }

    /**
     * Grow the Series by inserting the given Segment at position.
     */
    public Series insert(int position, Segment segment) {
        final Segment[] original, replacement;

        original = this.segments;

        replacement = new Segment[original.length + 1];

        System.arraycopy(original, 0, replacement, 0, position);
        replacement[position] = segment;
        System.arraycopy(original, position, replacement, position + 1, original.length - position);

        return new Series(replacement, -1, -1, position, -1);
    }

    /**
     * Grow the Series by replacing the first Segment, inserting the added
     * Segment, and then following it with the second half of the original
     * Segment, third.
     */
    public Series splice(int position, Segment first, Segment added, Segment third) {
        final Segment[] original, replacement;

        if (third == null) {
            throw new AssertionError("Use insert() for the append case");
        }

        original = this.segments;
        replacement = new Segment[original.length + 2];

        System.arraycopy(original, 0, replacement, 0, position);
        replacement[position] = first;
        replacement[position + 1] = added;
        replacement[position + 2] = third;
        System.arraycopy(original, position + 1, replacement, position + 3, original.length - position
                - 1);

        return new Series(replacement, -1, position, position + 1, position + 2);
    }

    /**
     * Remove the Segment at the given position.
     */
    public Series delete(int position) {
        final Segment[] original, replacement;

        original = this.segments;

        replacement = new Segment[original.length - 1];

        System.arraycopy(original, 0, replacement, 0, position);
        System.arraycopy(original, position + 1, replacement, position, original.length - position - 1);

        return new Series(replacement, position, -1, -1, -1);
    }

    public int indexOf(Segment segment) {
        int i;

        for (i = 0; i < segments.length; i++) {
            if (segment == segments[i]) {
                return i;
            }
        }

        throw new IllegalArgumentException("\n" + "Segment not in this Series");
    }

    public int getIndexUpdated() {
        return updated;
    }

    public int getIndexAdded() {
        return added;
    }

    public int getIndexThird() {
        return third;
    }

    public int getIndexDeleted() {
        return deleted;
    }
}
