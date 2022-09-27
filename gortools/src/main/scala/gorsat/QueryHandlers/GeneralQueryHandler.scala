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

package gorsat.QueryHandlers

import gorsat.Analysis.CheckOrder

import java.lang
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import gorsat.Utilities.AnalysisUtilities.writeList
import gorsat.Commands.{CommandParseUtilities, Processor}
import gorsat.DynIterator.DynamicRowSource
import gorsat.Outputs.OutFile
import gorsat.QueryHandlers.GeneralQueryHandler.{findCacheFile, findOverheadTime, getRelativeFileLocationForDictionaryFileReferences, runCommand}
import gorsat.Utilities.AnalysisUtilities
import gorsat.process.{GorJavaUtilities, ParallelExecutor}
import org.gorpipe.client.FileCache
import org.gorpipe.exceptions.{GorException, GorSystemException, GorUserException}
import org.gorpipe.gor.binsearch.GorIndexType
import org.gorpipe.gor.model.{DriverBackedFileReader, FileReader, GorMeta, GorParallelQueryHandler}
import org.gorpipe.gor.monitor.GorMonitor
import org.gorpipe.gor.session.GorContext
import org.gorpipe.gor.table.TableHeader
import org.gorpipe.gor.table.dictionary.DictionaryTableMeta
import org.gorpipe.gor.table.util.PathUtils
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import java.util.Optional

class GeneralQueryHandler(context: GorContext, header: Boolean) extends GorParallelQueryHandler {

  def getResultsPath(nested: GorContext, newCacheFile: String, resultFileName: String): (Path, String) = {
    val resultFilePath = if (newCacheFile.endsWith(".gor") && resultFileName.endsWith(".gorz")) resultFileName.substring(0,resultFileName.length-1) else resultFileName
    val ending = resultFilePath.lastIndexOf(".")
    var extension = resultFilePath.substring(ending)
    if (PathUtils.isLocal(newCacheFile)) {
      val cachePath = if (!PathUtils.isAbsolutePath(newCacheFile)) {
        nested.getSession.getProjectContext.getProjectRootPath.resolve(newCacheFile)
      } else {
        Paths.get(newCacheFile)
      }
      val resPath = Path.of(resultFilePath)
      GorJavaUtilities.createSymbolicLink(resPath,cachePath)
      (resPath,extension)
    } else {
      extension += ".link"
      val resPath = Path.of(resultFilePath+".link")
      Files.writeString(resPath,newCacheFile)
      (resPath,extension)
    }
  }

  def runAndStoreLinkFileInCache(nested: GorContext, newCacheFile: String, fileCache: FileCache, useMd5: Boolean): String = {
    val startTime = System.currentTimeMillis
    val fileReader = nested.getSession.getProjectContext.getFileReader
    val commandToExecute = nested.getCommand
    val commandSignature = nested.getSignature
    val isGord = commandToExecute.toUpperCase().startsWith(CommandParseUtilities.GOR_DICTIONARY)
    val noDict = commandToExecute.toLowerCase.contains(" -nodict ")
    val writeGord = isGord && !noDict
    var cacheRes = newCacheFile
    var resultFileName = runCommand(nested, commandToExecute, if (isGord) newCacheFile else null, useMd5, theTheDict = true)
    val isCacheDir = fileReader.isDirectory(newCacheFile)
    if(fileCache != null && (!isCacheDir || writeGord)) {
        resultFileName = findCacheFile(commandSignature, commandToExecute, header, fileCache, AnalysisUtilities.theCacheDirectory(context.getSession))
        val overheadTime = findOverheadTime(commandToExecute)
        val resultPath = getResultsPath(nested, newCacheFile, resultFileName)
        cacheRes = fileCache.store(resultPath._1, commandSignature, resultPath._2, overheadTime + System.currentTimeMillis - startTime)
    }
    cacheRes
  }

  def runAndStoreInCache(nested: GorContext, fileCache: FileCache, useMd5: Boolean): String = {
    val startTime = System.currentTimeMillis
    val commandToExecute = nested.getCommand
    val commandSignature = nested.getSignature
    var cacheFile = findCacheFile(commandSignature, commandToExecute, header, fileCache, AnalysisUtilities.theCacheDirectory(context.getSession))
    val resultFileName = runCommand(nested, commandToExecute, cacheFile, useMd5, theTheDict = false)
    if (fileCache != null) {
      val extension = CommandParseUtilities.getExtensionForQuery(commandToExecute, header)
      val overheadTime = findOverheadTime(commandToExecute)
      cacheFile = fileCache.store(Paths.get(resultFileName), commandSignature, extension, overheadTime + System.currentTimeMillis - startTime)
    }
    cacheFile
  }

  def executeBatch(commandSignatures: Array[String], commandsToExecute: Array[String], batchGroupNames: Array[String], cacheFiles: Array[String], gorMonitor: GorMonitor): Array[String] = {
    val fileNames = new Array[String](commandSignatures.length)
    val fileCache = context.getSession.getProjectContext.getFileCache
    var commandList: List[() => Unit] = Nil
    val useMd5 = System.getProperty("gor.caching.md5.enabled", "false").toBoolean

    for (i <- commandSignatures.indices) {
      val executeFunction = block2Function {

        val (commandSignature, commandToExecute, batchGroupName) = (commandSignatures(i), commandsToExecute(i), batchGroupNames(i))
        val nested = context.createNestedContext(batchGroupName, commandSignature, commandToExecute)

        try {
          var cacheFile = fileCache.lookupFile(commandSignature)
          cacheFile = GorJavaUtilities.verifyLinkFileLastModified(context.getSession.getProjectContext,cacheFile)
          // Do this if we have result cache active or if we are running locally and the local cacheFile does not exist.
          fileNames(i) = if (cacheFile == null) {
            val newCacheFile = cacheFiles(i)
            if(newCacheFile!=null) {
              runAndStoreLinkFileInCache(nested, newCacheFile, fileCache, useMd5)
            } else {
              runAndStoreInCache(nested, fileCache, useMd5)
            }
          } else {
            nested.cached(cacheFile)
            cacheFile
          }
        } catch {
          case gue: GorUserException =>
            gue.setQuerySource(batchGroupName)
            gue.setContext(nested)
            throw gue
        }
      }
      commandList ::= executeFunction
    }

    if (commandList != Nil) parallelExecution(commandList.reverse.toArray)
    fileNames
  }


  def parallelExecution(commands: Array[() => Unit]): Unit = {
    val pe = new ParallelExecutor(context.getSession.getSystemContext.getWorkers, commands)
    try
      pe.parallelExecute()
    catch {
      case ge: GorException =>
        throw ge
      case e: Throwable =>
        throw new GorSystemException(e)
    }
  }

  def block2Function(block: => Unit): () => Unit = {
    () => block
  }



  override def setForce(force: Boolean): Unit = {

  }

  override def setQueryTime(time: lang.Long): Unit = {

  }

  override def getWaitTime: Long = {
    -1
  }
}

object GeneralQueryHandler {

  private val logger = LoggerFactory.getLogger(this.getClass)

  /**
    * @return full path to the cache file.
    */
  def findCacheFile(commandSignature: String, commandToExecute: String, header: Boolean, fileCache: FileCache, cacheDirectory: String): String = {
    fileCache.tempLocation(commandSignature,
      CommandParseUtilities.getExtensionForQuery(commandToExecute, header))
  }

  def runCommand(context: GorContext, commandToExecute: String, outfile: String, useMd5: Boolean, theTheDict: Boolean): String = {
    context.start(outfile)
    // We are using absolute paths here
    val fileReader = context.getSession.getProjectContext.getSystemFileReader
    val result = if (commandToExecute.toUpperCase().startsWith(CommandParseUtilities.GOR_DICTIONARY_PART) || commandToExecute.toUpperCase().startsWith(CommandParseUtilities.GOR_DICTIONARY_FOLDER_PART)) {
      writeOutGorDictionaryPart(commandToExecute, fileReader, outfile, theTheDict)
    } else if (commandToExecute.toUpperCase().startsWith(CommandParseUtilities.GOR_DICTIONARY)) {
      writeOutGorDictionary(commandToExecute, fileReader, outfile, theTheDict)
    } else if (commandToExecute.toUpperCase().startsWith(CommandParseUtilities.NOR_DICTIONARY)) {
      writeOutNorDictionaryPart(commandToExecute, fileReader, outfile)
    } else {
      runCommandInternal(context, commandToExecute, outfile, useMd5)
    }
    context.end()
    result
  }

  private def runCommandInternal(context: GorContext, commandToExecute: String, outfile: String, useMd5: Boolean): String = {
    val theSource = new DynamicRowSource(commandToExecute, context)
    val theHeader = theSource.getHeader

    val projectContext = context.getSession.getProjectContext
    val fileReader = projectContext.getFileReader.asInstanceOf[DriverBackedFileReader]
    val projectRoot = projectContext.getProjectRoot
    val temp_cacheFile = if(outfile!=null) AnalysisUtilities.getTempFileName(outfile) else null
    val oldName = if(temp_cacheFile!=null) {
      var tmpcache = temp_cacheFile
      if(!PathUtils.isAbsolutePath(tmpcache)) {
        tmpcache = PathUtils.resolve(projectRoot,tmpcache)
      }
      tmpcache
    } else null
    try {
      val nor = theSource.isNor
      var newName: String = null
      // TODO: Get a gor config instance somehow into gorpipeSession or gorContext?
      if (useMd5) {
        val runner = context.getSession.getSystemContext.getRunnerFactory.create()
        val ps: Processor = if(outfile!=null) {
          val out = OutFile(temp_cacheFile, fileReader, theHeader, skipHeader = false, columnCompress = false, nor = nor, useMd5, md5File = true, infer = false, GorIndexType.NONE)
          if(nor) out else CheckOrder() | out
        } else null
        runner.run(theSource, ps)
        val md5File = oldName + ".md5"
        if (fileReader.exists(md5File)) {
          val md5SumLines = fileReader.readAll(md5File)

          if (md5SumLines.nonEmpty && md5SumLines(0).nonEmpty) {
            val extension = outfile.slice(outfile.lastIndexOfSlice("."), outfile.length)
            val md5FileParent = PathUtils.getParent(md5File)
            newName = PathUtils.resolve(md5FileParent,md5SumLines(0) + extension)
            try {
              //Files.delete(md5File)
            } catch {
              case _: Exception => /* Do nothing */
            }
          } else {
            logger.warn("MD5 file names are enabled bu the md5 files are not returning any values.")
          }
        } else {
          logger.warn("MD5 files are enabled but no md5 files are found when storing files in filecahce.")
        }

        if (newName == null) {
          newName = outfile
          if(!PathUtils.isAbsolutePath(newName)) {
            newName = PathUtils.resolve(projectRoot,newName)
          }
        }
      } else {
        val runner = context.getSession.getSystemContext.getRunnerFactory.create()
        val ps: Processor = if(outfile!=null) {
          val out = OutFile(temp_cacheFile, fileReader, theHeader, skipHeader = false, nor = nor, md5 = true, command = commandToExecute)
          if (nor) out else CheckOrder() | out
        } else null
        runner.run(theSource, ps)
        if(outfile!=null) {
          newName = outfile
          if(!PathUtils.isAbsolutePath(newName)) {
            newName = PathUtils.resolve(projectRoot,newName)
          }
        }
      }

      if(oldName!=null && fileReader.exists(oldName) && !oldName.equals(newName)) {
        fileReader.move(oldName, newName)
        val oldMetaName = oldName + ".meta"
        if (fileReader.exists(oldMetaName)) {
          val parent = PathUtils.getParent(oldMetaName)
          val name = PathUtils.getFileName(newName)

          fileReader.move(oldMetaName, parent + "/" + name+".meta")
        }
        newName
      } else ""
    } catch {
      case e: Exception =>
        try {
          fileReader.delete(oldName)
        } catch {
          case _: Exception => /* do nothing */
        }
        throw e
    } finally {
      theSource.close()
    }
  }

  private def writeOutGorDictionaryFolder(fileReader: FileReader, outfolderpath: String, useTheDict: Boolean): Unit = {
    val outpath = if(useTheDict) {
      if (outfolderpath.endsWith("/")) outfolderpath+"thedict.gord" else outfolderpath+"/thedict.gord"
    } else {
      var idx = outfolderpath.lastIndexOf("/")
      if (idx == -1) {
        outfolderpath+"/"+outfolderpath
      } else if (idx == outfolderpath.length) {
        idx = outfolderpath.lastIndexOf("/", idx-1)
        outfolderpath+"/"+outfolderpath.substring(idx+1,outfolderpath.length-1)
      } else {
        outfolderpath+"/"+outfolderpath.substring(idx+1)
      }
    }
    GorJavaUtilities.writeDictionaryFromMeta(fileReader, outfolderpath, outpath)
  }

  def dictRangeFromSeekRange(inp: String, prefix: String): String = {
    val cep = inp.split(':')
    val stasto = if (cep.length > 1) cep(1).split('-') else Array("0", Integer.MAX_VALUE.toString)
    val (c, sp, ep) = (cep(0), stasto(0), if (stasto.length > 1 && stasto(1).nonEmpty) stasto(1) else Integer.MAX_VALUE.toString)
    prefix + c + "\t" + sp + "\t" + c + "\t" + ep
  }

  private def getDictList(dictFiles: List[String], chromsrange: List[String], fileReader: FileReader): List[String] = {
    var chrI = 0
    val useMetaFile = System.getProperty("gor.use.meta.dictionary","true")
    if(useMetaFile!=null && useMetaFile.toLowerCase.equals("true")) {
      dictFiles.zip(chromsrange).map(x => {
        val f = x._1
        chrI += 1
        val rf = getRelativeFileLocationForDictionaryFileReferences(f)
        val prefix = rf + "\t" + chrI + "\t"
        val metaPath = f+".meta"
        val opt: Optional[String] = if (fileReader.exists(metaPath)) {
          val meta = GorMeta.createAndLoad(fileReader, metaPath)
          if (meta.getLineCount == -1) {
            val ret = dictRangeFromSeekRange(x._2, prefix)
            Optional.of[String](ret)
          } else if(meta.getLineCount > 0) {
            Optional.of[String](prefix + meta.getRange().formatAsTabDelimited())
          } else {
            Optional.empty()
          }
        } else {
          val ret = dictRangeFromSeekRange(x._2, prefix)
          Optional.of[String](ret)
        }
        opt
      }).flatMap(o => o.stream().iterator().asScala)
    } else {
      dictFiles.zip(chromsrange).map(x => {
        val f = x._1
        chrI += 1
        val rf = getRelativeFileLocationForDictionaryFileReferences(f)
        val prefix = rf + "\t" + chrI + "\t"
        dictRangeFromSeekRange(x._2, prefix)
      })
    }
  }

  private def getPartDictList(dictFiles: List[String], partitions: List[String]): List[String] = {
    dictFiles.zip(partitions).map(x => {
      val f = getRelativeFileLocationForDictionaryFileReferences(x._1)
      val part = x._2
      // file, alias
      f + "\t" + part
    })
  }

  private def writeOutGorDictionary(commandToExecute: String, fileReader: FileReader, outfile: String, useTheDict: Boolean): String = {
    if(fileReader.exists(outfile)) {
      if (!commandToExecute.toLowerCase.contains("-nodict")) writeOutGorDictionaryFolder(fileReader, outfile, useTheDict)
    } else {
      val w = commandToExecute.split(' ')
      var dictFiles: List[String] = Nil
      var chromsrange: List[String] = Nil
      var i = 1
      while (i < w.length - 1) {
        dictFiles ::= w(i)
        chromsrange ::= w(i + 1)
        i += 2
      }
      val tableHeader = new TableHeader
      if(dictFiles.nonEmpty) {
        val header = fileReader.readHeaderLine(dictFiles.head).split("\t")
        tableHeader.setColumns(header)
      }
      tableHeader.setProperty(DictionaryTableMeta.HEADER_LINE_FILTER_KEY, "false")
      tableHeader.setFileHeader(DictionaryTableMeta.DEFULT_RANGE_TABLE_HEADER)
      val dictList = getDictList(dictFiles, chromsrange, fileReader)
      writeList(fileReader, outfile, tableHeader.formatHeader(), dictList)
    }
    outfile
  }

  def writeOutNorDictionaryPart(commandToExecute: String, fileReader: FileReader, outfile: String): String = {
    val w = commandToExecute.split(' ')
    var dictFiles: List[String] = Nil
    var partitions: List[String] = Nil
    var i = 1
    while (i < w.length - 1) {
      dictFiles ::= w(i)
      partitions ::= w(i + 1)
      i += 2
    }
    val tableHeader = new TableHeader
    tableHeader.setProperty(DictionaryTableMeta.HEADER_LINE_FILTER_KEY, "false")
    val header = fileReader.readHeaderLine(dictFiles.head).split("\t")
    tableHeader.setColumns(header)
    val dictList = getPartDictList(dictFiles, partitions)
    writeList(fileReader, outfile, tableHeader.formatHeader(), dictList)

    outfile
  }

  private def writeOutGorDictionaryPart(commandToExecute: String, fileReader: FileReader, outfile: String, useTheDict: Boolean): String = {
    if(fileReader.isDirectory(outfile)) {
      if (!commandToExecute.toLowerCase.contains("-nodict")) writeOutGorDictionaryFolder(fileReader, outfile, useTheDict)
    } else {
      val w = commandToExecute.split(' ')
      var dictFiles: List[String] = Nil
      var partitions: List[String] = Nil
      var i = 1
      while (i < w.length - 1) {
        dictFiles ::= w(i)
        partitions ::= w(i + 1)
        i += 2
      }
      val tableHeader = new TableHeader
      val header = fileReader.readHeaderLine(dictFiles.head).split("\t")
      tableHeader.setProperty(DictionaryTableMeta.HEADER_LINE_FILTER_KEY, "false")
      tableHeader.setColumns(header)
      val dictList = getPartDictList(dictFiles, partitions)
      writeList(fileReader, outfile, tableHeader.formatHeader(), dictList)
    }
    outfile
  }

  def getRelativeFileLocationForDictionaryFileReferences(fileName: String): String = {
    if(fileName.startsWith("/")) fileName else "../" * fileName.count(x => x == '/') + fileName
  }

  def findOverheadTime(commandToExecute: String): Long = {
    var overheadTime = 0
    if (commandToExecute.startsWith(CommandParseUtilities.GOR_DICTIONARY_PART)) {
      overheadTime = 1000 * 60 * 10 // 10 minutes
    } else if (commandToExecute.startsWith(CommandParseUtilities.GOR_DICTIONARY)) {
      overheadTime = 1000 * 60 * 10 // 10 minutes
    }
    overheadTime
  }

}
