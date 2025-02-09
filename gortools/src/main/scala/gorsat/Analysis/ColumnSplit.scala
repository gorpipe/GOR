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

package gorsat.Analysis

import gorsat.Commands.Analysis
import org.gorpipe.gor.model.Row
import org.gorpipe.model.gor.RowObj

/**
 * Implementation of the SPLIT command for a single column (not COLSPLIT)
 */
case class ColumnSplit(totalNumberOfColumns: Int, column: Int, separator: String) extends Analysis {

  private val isLast: Boolean = column+1 == totalNumberOfColumns

  override def isTypeInformationMaintained: Boolean = true

  /*
   * After split, the type of the split column should be re-inferred
   */
  override def columnsWithoutTypes(invalidOnInput: Array[Int]): Array[Int] = {
    if (invalidOnInput == null) Array(column)
      // the next line avoids possibility of duplication, which is harmless but may be confusing
    else if (invalidOnInput.contains(column)) invalidOnInput
    else invalidOnInput.concat(Array(column))
  }

  override def process(r: Row): Unit = {
    val colVals = r.colAsString(column).toString.split(separator, -1)
    if (colVals.length == 1) super.process(r)
    else {
      val prefix = r.colsSlice(0, column).toString
      val suffix = r.colsSlice(column + 1, totalNumberOfColumns).toString
      for (colValue <- colVals) {
        if (!isLast) super.process(RowObj(prefix + "\t" + colValue.trim + "\t" + suffix))
        else super.process(RowObj(prefix + "\t" + colValue.trim))
      }
    }
  }
}
