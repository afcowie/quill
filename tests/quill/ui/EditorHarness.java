/*
 * EditorHarness.java
 *
 * Copyright (c) 2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package quill.ui;

import java.io.File;
import java.io.IOException;

import nu.xom.ParsingException;
import nu.xom.ValidityException;

import org.gnome.gdk.Event;
import org.gnome.gtk.Gtk;
import org.gnome.gtk.PolicyType;
import org.gnome.gtk.ScrolledWindow;
import org.gnome.gtk.Widget;
import org.gnome.gtk.Window;
import org.gnome.gtk.Window.DeleteEvent;

import quill.converter.DocBookLoader;
import quill.textbase.Segment;
import quill.ui.EditorWidget;

public final class EditorHarness
{
    public static void main(String[] args) throws ValidityException, ParsingException, IOException {
        final Window window;
        final ScrolledWindow scroll;
        final EditorWidget editor;
        final File target;
        final DocBookLoader loader;
        final Segment[] segments;

        Gtk.init(args);

        window = new Window();

        editor = new EditorWidget();

        scroll = new ScrolledWindow();
        scroll.setPolicy(PolicyType.NEVER, PolicyType.ALWAYS);
        scroll.add(editor);
        window.add(scroll);
        window.setTitle("EditorWidget");
        window.setDefaultSize(400, 300);
        window.showAll();

        window.connect(new DeleteEvent() {
            public boolean onDeleteEvent(Widget source, Event event) {
                Gtk.mainQuit();
                return false;
            }
        });

        target = new File("tests/markerpen/converter/ExampleProgram.xml");

        loader = new DocBookLoader(target);
        segments = loader.parseTree();
        editor.loadText(segments[0].getText());

        Gtk.main();
    }
}