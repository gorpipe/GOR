/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package org.gorpipe.gor.model;

import gorsat.process.CLISessionFactory;
import gorsat.process.PipeOptions;
import org.gorpipe.gor.model.DefaultChromoLookup;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.model.SourceRef;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class UTestStdInGenomicIterator {
    private InputStream stdin;
    private final String defaultContents = "Chrom\tPos\tCategory\tValue\n" +
            "chr1\t1\t1\t1\n" +
            "chr1\t1\t2\t11\n" +
            "chr1\t1\t3\t111\n" +
            "chr1\t2\t1\t2\n" +
            "chr1\t2\t2\t22\n" +
            "chr1\t2\t3\t222";

    @Before
    public void setUp() throws IOException {
        stdin = System.in;
    }

    @After
    public void tearDown() {
        System.setIn(stdin);
    }

    @Test
    public void getHeader() throws IOException {
        try(GenomicIterator iterator = getIterator(defaultContents)) {
            final String header = iterator.getHeader();
            assertEquals("Chrom\tPos\tCategory\tValue", header);
        }
    }

    @Test
    public void getHeader_NoHeader() throws IOException {
        final String contents = "chr1\t1\t1\t1\n" +
                "chr1\t1\t2\t11\n" +
                "chr1\t1\t3\t111\n" +
                "chr1\t2\t1\t2\n" +
                "chr1\t2\t2\t22\n" +
                "chr1\t2\t3\t222";
        try(GenomicIterator iterator = getIterator(contents)) {
            final String header = iterator.getHeader();
            assertEquals("Chromo\tPos\tCol3\tCol4", header);
        }
    }

    @Test
    public void hasNext_ReturnsTrueAtStart() throws IOException {
        try(GenomicIterator iterator = getIterator(defaultContents)) {
            assertTrue(iterator.hasNext());
        }
    }

    @Test
    public void hasNext_ReturnsFalseAtStartWhenOnlyHeader() throws IOException {
        try(GenomicIterator iterator = getIterator("Chrom\tPos\tCategory\tValue\n")) {
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    @Ignore("Exception is thrown on empty input")
    public void hasNext_ReturnsFalseAtStartWhenEmptyInput() throws IOException {
        try(GenomicIterator iterator = getIterator("")) {
            assertFalse(iterator.hasNext());
        }
    }

    @Test
    public void next() throws IOException {
        try(GenomicIterator iterator = getIterator(defaultContents)) {
            int counter = 0;
            while(iterator.hasNext()) {
                iterator.next();
                counter++;
            }
            assertEquals(6, counter);
        }
    }

    @Test
    public void next_WhenNoHeader() throws IOException {
        final String contents = "chr1\t1\t1\t1\n" +
                "chr1\t1\t2\t11\n" +
                "chr1\t1\t3\t111\n" +
                "chr1\t2\t1\t2\n" +
                "chr1\t2\t2\t22\n" +
                "chr1\t2\t3\t222";
        int counter = 0;

        try(GenomicIterator iterator = getIterator(contents)) {
            while(iterator.hasNext()) {
                iterator.next();
                counter++;
            }
        }
        assertEquals(6, counter);
    }

    @Test
    public void seek_ShouldThrowError() {
        Assert.assertThrows(RuntimeException.class, () -> {
            try(GenomicIterator iterator = getIterator(defaultContents)) {
                iterator.seek("chr1", 1);
            }
        });
    }

    private GenomicIterator getIterator(String contents) throws IOException {
        System.setIn(new ByteArrayInputStream(contents.getBytes()));

        GenomicIterator iterator = SourceRef.STANDARD_IN.iterate(new DefaultChromoLookup(),
                new CLISessionFactory(new PipeOptions(), null).create());
        return iterator;
    }
}