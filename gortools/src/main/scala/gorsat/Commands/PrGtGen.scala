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

package gorsat.Commands

import gorsat.Analysis.PrGtGenAnalysis.{AFANSourceAnalysis, LeftSourceAnalysis, RightSourceAnalysis}
import gorsat.Commands.CommandParseUtilities._
import gorsat.DynIterator.DynamicNorSource
import gorsat.IteratorUtilities
import gorsat.process.SourceProvider
import org.gorpipe.exceptions.GorParsingException
import org.gorpipe.gor.GorContext
import org.gorpipe.model.gor.iterators.RowSource

class PrGtGen extends CommandInfo("PRGTGEN",
  CommandArguments("", "-tag -gl -gp -gc -maxseg -e -afc -af -cc -crc -ld -rd -anc -th -psep -osep -maxit -tol", 2, 3),
  CommandOptions(gorCommand = true, cancelCommand = true))
{
  override def processArguments(context: GorContext, argString: String, iargs: Array[String], args: Array[String], executeNor: Boolean, forcedInputHeader: String): CommandParsingResult = {

    val leftHeader = forcedInputHeader

    val lhl = leftHeader.toLowerCase
    val leftCols = lhl.split('\t')

    val PNCol = if (hasOption(args, "-tag")) {
      val pnCols: List[Int] = columnsOfOptionWithNil(args, "-tag", leftHeader, executeNor)
      if (pnCols == Nil || pnCols.length > 1) {
        throw new GorParsingException("Illegal -tag option: " +
          stringValueOfOption(args, "-tag") + "\n\nPlease specify a single column for -tag.")
      }
      pnCols.head
    } else {
      columnFromHeader("pn", lhl, executeNor)
    }

    val gcCols: List[Int] = columnsOfOptionWithNil(args, "-gc", leftHeader, executeNor).distinct

    val gl = if (hasOption(args, "-gl")) {
      columnOfOption(args, "-gl", leftHeader, executeNor)
    } else leftCols.indexOf("gl")

    val gp = if (hasOption(args, "-gp")) {
      columnOfOption(args, "-gp", leftHeader, executeNor)
    } else leftCols.indexOf("gp")

    val cc = if (hasOption(args, "-cc")) {
      columnOfOption(args, "-gp", leftHeader, executeNor)
    } else leftCols.indexOf("callcopies")

    val crc = if (hasOption(args, "-crc")) {
      columnOfOption(args, "-crc", leftHeader, executeNor)
    } else leftCols.indexOf("callratio")

    val ld = if (hasOption(args, "-ld")) {
      columnOfOption(args, "-ld", leftHeader, executeNor)
    } else leftCols.indexOf("depth")

    //Validate options
    val gpAmbiguous = gp != -1 && !(gl == -1 && cc == -1 && crc == -1 && ld == -1)
    val glCcAmbiguous = (gl != -1 && cc != -1) && !(gp == -1 && crc == -1 && ld == -1)
    val glAmbiguous = (gl != -1) && !(cc == -1 && gp == -1 && crc == -1 && ld == -1)
    val cdAmbiguous = (crc != -1 && ld != -1) && !(gp == -1 && gl == -1 && cc == -1)

    if (gpAmbiguous || glCcAmbiguous || glAmbiguous || cdAmbiguous) {
      throw new GorParsingException("Ambiguous parameters or header names. Please read the manual.")
    }

    val e = doubleValueOfOptionWithDefault(args, "-e", 0.0)

    val af = doubleValueOfOptionWithDefault(args, "-af", 0.05)

    val maxIt = intValueOfOptionWithDefault(args, "-maxit", 20)
    val tol = doubleValueOfOptionWithDefault(args, "-tol", 1e-5)

    val threshold = doubleValueOfOptionWithDefault(args, "-th", -1)

    val pSep = if (hasOption(args, "-psep")) charValueOfOption(args, "-psep") else ';'
    val writeOutTriplets = hasOption(args, "-osep")
    val oSep = if (writeOutTriplets) charValueOfOption(args, "-osep") else ','

    var buckTagItCommand = ""
    var buckTagDNS: DynamicNorSource = null
    var buckTagHeader = ""
    val buckTagFile = {
      val cand = iargs.head
      val cl = cand.toUpperCase
      if ((cl.endsWith(".NORZ") || cl.endsWith(".TSV") || cl.endsWith(".NOR")) && !(cl.slice(0, 2) == "<(")) "<(nor " + cand + " )" else cand
    }

    try {
      val buckTagSource = SourceProvider(buckTagFile, context, executeNor = executeNor, isNor = true)
      buckTagItCommand = buckTagSource.iteratorCommand
      buckTagDNS = buckTagSource.dynSource.asInstanceOf[DynamicNorSource]
      buckTagHeader = buckTagSource.header
    } catch {
      case e: Exception =>
        if (buckTagDNS != null) buckTagDNS.close()
        throw e
    }

    if (buckTagHeader.split("\t").length != 2) {
      throw new GorParsingException("buckettagfile must have 2 tab-delimited columns: Tag/PN (distinct), bucketID.\nThe relative position of tag in bucket specifies the csv order.\nCurrent header is: $buckTagHeader")
    }

    val priorFile: String = iargs(1).trim
    var priorSource: RowSource = null
    var priorHeader = ""
    if (iargs.length == 3) {
      try {
        val inputSource = new SourceProvider(priorFile, context, executeNor = executeNor, isNor = false)
        priorSource = inputSource.source
        priorHeader = inputSource.header
      } catch {
        case ex: Exception =>
          if (priorSource != null) priorSource.close()
          throw ex
      }
    }

    val afc = if (iargs.length == 3) {
      if (hasOption(args, "-afc")) intValueOfOption(args, "-afc") else columnFromHeader("AF", priorHeader, false)
    } else -1
    val anc = if (iargs.length == 3) {
      if (hasOption(args, "-anc")) intValueOfOption(args, "-anc") else columnFromHeader("AN", priorHeader, false)
    } else -1

    val segFile: String = iargs.last.trim
    var segSource: RowSource = null
    var rightHeader = ""
    try {
      val inputSource = new SourceProvider(segFile, context, executeNor = executeNor, isNor = false)
      segSource = inputSource.source
      rightHeader = inputSource.header
    } catch {
      case e: Exception =>
        if (segSource != null) segSource.close()
        throw e
    }
    val PNCol2 = if (hasOption(args, "-tag")) PNCol else columnFromHeader("pn", rightHeader.toLowerCase, executeNor)

    val rdIdx = if (hasOption(args, "-rd")) {
      intValueOfOption(args, "-rd")
    } else {
      columnFromHeader("depth", rightHeader.toLowerCase, executeNor)
    }

    val hcol = leftHeader.split("\t")
    val outputHeader = hcol.slice(0, 2).mkString("\t") + (if (gcCols.nonEmpty) "\t" + gcCols.map(hcol(_)).mkString("\t") else "") + "\tAF\tBucket\tValues"

    var maxSegSize = 10000
    if (hasOption(args, "-maxseg")) maxSegSize = intValueOfOption(args, "-maxseg")
    val lookupSignature = buckTagFile + "#" + buckTagItCommand + "#" + segFile

    val combinedHeader = IteratorUtilities.validHeader(outputHeader)
    val pipeStep: Analysis =
      if (iargs.length == 3) {
        LeftSourceAnalysis(context, lookupSignature, buckTagFile, buckTagItCommand, buckTagDNS, gl, gp, cc, crc, ld, PNCol, gcCols, af, e, tripSep = pSep) |
          AFANSourceAnalysis(priorSource, context, lookupSignature, gcCols, afc, anc) |
          RightSourceAnalysis(segSource, context, lookupSignature, rdIdx, PNCol2, threshold, maxSegSize, maxIt, tol, sepOut = writeOutTriplets, outSep = oSep)
      } else {
        LeftSourceAnalysis(context, lookupSignature, buckTagFile, buckTagItCommand, buckTagDNS, gl, gp, cc, crc, ld, PNCol, gcCols, af, e, tripSep = pSep) |
          RightSourceAnalysis(segSource, context, lookupSignature, rdIdx, PNCol2, threshold, maxSegSize, maxIt, tol, sepOut = writeOutTriplets, outSep = oSep)
      }
    CommandParsingResult(pipeStep, combinedHeader)
  }
}
