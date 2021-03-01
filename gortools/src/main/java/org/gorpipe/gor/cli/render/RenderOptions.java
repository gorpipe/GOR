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

package org.gorpipe.gor.cli.render;

import org.gorpipe.gor.cli.HelpOptions;
import picocli.CommandLine;

import java.io.File;

public abstract class RenderOptions extends HelpOptions {

    @CommandLine.Option(names={"-a","--aliases"}, description = "Loads aliases from external file.")
    protected File aliasFile;

    @CommandLine.Option(names={"-p","--pretty"}, description = "Formats output by commands and steps")
    protected boolean pretty;

    @CommandLine.Parameters(index = "0", arity = "1", paramLabel = "INPUT", description = "Report, script file or script to render.")
    protected String input;
}
