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
package parchment.manuscript;

import quill.client.ApplicationException;

/**
 * The class specified as RenderEngine was not found. This is as a result of
 * ClassNotFoundException, but is both less serious (in that it could
 * reasonably happen if you are trying to load someone else's document) and
 * more serious in that we need to do something realistic about this
 * situation) requiring dedicated UI for this case.
 * 
 * @author Andrew Cowie
 */
@SuppressWarnings("serial")
public class RendererNotFoundException extends ApplicationException
{
    public RendererNotFoundException(String rendererClass) {
        super(rendererClass);
    }

}
