/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2008-2010 Operational Dynamics Consulting, Pty Ltd
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
package parchment.quack;

import nu.xom.Attribute;
import nu.xom.Element;

/**
 * Internal base class for all Quack format tags.
 * 
 * @author Andrew Cowie
 */
abstract class QuackElement extends Element
{
    QuackElement(String name) {
        super(name, "http://namespace.operationaldynamics.com/parchment/5.0");
    }

    void add(Tag tag) {
        super.appendChild((QuackElement) tag);
    }

    void add(String str) {
        super.appendChild(str);
    }

    void add(Meta data) {
        super.addAttribute((QuackAttribute) data);
    }

    void setValue(String name, String value) {
        super.addAttribute(new Attribute(name, value));
    }

    String getValue(String name) {
        final Attribute a;

        a = super.getAttribute(name);

        return a.getValue();
    }

    /*
     * Unused by everything... except ImageElement
     */
    public Meta[] getMeta() {
        return null;
    }

    /**
     * Set the <code>xml:space="preserve"</code> Attribute on an Element.
     */
    /*
     * How nasty is this expression! No kidding we wrap it up.
     */
    void setPreserveWhitespace() {
        super.addAttribute(new Attribute("xml:space", "http://www.w3.org/XML/1998/namespace", "preserve"));
    }

    public String toString() {
        return "<" + getQualifiedName() + ">";
    }
}
