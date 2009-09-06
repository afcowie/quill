/*
 * TextOutput.java
 * 
 * Copyright (c) 2005-2009 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the suite it is a part of, are made available
 * to you by the authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 *
 * This class imported from ObjectiveAccounts accounting package where it was
 * originally deployed as GPL code in generic.ui.TextOutput
 */

import java.io.PrintStream;
import java.io.PrintWriter;

import org.freedesktop.bindings.Environment;

/**
 * Base class for routines that output text to terminal. It takes care of
 * working out and making available to subclasses an appropriate terminal
 * width. Note that this can be set or overridden on the VM command line by
 * setting the COLUMNS property:
 * 
 * <pre>
 *  java -DCOLUMNS=70 ...
 * </pre>
 * 
 * @author Andrew Cowie
 */
public abstract class TextOutput extends Text
{
    /**
     * Terminal width, in character cells. Pulled from environment variable
     * "COLUMNS" if it set, otherwise a default value is used.
     */
    public static final int COLUMNS;

    private static final int COLUMNS_DEFAULT = 80;

    protected static final int COLUMNS_MIN = 50;

    static {
        String env = Environment.getEnv("COLUMNS");
        if (env == null) {
            COLUMNS = COLUMNS_DEFAULT;
        } else {
            int val = Integer.valueOf(env).intValue();
            if (val < COLUMNS_MIN) {
                throw new IllegalStateException("Terminal too narrow for TextOutput. Min width "
                        + COLUMNS_MIN + " characters.");
            }
            COLUMNS = val;
        }
    }

    /**
     * Wrapper around toOutput(PrintWriter) to which you can easily pass an
     * old school PrintStream
     * 
     * @param out
     *            a PrintStream like System.out or System.err
     */
    public final void toOutput(PrintStream out) {
        toOutput(new PrintWriter(out, true));

    }

    public abstract void toOutput(PrintWriter out);
}
