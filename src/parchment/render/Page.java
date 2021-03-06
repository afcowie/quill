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
package parchment.render;

import java.util.ArrayList;

import org.freedesktop.cairo.Context;

/**
 * A list of Areas that can be rendered as (have been collected as?) a page.
 * 
 * @author Andrew Cowie
 */
/*
 * Should the flowing-into logic really be here?
 */
class Page
{
    private final ArrayList<Flow> areas;

    private final int pageNumber;

    Page(int num) {
        this.areas = new ArrayList<Flow>(64);
        this.pageNumber = num;
    }

    int getPageNumber() {
        return this.pageNumber;
    }

    /**
     * Add Area onto this Page. It's up to the RenderEngine to work out the
     * positioning.
     */
    void append(final double y, final Area area) {
        final Flow f;

        f = new Flow(y, area);
        areas.add(f);
    }

    /**
     * (vertical offset, Area) pairs.
     */
    private static class Flow
    {
        private final double y;

        private final Area area;

        private Flow(final double y, final Area area) {
            this.y = y;
            this.area = area;
        }

        private void render(final Context cr) {
            area.draw(cr, y);
        }
    }

    void render(final Context cr) {
        for (Flow f : areas) {
            f.render(cr);
        }
    }

    public String toString() {
        return "Page: # " + pageNumber;
    }
}
