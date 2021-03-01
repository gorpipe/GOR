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

package org.gorpipe.gor.cli;

import org.gorpipe.gor.cli.cache.CacheCommand;
import org.gorpipe.gor.cli.help.HelpCommand;
import org.gorpipe.gor.cli.index.IndexCommand;
import org.gorpipe.gor.cli.info.InfoCommand;
import org.gorpipe.gor.cli.manager.ManagerCommand;
import org.gorpipe.gor.cli.query.QueryCommand;
import org.gorpipe.gor.cli.render.RenderCommand;
import org.gorpipe.logging.GorLogbackUtil;
import picocli.CommandLine;

@SuppressWarnings("squid:S106")
@CommandLine.Command(name="gor",
        version="version 1.0",
        description = "Command line interface for gor query language and processes.",
        subcommands = {QueryCommand.class, HelpCommand.class, ManagerCommand.class, IndexCommand.class,
                CacheCommand.class, RenderCommand.class, InfoCommand.class})
public class GorCLI extends HelpOptions implements Runnable {
    public static void main(String[] args) {
        GorLogbackUtil.initLog("gor");
        CommandLine cmd = new CommandLine(new GorCLI());
        cmd.parseWithHandlers(
                new CommandLine.RunLast(),
                CommandLine.defaultExceptionHandler().andExit(-1),
                args);

    }

    @CommandLine.Option(names = {"-v", "--version"},
            versionHelp = true,
            description = "Print version information and exits.")
    boolean versionHelpRequested;

    @Override
    public void run() {
        CommandLine.usage(this, System.err);
    }
}
