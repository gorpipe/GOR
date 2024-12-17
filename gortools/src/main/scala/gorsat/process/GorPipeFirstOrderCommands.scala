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

package gorsat.process

import gorsat.Commands.CommandParseUtilities
import gorsat.gorsatGorIterator.MapAndListUtilities
import org.gorpipe.gor.model.FileReader

import scala.jdk.CollectionConverters.ListHasAsScala
import scala.language.postfixOps

trait GorPipeFirstOrderCommands {

  def helpCommand(args: Array[String], fileReader: FileReader): Unit = {
    
    var message = ""

    if (args.length < 2) {
      message = "\ngorpipe, version " + gorsat.process.GorPipe.version + ", (c) WuXiNextCODE, 2018\n\n" +
        //  var message = "\ngorpipe, evaluation version. (c) WuXiNextCODE, 2014\n\n" +
        "gorpipe is a software tool used to execute GOR queries within a command shell locally. \n\n" +
        "Options:\n\n" +
        " -aliases <file>      File alias lookup file, e.g. $gene path/genes.gor.\n" +
        "                      By default the program looks for the file gor_aliases.txt in local directory.\n" +
        " -stdin               The program reads stdin, e.g. if the command does not start with gor ...\n" +
        " -nor                 The program reads the source as a non-ordered file, e.g. tsv file and adds (chr,pos) ...\n" +
        " -color <coloring>    Color the output either by 'type' or 'rotate'.\n" +
        " -config <file>       Path to configuration file, e.g. build info etc..\n" +
        " -cachedir <path>     Path to a folder for temporary and cache files.\n" +
        " -logdir <path>       Path to a folder for log files.\n" +
        " -workers <count>     Number of workers in parallel execution.\n" +
        " -script <file>       Path to a file with gor script, e.g. create xxx = ....;, def yyy = ...; and a final gor command.\n" +
        " -signature           Output a signature to name the query. Advanced option for developers.\n" +
        " -virtualfile <vr>    Output the filename of a virtualfile in the query. Advanced option for developers.\n" +
        " -helpfile <file>     Full path to the help file.\n" +
        " -stacktrace          Show stack trace on errors.\n" +
        " -version             Show the build version and git commit SHA hash.\n\n" +
        "Example: \n" +
        "  gorpipe \"gor genes.gor | group 1000 -count\"\n\n" +
        "For further information see the GOR User Manual: https://docs.gorpipe.org\n" +
        "For command help type gorpipe help <command>, e.g. gorpipe help join, gorpipe help formulas, or gorpipe help all.\n"
    }

    if (args.length > 1) {
      val cmd: String = if (args.length < 2) "COMMANDS" else args(1).toUpperCase
      var toPrint: Boolean = false
      var helpList: Array[String] = null
      var helpFile: String = "from jar"
      try {
        val helpParams = helpFileOpt(args, fileReader)
        helpFile = helpParams._1
        helpList = helpParams._2
        helpList.foreach(l => {
          if (l.startsWith("->")) {
            if (l.slice(2, l.length).startsWith(cmd)) {
              toPrint = true
              message += "\n\n" + l.slice(2, l.length) + "\n" + "=====================".slice(2, l.length) + "\n\n"
            } else toPrint = false
          } else if (toPrint) message += l + "\n"
        })
      } catch {
        case e: Throwable => message += " Cannot read the help file: " + helpFile + "\n\n" + e.toString
      }
    }

    System.err.print(message)
  }

  def helpFileOpt(args: Array[String], fileReader: FileReader):(String,Array[String]) = {
    var helpList: Array[String] = null
    var helpFile: String = "from jar"
    if (CommandParseUtilities.hasOption(args, "-helpfile")) {
      helpFile = CommandParseUtilities.stringValueOfOption(args, "-helpfile")
      helpList = MapAndListUtilities.readArray(helpFile, fileReader)
    } else {
      helpList = getHelpListFromFiles()
    }
    (helpFile,helpList)
  }

  def getHelpListFromFiles(): Array[String] = {
    var helpList = List.empty[String]
    val helpFiles = Array("gor_commands_help.txt", "gor_functions_help.txt")

    helpFiles.foreach{helpFile =>
      val helpJarURL = getClass.getClassLoader.getResource(helpFile).toURI
      val env = new java.util.HashMap[java.lang.String, java.lang.String]()
      env.put("create", "true")
      val zipfs = java.nio.file.FileSystems.newFileSystem(helpJarURL, env)
      helpList :::= java.nio.file.Files.readAllLines(java.nio.file.Paths.get(helpJarURL), java.nio.charset.Charset.forName("ISO-8859-1")).asScala.toList
      zipfs.close()
    }

    helpList.toArray
  }
}
