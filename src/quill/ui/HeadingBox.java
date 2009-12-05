/*
 * HeadingBox.java, from the Quill and Parchment document editor.
 *
 * Copyright © 2009 Operational Dynamics Consulting Pty Ltd
 *
 * The code in this file, and the program it is a part of, is made available
 * to you by its authors as open source software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License, version
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

import org.gnome.gtk.HBox;
import org.gnome.gtk.Label;

import quill.textbase.Segment;

class HeadingBox extends HBox
{
    private HBox box;

    protected HeadingEditorTextView title;

    protected Label label;

    public HeadingBox(Segment segment) {
        super(false, 0);

        setupBox(segment);
    }

    private void setupBox(Segment segment) {
        box = this;

        title = new HeadingEditorTextView(segment);
        box.packStart(title, true, true, 0);

        label = new Label();
        label.setWidthChars(20);
        box.packEnd(label, false, false, 0);
    }

    EditorTextView getEditor() {
        return title;
    }
}
