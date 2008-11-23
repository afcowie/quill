/*
 * Text.java
 *
 * Copyright (c) 2008 Operational Dynamics Consulting Pty Ltd
 * 
 * The code in this file, and the program it is a part of, are made available
 * to you by its authors under the terms of the "GNU General Public Licence,
 * version 2" See the LICENCE file for the terms governing usage and
 * redistribution.
 */
package com.operationaldynamics.textbase;

import java.util.Arrays;

public class Text
{
    Chunk[] chunks;

    Text(String str) {
        chunks = new Chunk[1];
        chunks[0] = new Chunk(str);
    }

    public String toString() {
        StringBuilder str;

        str = new StringBuilder();

        for (Chunk chunk : chunks) {
            str.append(chunk.text, chunk.offset, chunk.length);
        }

        return str.toString();
    }

    public void append(Chunk chunk) {
        final Chunk[] next;
        final int i;

        if (chunk == null) {
            throw new IllegalArgumentException();
        }

        i = chunks.length;
        next = Arrays.copyOf(chunks, i + 1);
        next[i] = chunk;

        chunks = next;
    }
}
