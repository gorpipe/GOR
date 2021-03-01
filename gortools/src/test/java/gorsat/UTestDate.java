/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2021 WuXi NextCode Inc.
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

package gorsat;

import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * Created by Gunnar on 13/06/2017.
 */
public class UTestDate {

    @Test
    public void testDateNoParam() {
        String result = TestUtils.getCalculated("date()");
        Assert.assertTrue(Pattern.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", result));
    }

    @Test
    public void testDateOneParam() {
        String result = TestUtils.getCalculated("date('dd/MM/yyyy')");
        Assert.assertTrue(Pattern.matches("\\d{2}/\\d{2}/\\d{4}", result));

        result = TestUtils.getCalculated("date('HH:mm:ss dd.MM.yy')");
        Assert.assertTrue(Pattern.matches("\\d{2}:\\d{2}:\\d{2} \\d{2}\\.\\d{2}\\.\\d{2}", result));
    }

    @Test
    public void testEdateOneParam() {

        long testLongJuly = 1497359194000L;
        long testLongDecember = 1514764799000L;
        long testLongJanuary= 1514764800000L;

        TestUtils.assertCalculated("edate("+ testLongJuly +")", "2017-06-13 13:06:34");
        TestUtils.assertCalculated("edate("+ testLongDecember + ")", "2017-12-31 23:59:59");
        TestUtils.assertCalculated("edate("+ testLongJanuary + ")", "2018-01-01 00:00:00");
    }

    @Test
    public void testEdateTwoParam() {

        long testLongJuly = 1497359194000L;
        long testLongDecember = 1514764799000L;
        long testLongJanuary= 1514764800000L;

        TestUtils.assertCalculated("edate("+ testLongJuly +", 'dd/MM/yyyy')", "13/06/2017");
        TestUtils.assertCalculated("edate("+ testLongDecember + ", 'dd.MM.yy')", "31.12.17");
        TestUtils.assertCalculated("edate("+ testLongJanuary + ", 'yyyy-MM-dd HH:mm:ss')", "2018-01-01 00:00:00");
    }

    @Test
    public void testEpochNoParam() {
        long result = Long.parseLong(TestUtils.getCalculated("epoch()"));
        long now = new Date().getTime();
        Assert.assertTrue(now >= result);
        Assert.assertTrue(now < result + 10000);
    }

    @Test
    public void testEpochTwoParam() {
        TestUtils.assertCalculatedLong("epoch('2017-06-13 13:06:34', 'yyyy-MM-dd HH:mm:ss')", 1497359194000L);
        TestUtils.assertCalculatedLong("epoch('23:59:59 31/12/2017', 'HH:mm:ss dd/MM/yyyy')", 1514764799000L);
        TestUtils.assertCalculatedLong("epoch('01.01.18', 'dd.MM.yy')", 1514764800000L);
    }
}
