/*
 * BlockquoteEditorTextView.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.ui;

import quill.textbase.Segment;

class BlockquoteEditorTextView extends EditorTextView
{
    public BlockquoteEditorTextView(Segment segment) {
        super(segment);

        view.modifyFont(fonts.serif);
        view.setPaddingBelowParagraph(0); // TODO verify
        view.setLeftMargin(40);
    }
}