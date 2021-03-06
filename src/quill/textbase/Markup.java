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

/**
 * Base class for indicating formatting and markup.
 * 
 * @author Andrew Cowie
 */
public abstract class Markup
{
    /*
     * This is for debugging; we can remove this down the track
     */
    private final String name;

    /**
     * Should text with this markup be spell checked?
     */
    private final boolean spellCheck;

    protected Markup(String name, boolean spellCheck) {
        this.name = name;
        this.spellCheck = spellCheck;
    }

    public String toString() {
        return this.name;
    }

    /*
     * TODO these shouldn't be public. Layering!
     */

    public static Markup[] applyMarkup(Markup[] original, Markup format) {
        final Markup[] replacement;
        final int len;

        if (original == null) {
            len = 0;
        } else {
            len = original.length;
        }

        if (contains(original, format)) {
            return original;
        }

        replacement = new Markup[len + 1];
        if (len > 0) {
            System.arraycopy(original, 0, replacement, 0, len);
        }

        replacement[len] = format;

        return replacement;
    }

    public static Markup[] removeMarkup(Markup[] original, Markup format) {
        final Markup[] replacement;
        final int len;
        int i;

        if (original == null) {
            return null;
        }

        if (!(contains(original, format))) {
            return original;
        }

        len = original.length - 1;

        if (len == 0) {
            return null;
        }

        replacement = new Markup[len];

        i = 0;
        for (Markup m : original) {
            if (m == format) {
                continue;
            }
            replacement[i++] = m;
        }

        return replacement;
    }

    /**
     * Does the given Markup[] contain the specified formatting?
     */
    static final boolean contains(Markup[] markup, Markup format) {
        if (markup == null) {
            return false;
        }
        for (Markup m : markup) {
            if (m == format) {
                return true;
            }
        }
        return false;
    }

    /**
     * Should a Span with this Markup be spell checked?
     */
    public boolean isSpellCheckable() {
        return spellCheck;
    }
}
