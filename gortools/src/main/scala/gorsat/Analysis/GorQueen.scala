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

import gorsat.Commands.{Analysis, BinAggregator, BinAnalysis, BinFactory, BinInfo, BinState, Processor, RegularRowHandler, RowHeader}
import gorsat.gorsatGorIterator.MapAndListUtilities
import org.gorpipe.exceptions.GorDataException
import org.gorpipe.gor.model.{Line, Row}
import org.gorpipe.gor.monitor.GorMonitor
import org.gorpipe.gor.session.GorSession
import org.gorpipe.model.gor.{BinaryHolder, RowObj}
import org.gorpipe.model.gor.iterators.LineIterator

object GorQueen {

  case class binaryHolder(bui : BucketInfo, af : Float, IDX1 : Array[Int], IDX1size : Int, GTS1length : Int, GTS2 : Array[Char]) extends BinaryHolder

  case class SaHolder(var seps: scala.collection.mutable.ArrayBuffer[Int])

  def splitArray(s: CharSequence, offset: Int, sah: SaHolder, sepval: Char = ','): Unit = {
    var i = offset
    var n = 0
    while (i < s.length) {
      if (s.charAt(i) == sepval || s.charAt(i) == '\t') {
        if (n < sah.seps.length) sah.seps(n) = i else sah.seps += i
        n += 1
      }
      i += 1
    }
    if (n < sah.seps.length) sah.seps(n) = i else sah.seps += i
    n += 1
    if (n < sah.seps.length) sah.seps = sah.seps.slice(0, n)
    // sah.seps.toArray
  }

  def colCharMove(n: Int, str: CharSequence, offset: Int, sah: SaHolder, o: Array[Char], oc : Int): Unit = {
    val start = if (n == 0) offset else sah.seps(n - 1) + 1
    val stop = sah.seps(n)
    var i = start
    while (i < stop) {
      o(oc) = str.charAt(i)
      i += 1
    }
  }

  def colCharMoveFixed(n: Int, str: CharSequence, offset: Int, o: Array[Char], oc : Int, valSize: Int, sepSize: Int): Unit = {
    val start = offset + n * (valSize + sepSize)
    val stop = start + valSize
    var i = start
    while (i < stop) {
      o(oc) = str.charAt(i)
      i += 1
    }
  }

  case class QueenState(session: GorSession,
                         lookupSignature: String,
                         buckCol: Int,
                         valCol: Int,
                         grCols: List[Int],
                         sepVals: String,
                         valSize: Int,
                         uv: String) extends BinState {

    case class ColHolder() {
      var buckRows: Array[CharSequence] = _
      var offsetArray: Array[Int] = _
      var splitArr: Array[SaHolder] = _
      var af : Float = _
    }

    val useGroup: Boolean = if (grCols.nonEmpty) true else false
    val sepSize: Int = if (valSize > 0) 0 else if (valSize == -1) 1 else sepVals.length.min(1)
    val sepval: Char = if (sepSize == 1 && valSize == -1) sepVals(0) else ','
    val unknownVal: String = if (valSize != -1) (uv + Range(uv.length + 1, 1000).map((x: Int) => {
      x - x / 10
    }).mkString("")).slice(0, valSize) else uv
    var unknown: Boolean = if (uv != "") true else false

    var bui: BucketInfo = _
    var groupMap = scala.collection.mutable.HashMap.empty[String, ColHolder]
    var singleColHolder = ColHolder()
    if (!useGroup) groupMap += ("theOnlyGroup" -> singleColHolder)
    val grColsArray: Array[Int] = grCols.toArray
    var ladd = new java.lang.StringBuilder(1024 * 4)

    def initColHolder(sh: ColHolder): Unit = {
      if (sh.buckRows == null) {
        sh.buckRows = new Array[CharSequence](maxUsedBuckets)
        sh.offsetArray = new Array[Int](maxUsedBuckets)
        sh.splitArr = new Array[SaHolder](maxUsedBuckets)
      }
      var i = 0
      while (i < sh.buckRows.length) {
        sh.buckRows(i) = null
        if (valSize == -1) sh.splitArr(i) = SaHolder(new scala.collection.mutable.ArrayBuffer[Int](100))
        i += 1
      }
    }

    var line: CharSequence = ""
    var maxUsedBuckets = 0

    def initialize(binInfo: BinInfo): Unit = {
      bui = session.getCache.getObjectHashMap.get(lookupSignature).asInstanceOf[BucketInfo]
      if (bui == null) throw new GorDataException(s"Non existing bucket info for lookupSignature $lookupSignature")
      maxUsedBuckets = bui.bucketIDMap.size
      if (useGroup) groupMap = scala.collection.mutable.HashMap.empty[String, ColHolder]
      else initColHolder(singleColHolder)
    }

    def process(r: Row): Unit = {
      var useLineObject = false
      if (r.isInstanceOf[Line]){
        line = r.colAsString(valCol)
        useLineObject = true
      } else {
        line = r.toString
      }

      bui.bucketIDMap.get(r.colAsString(buckCol).toString) match {
        case Some(buckNo) =>
          var sh: ColHolder = null
          if (useGroup) {
            val groupID = r.selectedColumns(grColsArray)
            groupMap.get(groupID) match {
              case Some(x) => sh = x
              case None =>
                sh = ColHolder()
                initColHolder(sh)
                groupMap += (groupID -> sh)
            }
          } else sh = singleColHolder

          sh.buckRows(buckNo) = line
          val offset = if (useLineObject) 0 else r.getSplitArray()(valCol - 1) + 1
          sh.offsetArray(buckNo) = offset
          if (valSize == -1) {
            splitArray(line, offset, sh.splitArr(buckNo), sepval)
          }
        case None => /* Do nothing - a row representing unused bucket */
      }

    }

    def sendToNextProcessor(bi: BinInfo, nextProcessor: Processor): Unit = {

      for (key <- groupMap.keys.toList.sorted) {
/*
        var IDX1 : Array[Int] = _
        var IDX1size : Int = 0
        var GTS1length : Int = 0
        var GTS2 : Array[Char] = _
*/
        val GTS1length = bui.outputBucketID.length
        val outputSize2 = bui.outputBucketID2.length
        val IDX1 = new Array[Int](GTS1length)
        val GTS2 = new Array[Char](outputSize2)
        var IDX1size : Int = 0

        def colIndexMove(n: Int, str: CharSequence, offset: Int, sah: SaHolder, oc : Int): Unit = {
          val start = if (n == 0) offset else sah.seps(n - 1) + 1
          if (str.charAt(start)=='1' || str.charAt(start)=='2') { IDX1(IDX1size) = oc; IDX1size += 1 }
        }

        def colIndexMoveFixed(n: Int, str: CharSequence, offset: Int, oc : Int, valSize: Int, sepSize: Int): Unit = {
          val start = offset + n * (valSize + sepSize)
          if (str.charAt(start)=='1' || str.charAt(start)=='2') { IDX1(IDX1size) = oc; IDX1size += 1 }
        }

        val theBinaryHolderRow = RowObj("chrA", 0, "")
        var sh: ColHolder = null
        if (useGroup) sh = groupMap(key) else sh = groupMap("theOnlyGroup")
        val af = sh.af
        var r: CharSequence = null
        var outCol = 0
        try {
          r = null
          IDX1size = 0
          outCol = 0
          while (outCol < bui.outputBucketPos.length) {
            val buckNo = bui.outputBucketID(outCol)
            val buckPos = bui.outputBucketPos(outCol)
            r = sh.buckRows(buckNo)
            val offset = sh.offsetArray(buckNo)
            if (r != null) {
                if (valSize == -1) {
                colIndexMove(buckPos, r, offset, sh.splitArr(buckNo), outCol)
              } else {
                  colIndexMoveFixed(buckPos, r, offset, outCol, valSize, sepSize)
                }
            }
            outCol += 1
          }
          outCol = 0
          while (outCol < bui.outputBucketPos2.length) {
            val buckNo = bui.outputBucketID2(outCol)
            val buckPos = bui.outputBucketPos2(outCol)
            r = sh.buckRows(buckNo)
            val offset = sh.offsetArray(buckNo)
            if (r == null) {
              GTS2(outCol) = '3'
            } else {
              if (valSize == -1) {
                colCharMove(buckPos, r, offset, sh.splitArr(buckNo), GTS2, outCol)
              } else {
                colCharMoveFixed(buckPos, r, offset, GTS2, outCol, valSize, sepSize)
              }
            }
            outCol += 1
          }

          theBinaryHolderRow.bH = binaryHolder(bui,af,IDX1,IDX1size,GTS1length,GTS2)

          if (!nextProcessor.wantsNoMore) {
            nextProcessor.process(theBinaryHolderRow)
          }

        } catch {
          case e: java.lang.IndexOutOfBoundsException =>
            throw new GorDataException(s"Missing values in bucket ${r.toString.split("\t")(buckCol)} in searching for tag ${bui.outputTags(outCol)} no $outCol in output\nin row\n$r\n\n", e)
        }
      }
      // cleanup
      if (useGroup) {
        groupMap.clear()
      }
    }
  }

  case class QueenFactory(session: GorSession,
                           lookupSignature: String,
                           buckCol: Int,
                           valCol: Int,
                           grCols: List[Int],
                           sepVal: String,
                           valSize: Int,
                           uv: String) extends BinFactory {
    def create: BinState =
      QueenState(session, lookupSignature, buckCol, valCol, grCols, sepVal, valSize, uv)
  }

  case class BucketInfo() {
    var outputBucketID: Array[Int] = _
    var outputBucketPos: Array[Int] = _
    var outputBucketID2: Array[Int] = _
    var outputBucketPos2: Array[Int] = _
    var bucketIDMap = scala.collection.mutable.Map.empty[String, Int]
    var outputTags: Array[String] = _
    var outputTags2: Array[String] = _
  }

  case class QueenAggregate(minSharing: Float, gm : GorMonitor) extends Analysis {
    var share: Array[Int] = _
    var count: Array[Int] = _
    var needsInitialization: Boolean = true
    var bh: binaryHolder = _
    var gtSize1: Int = 0
    var gtSize2: Int = 0
    var gtPairSize: Int = 0
    var cancelled: Boolean = false

    override def isTypeInformationMaintained : Boolean = true

    override def setRowHeader(header: RowHeader): Unit = {
      val columnNames: Array[String] = "Chrom\tPos\tPN1\tPN2\tavgSharing\tShare\tCount".split('\t')
      val columnTypes: Array[String] = "S\tI\tS\tS\tD\tI\tI".split('\t')
      super.setRowHeader(RowHeader(columnNames,columnTypes))
    }

    override def process(r: Row): Unit = {

      bh = r.bH.asInstanceOf[binaryHolder]
      if (needsInitialization) {
        needsInitialization = false
        gtSize1 = bh.GTS1length
        gtSize2 = bh.GTS2.length
        gtPairSize = gtSize1 * gtSize2
        share = new Array[Int](gtPairSize)
        count = new Array[Int](gtPairSize)
      }

      var i: Int = 0
      while (i < bh.IDX1size) {
        val pni = bh.IDX1(i)
        var j: Int = 0
        var ai = pni*gtSize2
        while (j < gtSize2) {
            val gt2 = bh.GTS2(j)
            if (gt2 == '1' || gt2 == '2') share(ai) += 1
            if (gt2 != '3') count(ai) += 1
            ai += 1
            j += 1
        }
        if (gm != null && gm.isCancelled()) {
          reportWantsNoMore()
          cancelled = true
        }
        i += 1
      }
    }

    override def finish(): Unit = {
      if (!cancelled && !needsInitialization) {
        var i: Int = 0
        while (i < gtSize1 && !cancelled && !wantsNoMore) {
          val PNi = bh.bui.outputTags(i)
          var j: Int = 0
          while (j < gtSize2) {
            val PNj = bh.bui.outputTags2(j)
            val ai = i*gtSize2+j
            val avgSharing = if (count(ai) > 0) share(ai).toFloat/count(ai) else -1.0f
            if (avgSharing >= minSharing ) {
              super.process(RowObj(s"chrA\t0\t$PNi\t$PNj\t$avgSharing\t${share(ai)}\t${count(ai)}"))
            }
            j += 1
          }
          if (gm != null && gm.isCancelled()) {
            reportWantsNoMore()
            cancelled = true
          }
          i += 1
        }
      }
      share = null
      count = null
    }
  }

  case class QueenAnalysis(fileName1: String, iteratorCommand1: String, iterator1: LineIterator, fileName2: String, iteratorCommand2: String, iterator2: LineIterator, fileName3: String, iteratorCommand3: String, iterator3: LineIterator, buckCol: Int, valCol: Int,
                            grCols: List[Int], sepVal: String, valSize: Int, uv: String, session: GorSession) extends
    BinAnalysis(RegularRowHandler(1), BinAggregator(QueenFactory(session, fileName1 + "#" + iteratorCommand1 + "#" + fileName2 + "#" + iteratorCommand2 + "#" + fileName3 + "#" + iteratorCommand3, buckCol, valCol, grCols, sepVal, valSize, uv), 2, 1)) {

    val bucketIDMap = scala.collection.mutable.Map.empty[String, Int]
    var bucketID: Int = -1

    def bucketID(b: String): Int = bucketIDMap.get(b) match {
      case Some(x) => x;
      case None =>
        bucketID += 1
        bucketIDMap += (b -> bucketID)
        bucketID
    }

    case class BucketCounter() {
      var c: Integer = -1
    }

    val bucketOrderMap = scala.collection.mutable.Map.empty[String, Int]
    val bucketCounterMap = scala.collection.mutable.Map.empty[String, BucketCounter]

    def bucketOrder(b: String, t: String): Int = {
      val bc = bucketCounterMap.get(b) match {
        case Some(x) => x;
        case None =>
          bucketCounterMap += (b -> BucketCounter())
          bucketCounterMap(b)
      }
      bucketOrderMap.get(s"#bu:$b" + s"#ta:$t") match {
        case Some(x) => x;
        case None =>
          bc.c += 1
          bucketOrderMap += ((s"#bu:$b" + s"#ta:$t") -> bc.c)
          bc.c
      }
    }

    val outputOrderMap = scala.collection.mutable.Map.empty[String, Int]
    var outputCounter: Int = -1
    val outputOrderMap2 = scala.collection.mutable.Map.empty[String, Int]
    var outputCounter2: Int = -1

    def outputOrder(b: String): Int = outputOrderMap.get(b) match {
      case Some(x) => x;
      case None =>
        outputCounter += 1
        outputOrderMap += (b -> outputCounter)
        outputCounter
    }
    def outputOrder2(b: String): Int = outputOrderMap2.get(b) match {
      case Some(x) => x;
      case None =>
        outputCounter2 += 1
        outputOrderMap2 += (b -> outputCounter2)
        outputCounter2
    }

    val lookupSignature: String = s"$fileName1#$iteratorCommand1#$fileName2#$iteratorCommand2#$fileName3#$iteratorCommand3"

    session.getCache.getObjectHashMap.computeIfAbsent(lookupSignature, _ => {
      var l1 = Array.empty[String]
      var l2 = Array.empty[String]
      var l3 = Array.empty[String]

      try {
        if (iteratorCommand1 != "") l1 = MapAndListUtilities.getStringArray(iteratorCommand1, iterator1, session)
        else l1 = MapAndListUtilities.getStringArray(fileName1, session)

        if (iteratorCommand2 != "") l2 = MapAndListUtilities.getStringArray(iteratorCommand2, iterator2, session)
        else l2 = MapAndListUtilities.getStringArray(fileName2, session)

        if (iteratorCommand3 != "") l3 = MapAndListUtilities.getStringArray(iteratorCommand3, iterator3, session)
        else l3 = MapAndListUtilities.getStringArray(fileName3, session)
      } catch {
        case e: Exception =>
          iterator1.close()
          iterator2.close()
          iterator3.close()
          throw e
      }

      var tags: List[String] = Nil
      var tags2: List[String] = Nil
      l2.foreach(x => {
        outputOrder(x)
        tags ::= x
      })
      l3.foreach(x => {
        outputOrder2(x)
        tags2 ::= x
      })
      val bi = BucketInfo()
      val outputSize = outputOrderMap.size
      val outputSize2 = outputOrderMap2.size
      bi.outputBucketID = new Array[Int](outputSize)
      bi.outputBucketPos = new Array[Int](outputSize)
      bi.outputBucketID2 = new Array[Int](outputSize2)
      bi.outputBucketPos2 = new Array[Int](outputSize2)

      val tagMap = outputOrderMap.clone()
      val tagMap2 = outputOrderMap2.clone()

      l1.foreach(x => {
        val r = x.split("\t")
        val (tag, bid) = (r(0), r(1))
        tagMap.remove(tag)
        tagMap2.remove(tag)

        val buckid = bucketID(bid)
        val buckpos = bucketOrder(bid, tag)

        if (outputOrder(tag) < outputSize) {
          bi.outputBucketID(outputOrder(tag)) = buckid
          bi.outputBucketPos(outputOrder(tag)) = buckpos
        }
        if (outputOrder2(tag) < outputSize2) {
          bi.outputBucketID2(outputOrder2(tag)) = buckid
          bi.outputBucketPos2(outputOrder2(tag)) = buckpos
        }
      })

      if (tagMap.nonEmpty) throw new GorDataException(s"There are tags in the second input file which are not defined in the first tag/bucket input, including: ${tagMap.keys.toList.slice(0, 10).mkString(",")}")
      if (tagMap2.nonEmpty) throw new GorDataException(s"There are tags in the third input file which are not defined in the first tag/bucket input, including: ${tagMap2.keys.toList.slice(0, 10).mkString(",")}")

      bi.bucketIDMap = bucketIDMap
      bi.outputTags = tags.reverse.toArray
      bi.outputTags2 = tags2.reverse.toArray
      bi
    })
  }
}
