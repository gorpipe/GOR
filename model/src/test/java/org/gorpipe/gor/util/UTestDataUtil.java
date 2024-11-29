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

package org.gorpipe.gor.util;

import org.gorpipe.gor.driver.meta.DataType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for DataUtil.
 *
 * @author gummi
 * @version $Id$
 */
public class UTestDataUtil {

    @Test
    public void testDataTypes() {
        // Any CSV
        Assert.assertFalse(DataUtil.isAnyCsv("foo.txt"));
        Assert.assertTrue(DataUtil.isAnyCsv("foo.csv"));
        Assert.assertTrue(DataUtil.isAnyCsv("foo.csv.link"));
        Assert.assertTrue(DataUtil.isAnyCsv("foo.csv.gz"));

        // isMem
        Assert.assertFalse(DataUtil.isMem("foo.txt"));
        Assert.assertTrue(DataUtil.isMem("foo.mem"));
        Assert.assertFalse(DataUtil.isMem("foo.mem.link"));

        // isGord
        Assert.assertFalse(DataUtil.isGord("foo.txt"));
        Assert.assertTrue(DataUtil.isGord("foo.gord"));
        Assert.assertTrue(DataUtil.isGord("foo.gord.link"));

        // isGor
        Assert.assertFalse(DataUtil.isGor("foo.txt"));
        Assert.assertTrue(DataUtil.isGor("foo.gor"));
        Assert.assertTrue(DataUtil.isGor("foo.gor.link"));

        // isGorz
        Assert.assertFalse(DataUtil.isGorz("foo.txt"));
        Assert.assertTrue(DataUtil.isGorz("foo.gorz"));
        Assert.assertTrue(DataUtil.isGorz("foo.gorz.link"));

        // isNord
        Assert.assertFalse(DataUtil.isNord("foo.txt"));
        Assert.assertTrue(DataUtil.isNord("foo.nord"));
        Assert.assertTrue(DataUtil.isNord("foo.nord.link"));

        // isNor
        Assert.assertFalse(DataUtil.isNor("foo.txt"));
        Assert.assertTrue(DataUtil.isNor("foo.nor"));
        Assert.assertTrue(DataUtil.isNor("foo.nor.link"));

        // isNorz
        Assert.assertFalse(DataUtil.isNorz("foo.txt"));
        Assert.assertTrue(DataUtil.isNorz("foo.norz"));
        Assert.assertTrue(DataUtil.isNorz("foo.norz.link"));

        // isNorSource
        Assert.assertFalse(DataUtil.isNorSource("foo.txt"));
        Assert.assertFalse(DataUtil.isNorSource("foo.nord"));
        Assert.assertTrue(DataUtil.isNorSource("foo.nor"));
        Assert.assertTrue(DataUtil.isNorSource("foo.norz"));
        Assert.assertTrue(DataUtil.isNorSource("foo.tsv"));
        Assert.assertFalse(DataUtil.isNorSource("foo.txt.link"));
        Assert.assertFalse(DataUtil.isNorSource("foo.nord.link"));
        Assert.assertTrue(DataUtil.isNorSource("foo.nor.link"));
        Assert.assertTrue(DataUtil.isNorSource("foo.norz.link"));
        Assert.assertTrue(DataUtil.isNorSource("foo.tsv.link"));

        // isDictionary
        Assert.assertFalse(DataUtil.isDictionary("foo.txt"));
        Assert.assertTrue(DataUtil.isDictionary("foo.gord"));
        Assert.assertTrue(DataUtil.isDictionary("foo.gord.link"));
        Assert.assertFalse(DataUtil.isDictionary("foo.txt"));
        Assert.assertTrue(DataUtil.isDictionary("foo.nord"));
        Assert.assertTrue(DataUtil.isDictionary("foo.nord.link"));

        // isTxt
        Assert.assertFalse(DataUtil.isTxt("foo.gor"));
        Assert.assertTrue(DataUtil.isTxt("foo.txt"));
        Assert.assertTrue(DataUtil.isTxt("foo.txt.link"));

        // isLink
        Assert.assertFalse(DataUtil.isLink("foo.txt"));
        Assert.assertTrue(DataUtil.isLink("foo.txt.link"));
        Assert.assertTrue(DataUtil.isLink("foo.link"));

        // isMeta
        Assert.assertFalse(DataUtil.isMeta("foo.txt"));
        Assert.assertTrue(DataUtil.isMeta("foo.meta"));
        Assert.assertTrue(DataUtil.isMeta("foo.meta.link"));

        // isR
        Assert.assertFalse(DataUtil.isRScript("foo.txt"));
        Assert.assertTrue(DataUtil.isRScript("foo.R"));
        Assert.assertTrue(DataUtil.isRScript("foo.R.link"));

        // isShellScript
        Assert.assertFalse(DataUtil.isShellScript("foo.txt"));
        Assert.assertTrue(DataUtil.isShellScript("foo.sh"));
        Assert.assertTrue(DataUtil.isShellScript("foo.sh.link"));

        // isPythonScript
        Assert.assertFalse(DataUtil.isPythonScript("foo.txt"));
        Assert.assertTrue(DataUtil.isPythonScript("foo.py"));
        Assert.assertTrue(DataUtil.isPythonScript("foo.py.link"));

        // isYml
        Assert.assertFalse(DataUtil.isYml("foo.txt"));
        Assert.assertTrue(DataUtil.isYml("foo.yml"));
        Assert.assertTrue(DataUtil.isYml("foo.yml.link"));
        Assert.assertTrue(DataUtil.isYml("../tests/data/reports/test.yml()"));
        Assert.assertTrue(DataUtil.isYml("../tests/data/reports/test.yml(top=50)"));
        Assert.assertTrue(DataUtil.isYml("../tests/data/reports/test2.yml::TestReport(query2, top = 10)"));
        Assert.assertTrue(DataUtil.isYml("../tests/data/reports/test.yml?TestReport&query=echo&some=stuff"));
        Assert.assertFalse(DataUtil.isYml("foo.yml.gord"));
        Assert.assertFalse(DataUtil.isYml("foo.yml.gord.link"));
        Assert.assertFalse(DataUtil.isYml("foo.yml.nord"));
        Assert.assertFalse(DataUtil.isYml("foo.yml.nord.link"));

        // isGorq
        Assert.assertFalse(DataUtil.isGorq("foo.txt"));
        Assert.assertTrue(DataUtil.isGorq("foo.gorq"));
        Assert.assertTrue(DataUtil.isGorq("foo.gorq.link"));

        // isParquet
        Assert.assertFalse(DataUtil.isParquet("foo.txt"));
        Assert.assertTrue(DataUtil.isParquet("foo.parquet"));
        Assert.assertTrue(DataUtil.isParquet("foo.parquet.link"));

        // isBam
        Assert.assertFalse(DataUtil.isBam("foo.txt"));
        Assert.assertTrue(DataUtil.isBam("foo.bam"));
        Assert.assertTrue(DataUtil.isBam("foo.bam.link"));

        // isCram
        Assert.assertFalse(DataUtil.isCram("foo.txt"));
        Assert.assertTrue(DataUtil.isCram("foo.cram"));
        Assert.assertTrue(DataUtil.isCram("foo.cram.link"));

        // isBgen
        Assert.assertFalse(DataUtil.isBgen("foo.txt"));
        Assert.assertTrue(DataUtil.isBgen("foo.bgen"));
        Assert.assertTrue(DataUtil.isBgen("foo.bgen.link"));

        // isAnyVcf
        Assert.assertFalse(DataUtil.isAnyVcf("foo.txt"));
        Assert.assertTrue(DataUtil.isAnyVcf("foo.vcf"));
        Assert.assertTrue(DataUtil.isAnyVcf("foo.vcf.gz"));
        Assert.assertTrue(DataUtil.isAnyVcf("foo.vcf.bgz"));
        Assert.assertTrue(DataUtil.isAnyVcf("foo.vcf.link"));
        Assert.assertTrue(DataUtil.isAnyVcf("foo.vcf.gz.link"));
        Assert.assertTrue(DataUtil.isAnyVcf("foo.vcf.bgz.link"));

        // isGz
        Assert.assertFalse(DataUtil.isGZip("foo.txt"));
        Assert.assertTrue(DataUtil.isGZip("foo.cram.gz"));
        Assert.assertTrue(DataUtil.isGZip("foo.cram.gz.link"));
    }

    @Test
    public void testToFile() {
        Assert.assertEquals("foo.txt", DataUtil.toFile("foo", DataType.TXT));
        Assert.assertEquals("foo.meta", DataUtil.toFile("foo", DataType.META));

        // Complex
        Assert.assertEquals("aaa/bbb/c_c/foo.gor", DataUtil.toFile("aaa/bbb/c_c/foo", DataType.GOR));

        // Illegal characters
        Assert.assertEquals("aaa/bbb/c??**--.,.,.,c/foo.gor", DataUtil.toFile("aaa/bbb/c??**--.,.,.,c/foo", DataType.GOR));
    }

    @Test
    public void testToLinkFile() {
        Assert.assertEquals("foo.txt.link", DataUtil.toLinkFile("foo", DataType.TXT));
        Assert.assertEquals("foo.link.link", DataUtil.toLinkFile("foo", DataType.LINK));
    }

    @Test
    public void testToTempTempFile() {
        Assert.assertTrue(DataUtil.toTempTempFile("foo.txt").matches("foo\\..*\\.temptempfile\\.txt"));
    }
}
