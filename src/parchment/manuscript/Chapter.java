/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2010-2011 Operational Dynamics Consulting, Pty Ltd
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
package parchment.manuscript;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.NodeFactory;
import nu.xom.ParsingException;
import nu.xom.ValidityException;
import parchment.quack.QuackConverter;
import parchment.quack.QuackLoader;
import parchment.quack.QuackNodeFactory;
import quill.client.ImproperFilenameException;
import quill.textbase.ChapterSegment;
import quill.textbase.Component;
import quill.textbase.Extract;
import quill.textbase.NormalSegment;
import quill.textbase.Segment;
import quill.textbase.Series;

import static java.lang.String.format;

/**
 * A chapter on disk in an .xml file containing a &lt;chapter&gt; root
 * element.
 * 
 * This class is an intermediary, not an encapsulating container, which is why
 * save takes a Series as argument to be persisted.
 * 
 * @author Andrew Cowie
 */
public class Chapter
{
    /**
     * The Manuscript this Chapter belongs to. We allow this to be null for
     * tests?
     */
    private Manuscript parent;

    /**
     * Relative path to .xml, relative to the directory of the "parent"
     * Manuscript.
     */
    private String relative;

    public Chapter(Manuscript manuscript) {
        if (manuscript == null) {
            throw new IllegalArgumentException("Can't use a null Manuscript");
        }
        parent = manuscript;
    }

    public Component createDocument() {
        final Segment heading, para;
        Extract blank;
        List<Segment> list;
        final Series series, empty;
        final Component result;

        blank = Extract.create();
        heading = new ChapterSegment(blank);

        blank = Extract.create();
        para = new NormalSegment(blank);

        /*
         * Main body
         */

        list = new ArrayList<Segment>(2);
        list.add(heading);
        list.add(para);

        series = new Series(list);

        /*
         * Endnotes, References
         */

        list = new ArrayList<Segment>(0);
        empty = new Series(list);

        result = new Component(series, empty, empty);

        return result;
    }

    /**
     * Given this Chapter's current pathname, load and parse the document into
     * set of Segment[], wrapped as a Component.
     */
    public Component loadDocument() throws ValidityException, ParsingException, IOException {
        final String filename;
        final File source, probe;
        final NodeFactory factory;
        final Builder parser;
        final Document doc;
        final QuackLoader loader; // change to interface or baseclass
        final Component component;

        filename = this.getFilename();

        /*
         * Safety check. FIXME This is actually horrible; we should checking
         * for all the recovery files at once in Manuscript's checkFilename(),
         * but that implies doing some kind of recursive search for such files
         * [ugly] or already having loaded the .parchment file [also ugly].
         */

        probe = new File(filename + ".RESCUED");
        if (probe.exists()) {
            System.err.println("WARNING: " + "There's still a Chapter recovery file," + "\n"
                    + probe.getPath());
        }

        source = new File(filename);

        factory = new QuackNodeFactory();
        parser = new Builder(factory);
        doc = parser.build(source);

        loader = new QuackLoader();
        component = loader.process(doc);

        return component;
    }

    /**
     * Specify the filename that this chapter will be serialized to. Path will
     * be compared against the parent Manuscript's filepath, and if it's not
     * relative it had better be within that path.
     */
    public void setFilename(final String path) throws ImproperFilenameException {
        File proposed, absolute;
        final String name, directory, filename;
        final int i;
        int prefix;

        /*
         * Ideally we'd only get relative paths passed in, and we do when
         * loading a .parchment, but of who knows what a FileChooser will end
         * up giving us. So we check that and use the Manuscript's directory
         * if we were given something relative.
         */

        directory = parent.getDirectory();

        proposed = new File(path);
        if (proposed.isAbsolute()) {
            absolute = proposed;
        } else {
            proposed = new File(directory + "/" + path);
            absolute = proposed.getAbsoluteFile();
        }

        name = absolute.getName();

        i = name.indexOf(".xml");
        if (i == -1) {
            throw new ImproperFilenameException("\n" + "Chapter files must have a .xml extension");
        }

        filename = absolute.getPath();

        if (!filename.startsWith(directory)) {
            throw new ImproperFilenameException(
                    "Why isn't this Chapter's filename within the Manuscript's directory?");
        }

        prefix = directory.length();
        if (filename.length() <= prefix) {
            throw new IllegalStateException(
                    "Why is the (absolute) filename not longer than the directory it is in?");
        }

        /*
         * Need to add the trailing '/' character
         */
        relative = filename.substring(prefix + 1);
    }

    /**
     * You need to have set the filename first, of course.
     */
    public void saveDocument(final Component component) throws IOException {
        final String filename;
        final File target, tmp;
        final FileOutputStream out;
        boolean result;
        String dir, path;

        if (relative == null) {
            throw new IllegalStateException("save filename not set");
        }

        filename = this.getFilename();

        target = new File(filename);
        if (target.exists() && (!target.canWrite())) {
            throw new IOException("Can't write to document file!\n\n" + "<i>Check permissions?</i>");
        }

        /*
         * We need a temporary file to write to, since writing is descructive
         * and we don't want to blow away the existing file if something goes
         * wrong.
         */

        tmp = new File(filename + ".tmp");
        if (tmp.exists()) {
            tmp.delete();
        }
        result = tmp.createNewFile();
        if (!result) {
            dir = new File(".").getAbsolutePath();
            dir = dir.substring(0, dir.length() - 1);
            path = tmp.toString().substring(dir.length());
            throw new IOException("Can't create temporary file for saving.\n\n"
                    + "<i>Assuming all is well otherwise, remove</i>\n" + "<tt>" + path
                    + "</tt>\n<i>and try again?</i>");
        }

        try {
            out = new FileOutputStream(tmp);
            saveDocument(component, out);
            out.close();
        } catch (IOException ioe) {
            tmp.delete();
            throw ioe;
        }

        /*
         * And now replace the temp file over the actual document.
         */

        result = tmp.renameTo(target);
        if (!result) {
            tmp.delete();
            throw new IOException("Unbale to rename temporary file to target document!");
        }
    }

    public void saveDocument(final Component component, final OutputStream out) throws IOException {
        final QuackConverter converter;
        Series series;
        Segment segment;
        int i, I;

        /*
         * Create an output converter and run the segments through it to turn
         * them into DocBook elements.
         */

        converter = new QuackConverter();

        series = component.getSeriesMain();
        I = series.size();
        for (i = 0; i < I; i++) {
            segment = series.getSegment(i);
            converter.append(segment);
        }

        series = component.getSeriesEndnotes();
        I = series.size();
        for (i = 0; i < I; i++) {
            segment = series.getSegment(i);
            converter.append(segment);
        }

        series = component.getSeriesReferences();
        I = series.size();
        for (i = 0; i < I; i++) {
            segment = series.getSegment(i);
            converter.append(segment);
        }

        /*
         * Finally, serialize the resultant top level.
         */

        converter.writeChapter(out);
    }

    /**
     * Get the (relative) filename of this Chapter on disk.
     */
    public String getRelative() {
        return relative;
    }

    String getFilename() {
        final String directory;

        directory = parent.getDirectory();

        return directory + "/" + relative;
    }

    void emergencySave(Component component, PrintStream err, int index) {
        final String savename;
        File target = null;
        final OutputStream out;

        if (relative == null) {
            // gotta call it something
            relative = "Untitled" + format("%02d", index) + ".xml";
        }

        try {
            savename = getFilename() + ".RESCUED";
            target = new File(savename);

            err.println(savename);

            if (target.exists()) {
                err.println("Inhibited.");
                err.println("There's already a recovery file, and we're not going to overwrite it.");
                return;
            }

            out = new FileOutputStream(target);
            saveDocument(component, out);
            out.close();

        } catch (Throwable t) {
            // well, we tried
            err.println("Failed.");
            err.flush();

            if (target != null) {
                target.delete();
            }

            t.printStackTrace(err);
            return;
        }
    }
}
