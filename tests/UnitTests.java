/*
 * Quill and Parchment, a WYSIWYN document editor and rendering engine. 
 *
 * Copyright © 2009-2010 Operational Dynamics Consulting, Pty Ltd
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

import junit.framework.Test;
import junit.framework.TestSuite;
import parchment.manuscript.ValidateFileNaming;
import parchment.manuscript.ValidateManuscriptLoading;
import parchment.manuscript.ValidateManuscriptSerializing;
import parchment.manuscript.ValidateThereAndBackAgain;
import parchment.quack.ValidateBlockquoteConversion;
import parchment.quack.ValidateCitationConversion;
import parchment.quack.ValidateDataIntegrity;
import parchment.quack.ValidateEndnoteConversion;
import parchment.quack.ValidateImageConversion;
import parchment.quack.ValidateListitemConversion;
import parchment.quack.ValidatePreformattedConversion;
import parchment.quack.ValidateProperNewlineHandling;
import parchment.quack.ValidateTextChainToChapterConversion;
import parchment.render.ValidateStylesheetToRenderSettingsConversion;
import parchment.render.ValidateTypographySubstitutions;
import quill.textbase.ValidateExtracts;
import quill.textbase.ValidateOriginOrdering;
import quill.textbase.ValidateSpanOperations;
import quill.textbase.ValidateText;
import quill.textbase.ValidateUnicode;
import quill.textbase.ValidateWordExtraction;
import quill.textbase.ValidateWrapperExpansions;
import quill.ui.ValidateApplyUndoRedo;
import quill.ui.ValidateChangePropagation;
import quill.ui.ValidateDocumentModified;
import quill.ui.ValidateSpellingOperations;

public class UnitTests
{
    /**
     * Entry point from the command line, of course. Uses VerboseTestRunner to
     * do a more pretty printing of the test output.
     */
    public static void main(String[] args) {
        VerboseTestRunner.run(suite(args));
    }

    /**
     * Entry point used by Eclipse's built in JUnit TestRunner
     */
    public static Test suite() {
        return suite(null);
    }

    /**
     * @param args
     *            Ignored
     */
    private static Test suite(String[] args) {
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);

        TestSuite suite = new TestSuite("Complete quill unit test suite");

        suite.addTestSuite(ValidateSpanOperations.class);
        suite.addTestSuite(ValidateText.class);
        suite.addTestSuite(ValidateUnicode.class);
        suite.addTestSuite(ValidateExtracts.class);
        suite.addTestSuite(ValidateOriginOrdering.class);
        suite.addTestSuite(ValidateDocumentModified.class);
        suite.addTestSuite(ValidateTypographySubstitutions.class);
        suite.addTestSuite(ValidateStylesheetToRenderSettingsConversion.class);
        suite.addTestSuite(ValidateManuscriptSerializing.class);
        suite.addTestSuite(ValidateManuscriptLoading.class);
        suite.addTestSuite(ValidateTextChainToChapterConversion.class);
        suite.addTestSuite(ValidateDataIntegrity.class);
        suite.addTestSuite(ValidateBlockquoteConversion.class);
        suite.addTestSuite(ValidateEndnoteConversion.class);
        suite.addTestSuite(ValidateListitemConversion.class);
        suite.addTestSuite(ValidateCitationConversion.class);
        suite.addTestSuite(ValidateImageConversion.class);
        suite.addTestSuite(ValidateProperNewlineHandling.class);
        suite.addTestSuite(ValidateWrapperExpansions.class);
        suite.addTestSuite(ValidateThereAndBackAgain.class);
        suite.addTestSuite(ValidatePreformattedConversion.class);
        suite.addTestSuite(ValidateFileNaming.class);
        suite.addTestSuite(ValidateWordExtraction.class);
        suite.addTestSuite(ValidateApplyUndoRedo.class);
        suite.addTestSuite(ValidateChangePropagation.class);
        suite.addTestSuite(ValidateSpellingOperations.class);

        return suite;
    }
}
