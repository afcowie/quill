/*
 * Italics.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.quack;

/**
 * Italic text.
 * 
 * @author Andrew Cowie
 */
public class Italics extends InlineElement implements Inline
{
    public Italics() {
        super("italics");
    }
}
