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

import gorsat.TestUtils;
import org.gorpipe.gor.model.BoundedIterator;
import org.gorpipe.gor.model.GenomicIterator;
import org.gorpipe.gor.model.GorOptions;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class UTestBoundedIterator {

    @Test
    public void hasNextReturnsTrueWhenBoundedOnFirstChromosome() {
        BoundedIterator bit = getChromBoundedIterator("-p chr1 ../tests/data/gor/dbsnp_test.gor");
        assertTrue(bit.hasNext());
    }

    @Test
    public void hasNextReturnsTrueWhenBoundedOnFirstChromosomeAndPosition() {
        BoundedIterator bit = getChromBoundedIterator("-p chr1:10179-10179 ../tests/data/gor/dbsnp_test.gor");
        assertTrue(bit.hasNext());
    }

    @Test
    public void nextFindsProperRowsWhenBoundedOnFirstChromosome() {
        BoundedIterator bit = getChromBoundedIterator("-p chr1 ../tests/data/gor/dbsnp_test.gor");
        assertEquals(2, countRows(bit));
    }

    @Test
    public void nextFindsProperRowsWhenBoundedOnFirstChromosomeAndPosition() {
        BoundedIterator bit = getChromBoundedIterator("-p chr1:10179-10179 ../tests/data/gor/dbsnp_test.gor");
        assertEquals(1, countRows(bit));
    }

    @Test
    public void nextFindsProperRowsWhenBoundedOnSomeChromosome() {
        BoundedIterator bit = getChromBoundedIterator("-p chr13 ../tests/data/gor/dbsnp_test.gor");
        assertEquals(2, countRows(bit));
    }

    @Test
    public void nextFindsProperRowsWhenBoundedOnSomeChromosomeAndPosition() {
        BoundedIterator bit = getChromBoundedIterator("-p chr13:19020145-19020145 ../tests/data/gor/dbsnp_test.gor");
        assertEquals(1, countRows(bit));
    }

    @Test
    public void chainingPgorStatements() {
        String script = "create ##first## = pgor ../tests/data/gor/genes.gor | signature -timeres 1;" +
                "create ##second## = pgor [##first##] | signature -timeres 1;" +
                "gor [##second##]";

        int expected = TestUtils.runGorPipeCount("gor ../tests/data/gor/genes.gor");
        int numLines = TestUtils.runGorPipeCount(script);
        assertEquals(expected, numLines);
    }

    @Test
    public void seekInsideRange() {
        BoundedIterator bit = getChromBoundedIterator("-p chr2 ../tests/data/gor/dbsnp_test.gor");
        bit.seek("chr2", 1);
        assertEquals(2, countRows(bit));
    }

    @Test
    public void seekBeforeRange() {
        BoundedIterator bit = getChromBoundedIterator("-p chr2 ../tests/data/gor/dbsnp_test.gor");
        bit.seek("chr1", 1);
        assertEquals(2, countRows(bit));
    }

    @Test
    public void seekAfterRange() {
        BoundedIterator bit = getChromBoundedIterator("-p chr2 ../tests/data/gor/dbsnp_test.gor");
        bit.seek("chr3", 1);
        assertFalse(bit.hasNext());
    }

    private int countRows(BoundedIterator bit) {
        int counter = 0;
        while(bit.hasNext()) {
            bit.next();
            counter++;
        }
        return counter;
    }

    private BoundedIterator getChromBoundedIterator(String query) {
        GorOptions gorOptions = GorOptions.createGorOptions(query);
        GenomicIterator it = null;
        try {
            it = gorOptions.files.get(0).iterate(null,  gorOptions.getSession());
        } catch (IOException e) {
            Assert.fail("Couldn't create iterator");
        }
        it.init(gorOptions.getSession());

        return new BoundedIterator(it, gorOptions.chrname, gorOptions.begin, gorOptions.end);
    }
}