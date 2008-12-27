/*
 * Markerpen.java
 *
 * Copyright (c) 2008 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package markerpen.ui;

import org.gnome.gtk.Gtk;

public class Markerpen
{
    public static void main(String[] args) {
        Gtk.init(args);

        new EditorWindow();

        Gtk.main();
    }
}