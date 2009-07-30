/*
 * Article.java
 *
 * Copyright (c) 2008-2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.docbook;

import nu.xom.Elements;

/**
 * An <code>&lt;article&gt;</code> (which is equivalent to a single
 * <code>&lt;chapter&gt;</code>).
 * 
 * @author Andrew Cowie
 */
public class Article extends StructureElement implements Component
{
    public Article() {
        super("article");
    }

    public void add(Division section) {
        super.add(section);
    }

    // TODO copied from Chapter; refactor up?
    public Division[] getDivisions() {
        final Elements sections;
        int i;
        final int num;
        final Division[] result;

        sections = super.getChildElements("section", "http://docbook.org/ns/docbook"); // ???

        num = sections.size();
        result = new Division[num];

        for (i = 0; i < num; i++) {
            result[i] = (Division) sections.get(i);
        }

        return result;
    }
}
