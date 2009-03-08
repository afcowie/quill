/*
 * Bold.java
 *
 * Copyright (c) 2008 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.docbook;

/**
 * Emphasis to be given bold formatting.
 * 
 * @author Andrew Cowie
 */
public class Bold extends Inline
{
    public Bold() {
        super("emphasis");
        super.setAttribute("role", "bold");
    }

    public Bold(String str) {
        this();
        super.addText(str);
    }
}