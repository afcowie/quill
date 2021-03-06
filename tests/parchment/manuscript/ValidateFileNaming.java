/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2009-2011 Operational Dynamics Consulting, Pty Ltd
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
import java.io.IOException;

import nu.xom.ParsingException;
import nu.xom.ValidityException;
import quill.client.IOTestCase;
import quill.client.ImproperFilenameException;
import quill.textbase.Component;
import quill.textbase.Folio;

public class ValidateFileNaming extends IOTestCase
{
    public final void testManuscriptName() throws ValidityException, ParsingException, IOException {
        final Manuscript manuscript;
        final Folio folio;
        final File target;
        final String parentdir, basename, filename;

        manuscript = new Manuscript();
        folio = manuscript.createDocument();

        assertNotNull(folio);
        assertEquals("Untitled", manuscript.getBasename());

        target = new File("tmp/unittests/parchment/format/ValidateFileNaming.parchment");
        target.delete();
        assertFalse(target.exists());
        target.getParentFile().mkdirs();

        try {
            manuscript.setFilename(target.getPath());
        } catch (ImproperFilenameException e) {
            fail("Shouldn't have thrown");
        }

        parentdir = manuscript.getDirectory();
        basename = manuscript.getBasename();
        filename = manuscript.getFilename();

        assertTrue(parentdir.endsWith("tmp/unittests/parchment/format"));
        assertEquals("ValidateFileNaming", basename);
        assertEquals(parentdir + "/" + basename + ".parchment", filename);
    }

    public final void testChapterName() throws IOException {
        final Manuscript manuscript;
        final Chapter chapter;
        final Component component;
        final File target;

        manuscript = new Manuscript();
        chapter = new Chapter(manuscript);
        component = chapter.createDocument();

        try {
            chapter.saveDocument(component);
            fail("Lack of name not trapped");
        } catch (IllegalStateException ise) {
            // good
        }

        try {
            chapter.setFilename("something.other");
            fail("Should have rejected improper filename");
        } catch (ImproperFilenameException e) {
            // good
        }

        try {
            manuscript.setFilename("fake.parchment");
        } catch (ImproperFilenameException e) {
            fail("Fake; should have been ok");
        }

        target = new File("tmp/unittests/parchment/format/chapter01.xml");
        target.delete();
        assertFalse(target.exists());
        target.getParentFile().mkdirs();

        try {
            chapter.setFilename(target.getPath());
        } catch (ImproperFilenameException e) {
            fail("Shouldn't have thrown\n" + e);
        }
    }

    public final void testChapterRelative() throws IOException {
        final Manuscript manuscript;
        final Folio folio;
        final Chapter chapter, another;
        final String relative, second;

        manuscript = new Manuscript();
        chapter = new Chapter(manuscript);

        try {
            manuscript.setFilename("tmp/unittests/parchment/format/ValidateFileNaming.parchment");
        } catch (ImproperFilenameException ife) {
            fail(ife.getMessage());
        }

        try {
            chapter.setFilename("relative.xml");
        } catch (ImproperFilenameException ife) {
            fail(ife.getMessage());
        }

        folio = manuscript.createDocument();
        another = folio.getChapter(0);
        relative = another.getRelative();
        assertEquals("Chapter1.xml", relative);

        try {
            another.setFilename("tmp/unittests/parchment/format/relative.xml");
        } catch (ImproperFilenameException ife) {
            fail("Should have been ok");
        }
        second = another.getRelative();
        assertEquals("tmp/unittests/parchment/format/relative.xml", second);
    }

    // TODO
    public final void testThroughChapter() throws ValidityException, ParsingException, IOException,
            ImproperFilenameException {
        final Manuscript manuscript;
        final Folio folio;
        final File dotParchment, dotXml;

        manuscript = new Manuscript();
        folio = manuscript.createDocument();

        dotParchment = new File("tmp/unittests/parchment/format/ValidateFileNaming.parchment");
        dotParchment.delete();
        assertFalse(dotParchment.exists());

        dotXml = new File("tmp/unittests/parchment/format/Chapter1.xml");
        dotXml.delete();
        assertFalse(dotXml.exists());

        manuscript.setFilename(dotParchment.getPath());
        manuscript.saveDocument(folio);

        assertTrue("Save didn't write the expected .parchment file!", dotParchment.exists());
        assertTrue("Save didn't write anything to the .parchment file!", dotParchment.length() > 0);
        assertTrue("Save didn't write the expected .xml file!", dotXml.exists());
        assertTrue("Save didn't write anything to the .xml file!", dotXml.length() > 0);
    }
}
