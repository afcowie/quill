/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2009 Operational Dynamics Consulting, Pty Ltd
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

import parchment.format.Chapter;

/**
 * A collection of Segments, comprising a visible section of a document. Like
 * other areas in textbase, is is a wrapper around an array.
 * 
 * @author Andrew Cowie
 */
// immutable
public class Series
{
    private final Chapter chapter;

    private final Segment[] segments;

    public Series(Chapter chapter, List<Segment> segments) {
        Segment[] result;
        int i;

        this.chapter = chapter;

        result = new Segment[segments.size()];
        segments.toArray(result);

        for (i = 0; i < result.length; i++) {
            result[i].setParent(this);
        }

        this.segments = result;
    }

    Series(Chapter chapter, Segment[] segments) {
        int i;

        this.chapter = chapter;

        for (i = 0; i < segments.length; i++) {
            segments[i].setParent(this);
        }

        this.segments = segments;
    }

    public int size() {
        return segments.length;
    }

    public Segment get(int index) {
        return segments[index];
    }

    /**
     * Get the [reference to] the Chapter on disk.
     */
    public Chapter getChapter() {
        return chapter;
    }

    /**
     * Update the Series with the given Segment inserted at position.
     */
    static Series insert(Series series, int position, Segment segment) {
        final Segment[] original, replacement;

        original = series.segments;

        replacement = new Segment[original.length + 1];

        System.arraycopy(original, 0, replacement, 0, position);
        replacement[position] = segment;
        System.arraycopy(original, position, replacement, position + 1, original.length - position);

        return new Series(series.chapter, replacement);
    }

    /**
     * Remove the Segment at the given position.
     */
    static Series delete(Series series, int position) {
        final Segment[] original, replacement;

        original = series.segments;

        replacement = new Segment[original.length - 1];

        System.arraycopy(original, 0, replacement, 0, position);
        System.arraycopy(original, position + 1, replacement, position, original.length - position - 1);

        return new Series(series.chapter, replacement);
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
}
