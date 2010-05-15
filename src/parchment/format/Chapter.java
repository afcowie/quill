/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2010 Operational Dynamics Consulting, Pty Ltd
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
package parchment.format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.NodeFactory;
import nu.xom.ParsingException;
import nu.xom.ValidityException;
import quill.quack.QuackConverter;
import quill.quack.QuackLoader;
import quill.quack.QuackNodeFactory;
import quill.textbase.Series;

/**
 * A chapter on disk in an .xml file containing a <chapter> root element.
 * 
 * This class is an intermediary, not an encapsulating container, which is why
 * save takes a Series as argument to be persisted.
 * 
 * @author Andrew Cowie
 */
public class Chapter
{
    private String filename;

    public Chapter() {

    }

    /**
     * Given this Chapter's current pathname, load and parse the document into
     * a Segment[], wrapped as a Series.
     */
    public Series loadDocument() throws ValidityException, ParsingException, IOException {
        final File source;
        final NodeFactory factory;
        final Builder parser;
        final Document doc;
        final QuackLoader loader; // change to interface or baseclass
        final Series series;

        source = new File(filename);

        factory = new QuackNodeFactory();
        parser = new Builder(factory);
        doc = parser.build(source);

        loader = new QuackLoader();
        series = loader.process(doc);

        return series;
    }

    /**
     * Specify the filename that this chapter will be serialized to. Path will
     * be stored as absolute if it isn't already.
     */
    void setFilename(String path) {
        File proposed, absolute;
        final String name;
        final int i;

        proposed = new File(path);
        if (proposed.isAbsolute()) {
            absolute = proposed;
        } else {
            absolute = proposed.getAbsoluteFile();
        }

        name = absolute.getName();

        i = name.indexOf(".xml");
        if (i == -1) {
            throw new IllegalArgumentException("\n" + "Chapter files must have a .xml extension");
        }

        filename = absolute.getPath();
    }

    /**
     * You need to have set the filename first, of course.
     */
    public void saveDocument(final Series series) throws IOException {
        final File target, tmp;
        final FileOutputStream out;
        boolean result;
        String dir, path;

        if (filename == null) {
            throw new IllegalStateException("save filename not set");
        }

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
            saveDocument(series, out);
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

    void saveDocument(final Series series, final OutputStream out) throws IOException {
        final QuackConverter converter;
        int i;

        /*
         * Create an output converter and run the segments through it to turn
         * them into DocBook elements.
         */

        converter = new QuackConverter();

        for (i = 0; i < series.size(); i++) {
            converter.append(series.get(i));
        }

        /*
         * Finally, serialize the resultant top level.
         */

        converter.writeChapter(out);
    }
}
