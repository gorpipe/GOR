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

// GorPrePipe.scala
// (c) deCODE genetics
// 19th March, 2012, Hakon Gudbjartsson

package gorsat.process

import gorsat.Commands.{CommandArguments, CommandParseUtilities}
import gorsat.Script.{ScriptEngineFactory, ScriptParsers, SplitManager}
import gorsat.Utilities.MacroUtilities._
import gorsat.gorsatGorIterator.MapAndListUtilities.singleHashMap
import org.gorpipe.gor.model.GorOptions
import org.gorpipe.gor.session.GorSession
import org.gorpipe.gor.util.DataUtil

import scala.jdk.CollectionConverters.{MapHasAsScala, SetHasAsScala}


object GorPrePipe {

  val MAX_NUMBER_OF_STEPS = 10000

  var availCommands: Array[String] = GorPipeCommands.getGorCommands ++ GorInputSources.getInputSources
  val availNorCommands: Array[String] = GorPipeCommands.getNorCommands ++ GorInputSources.getInputSources

  private val supportedGorSQLFileEndings = Array[String](".json",".csv",".tsv",".gor",".gorz",".gor.gz",".gord",".txt",".vcf",".bgen",".parquet",".adam",".mt",".xml")
  private val commandsContainingInputSources = Array[String]("PARALLEL", "GOR","NOR","SPARK","MAP","MULTIMAP","INSET","MERGE","JOIN","LEFTJOIN","VARJOIN","GORIF", "NORIF"/*,"CSVSEL","CSVCC","GTGEN"*/)
  val gorpred = (p: String) => supportedGorSQLFileEndings.map(e => p.toLowerCase.endsWith(e)).reduce((a,b) => a || b)

  private def getCommandArgument(command: String) : Option[CommandArguments] = {
    val commandResult = GorPipeCommands.commandMap.get(command)

    if (commandResult.isDefined) {
      Option(commandResult.get.commandArguments)
    } else {
      val inputResult = GorInputSources.commandMap.get(command)

      if (inputResult.isDefined) {
        Option(inputResult.get.commandArguments)
      } else {
        val macroResult = GorPipeMacros.macrosMap.get(command)
        if (macroResult.isDefined) {
          Option(macroResult.get.commandArguments)
        } else {
          Option.empty[CommandArguments]
        }
      }
    }
  }

  def getUsedFiles(inputCommand: String, session: GorSession): List[String] = {

    val pipeSteps = CommandParseUtilities.quoteSafeSplit(inputCommand, '|')

    var usedFiles: List[String] = Nil

    for (i <- pipeSteps.indices) {
      val fullcommandWithHints =
        if (i == 0 &&
          !(pipeSteps(0).toUpperCase.trim.startsWith("PARALLEL")
            || pipeSteps(0).toUpperCase.trim.startsWith("GOR")
            || pipeSteps(0).toUpperCase.trim.startsWith("NOR")
            || pipeSteps(0).toUpperCase.trim.startsWith("GORIF")
            || pipeSteps(0).toUpperCase.trim.startsWith("NORIF")
            || pipeSteps(0).toUpperCase.trim.startsWith("SPARK")
            || pipeSteps(0).toUpperCase.trim.startsWith("SELECT")))
          "GOR " + pipeSteps(i).trim
        else
          pipeSteps(i).trim
      val fullcommand = GorJavaUtilities.clearHints(fullcommandWithHints)
      val command = fullcommand.split(' ')(0).toUpperCase
      var loopcommand = command
      val argString = CommandParseUtilities.quoteSafeSplitAndTrim(fullcommand, ' ').mkString(" ")

      val isSql = i==0 && command.equals("SELECT")
      if (commandsContainingInputSources.contains(command) || isSql) loopcommand = "JOIN"

      loopcommand match {
        case "JOIN" =>
          val cargs = CommandParseUtilities.quoteSafeSplit(argString, ' ')

          val commandArgument = getCommandArgument(command)

          if (commandArgument.isDefined) {
            val (iargs, _) = CommandParseUtilities.validateCommandArguments(cargs.slice(1, MAX_NUMBER_OF_STEPS), commandArgument.get)

            if (iargs.length > 0) {
              val inputArguments = iargs.slice(0, iargs.length).toList
              val of = if(isSql) {
                inputArguments.map(s => s.toLowerCase).zipWithIndex.find(p => p._1.equals("from"))
              } else Option.empty
              val rightFile = if(of.nonEmpty) inputArguments(of.get._2+1) else iargs(0).trim

              if (CommandParseUtilities.isNestedCommand(rightFile)) {
                // Nested pipe command
                val iteratorCommand = CommandParseUtilities.parseNestedCommand(rightFile)
                val subFiles = getUsedFiles(iteratorCommand, session)
                usedFiles :::= subFiles
              } else {
                if (inputArguments.exists(inputArgument => DataUtil.isDictionary(inputArgument))) {
                  val jtags = GorOptions.tagsFromOptions(session, cargs)
                  val tags: Set[String] = if (jtags != null) jtags.asScala.toSet else Set.empty
                  val dictFiles = inputArguments.collect {
                    case s: String if DataUtil.isDictionary(s) && !s.startsWith("-") =>
                      if (tags == null || tags.isEmpty) "#gordict#" + s else "#gordict#" + s + "#gortags#" + tags.mkString(",")
                  }
                  val otherFiles = inputArguments.filter(argumentEntry => !argumentEntry.startsWith("-") &&
                    !DataUtil.isDictionary(argumentEntry))

                  usedFiles :::= otherFiles ::: dictFiles
                } else if(isSql) {
                  if (of.nonEmpty) {
                    if (gorpred.apply(rightFile)) {
                      usedFiles ::= rightFile
                    } else {
                      usedFiles ::= "sql://" + rightFile
                    }
                  }
                } else {
                  usedFiles :::= inputArguments.filter(argumentEntry => !argumentEntry.startsWith("-"))
                }
              }
            }
          }
        case _ =>
          /* do nothing */
      }
    }

    usedFiles.distinct
  }


  def pgorReplacer(inputCommand: String): String = {
    var mic = inputCommand
    for (p <- List("p", "P"); g <- List("g", "G"); o <- List("o", "O"); r <- List("r", "R")) {
      val pgor = p + g + o + r + " "
      if (mic.contains(pgor)) {
        mic = CommandParseUtilities.repeatedQuoteSafeReplace(mic, pgor, "gor  ", mic.length + 1)
        val (splitOpt, splitSize, splitOverlap, _) = ScriptParsers.splitOptionParser(mic)
        if (splitOpt != "") {
          val repstr = if (splitOverlap != "") "-" + splitOpt + splitSize + ":" + splitOverlap + " " else "-" + splitOpt + splitSize + " "
          mic = mic.replace(repstr, "").replace(SplitManager.WHERE_SPLIT_WINDOW, "2=2")
        }
      }
    }

    mic
  }

  // Server side - alias replacement done in client
  def getAliasesAndCreates(inputCommand: String, session: GorSession): Array[String] = {

    var equiVFlist: List[(String, String)] = Nil
    var aliases:singleHashMap = new java.util.HashMap[String, String]()

    var outArray = Array.empty[String]

    val inputCommands = CommandParseUtilities.quoteSafeSplit(inputCommand, ';')

    val mainAliasMap:singleHashMap = new java.util.HashMap[String, String]()

    val engine = ScriptEngineFactory.create(session.getGorContext)

    try {
      val modifiedInputCommands = inputCommands.map(x => pgorReplacer(replaceAllAliases(x, mainAliasMap)))
      engine.execute(modifiedInputCommands)
      val createdFiles = engine.getCreatedFiles
      equiVFlist = engine.getVirtualFiles
      aliases = engine.getAliases

      val list: List[String] = Nil
      createdFiles.entrySet().forEach(x => {
        list :+ "createdFiles\t" + x.getKey + "\t" + x.getValue
      })
      outArray ++= list
      outArray ++= equiVFlist.map(x => "equiVFlist\t" + x._1 + "\t" + x._2)
      outArray ++= aliases.asScala.map(x => "aliases\t" + x._1 + "\t" + x._2)

    } catch {
      case _: Exception => /* nothing */
    }

    outArray
  }
}



