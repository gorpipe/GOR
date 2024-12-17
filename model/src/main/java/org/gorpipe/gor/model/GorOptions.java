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
package org.gorpipe.gor.model;

import com.google.common.base.Strings;
import gorsat.Commands.CommandArguments;
import gorsat.Commands.CommandParseUtilities;
import gorsat.Commands.GenomicRange;
import gorsat.DynIterator;
import gorsat.gorsatGorIterator.MapAndListUtilities;
import org.gorpipe.exceptions.GorDataException;
import org.gorpipe.exceptions.GorParsingException;
import org.gorpipe.exceptions.GorResourceException;
import org.gorpipe.exceptions.GorSystemException;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.filters.InFilter;
import org.gorpipe.gor.driver.filters.RowFilter;
import org.gorpipe.gor.driver.meta.DataType;
import org.gorpipe.gor.monitor.GorMonitor;
import org.gorpipe.gor.session.GorContext;
import org.gorpipe.gor.session.GorSession;
import org.gorpipe.gor.session.GorSessionCache;
import org.gorpipe.gor.session.SystemContext;
import org.gorpipe.gor.table.Dictionary;
import org.gorpipe.gor.table.dictionary.DictionaryTableReader;
import org.gorpipe.gor.table.dictionary.gor.*;
import org.gorpipe.gor.table.util.PathUtils;
import org.gorpipe.gor.table.TableHeader;
import org.gorpipe.gor.util.DataUtil;
import org.gorpipe.gor.util.StringUtil;
import org.gorpipe.gor.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.Tuple2;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static gorsat.Commands.CommandParseUtilities.stringValueOfOptionWithDefault;

/**
 * Various options that can be entered with Gor
 *
 * @version $Id$
 */
public class GorOptions {

    private static final String ERROR_INITIALIZING_QUERY = "Error Initializing Query: ";
    private static final String IS_NOT_FOUND = " is not found!";
    public static final String DEFAULT_FOLDER_DICTIONARY_NAME = "thedict.gord";

    private static final Logger log = LoggerFactory.getLogger(GorOptions.class);

    public GorSession getSession() {
        return session;
    }
    /**
     * True if we cache all the parsed lines of a dictionary once we have read it, and make a hash map from pn's to lines.
     */
    private final boolean useDictionaryCache = Boolean.parseBoolean(System.getProperty("gor.dictionary.cache.active", "true"));
    private final boolean useTable = false;
    /**
     * The gorPipeSession
     */
    private GorSession session;
    private GorContext context;
    /**
     * List of files to process
     */
    public final ArrayList<SourceRef> files = new ArrayList<>();
    /**
     * Chromsome number to start with
     */
    public final int chromo;
    /**
     * basepair position to start (inclusive) with, requires chromosome
     */
    public final int begin;
    /**
     * basepair position to end with (exclusive), requires chromosome,
     */
    public final int end;
    /**
     * Chromosome name to start with
     */
    public final String chrname;
    /**
     * True to insert source name as column
     */
    public final boolean insertSource;
    /**
     * Index file path
     */
    private final String idxFile;
    /**
     * Reference file path
     */
    public String refFile;
    /**
     * The name of the output column with the source field
     */
    public String sourceColName;
    /**
     * The header for the current table/dictionary
     */
    public String tableHeader;
    /**
     * The common root that was prepended to each source name
     */
    public final String commonRoot;
    /**
     * The number of blocks to run in parallel
     */
    public final int parallelBlocks;
    /**
     * Map of column nr to a list of tags to filter the column on
     */
    public Set<String> columnTags;
    /**
     * Silent tag filtering.  If silent we get don't error even if not all tags in filter are seen in the data.
     */
    public final boolean isSilentTagFilter;
    /**
     * Don't use tags for line filtering, only for dictionary filtering.
     */
    public boolean isNoLineFilter;
    /**
     * The resource monitor to report resources usage to, or null if none is attached
     */
    public final ResourceMonitor monitor;
    private boolean enforceResourceRelativeToRoot = false; // Flag that all local resources should be validated as being relative to root, i.e. must be at or below root in the hierarchy and no abolute refs.

    public boolean hasLocalDictonaryFile = false; // Flag that a local dictonary file has been found
    private boolean isDictionaryWithBuckets = false; // source col from dictionary files can be hiden if no buckets and no -f filters

    private String sourceName;

    /**
     * Chromosome Name to Id Cache
     */
    public final ChromoCache chrcache = new ChromoCache();
    
    public static class ProjectContext {
        public final String securityKey;
        public final String commonRoot;

        ProjectContext(String securityKey, String commonRoot) {
            this.securityKey = securityKey;
            this.commonRoot = commonRoot;
        }
    }

    public GorOptions(List<SourceRef> files, ResourceMonitor resmon) {
        this(-1, 0, Integer.MAX_VALUE, false, 0, null, false, false, null, files,
                null, resmon);
    }

    public GorOptions(List<SourceRef> files) {
        this(-1, 0, Integer.MAX_VALUE, false, 0, null, false, false, null,
                 files, null,  null);
    }

    /**
     * Construct GorOptions
     */
    public GorOptions(int chromoOpt, int beginOpt, int endOpt, boolean insertSourceOpt, int parallelBlocks,
                      final Set<String> columnTags, boolean silentTagFilter, boolean noLineFilter, String srcColName,
                      List<SourceRef> files, String commonRootOpt,
                      ResourceMonitor monitor) {
        this.chromo = chromoOpt;
        this.chrname = 0 <= chromoOpt && chromoOpt < ChrDataScheme.ChrLexico.id2chr.length ? ChrDataScheme.ChrLexico.id2chr[chromoOpt] : null;
        this.begin = beginOpt;
        this.end = endOpt;
        this.insertSource = insertSourceOpt;
        this.idxFile = null;
        this.refFile = null;
        this.parallelBlocks = parallelBlocks;
        this.columnTags = columnTags;
        this.isSilentTagFilter = silentTagFilter;
        this.isNoLineFilter = noLineFilter;
        this.sourceColName = srcColName;
        if (files != null) {
            this.files.addAll(files);
        }
        this.commonRoot = commonRootOpt;
        this.monitor = monitor;
    }

    static void checkFileNameIsRelativeToRoot(final String fileName) {
        if (fileName.length() > 2 && (fileName.charAt(0) == '/' || fileName.charAt(0) == '\\' || fileName.charAt(1) == ':')) {
            log.warn("Absolute path: {}", fileName);
            throw new GorResourceException("Absolute paths for files are not allowed!", fileName);
        }
        if (!isPathConstraintWithInRoot(fileName)) {
            throw new GorResourceException("Filepaths are not allowed to reference parent folders!", fileName);
        }
    }

    static boolean isPathConstraintWithInRoot(String ipath) {
        final String path = ipath.replace('\\', '/');
        final ArrayList<String> parts = StringUtil.split(path, '/');
        int last = 0;
        for (String part : parts) {
            if (part.equals(".")) {
                /* do nothing */
                continue;
            } else if (part.equals("..")) {
                last -= 1;
                if (last < 0) { // dot dots have traversed above the root, not allowed
                    log.info("Path not constraint within root: {}", ipath);
                    return false;
                }
            } else {
                last += 1;
            }
        }
        return true; // Path is constrained within the root
    }

    private static String[] extractQuotedFiles(String[] files) {
        List<String> processedFiles = new ArrayList<>();
        for (String file : files){
            if (file != null && !file.isEmpty()) {
                if (file.charAt(0) == '\"' || file.charAt(0) == '\'') {
                    final char lastChr = file.charAt(file.length() - 1);
                    final String name = file.substring(1, lastChr == '\"' || lastChr == '\'' ? file.length() - 1 : file.length());
                    processedFiles.add(name);
                } else {
                    processedFiles.add(file);
                }
            } else {
                throw new GorResourceException("Empty filename found when processing gor input", file);
            }
        }

        return processedFiles.toArray(String[]::new);
    }

    public static GorOptions createGorOptions(String... args) {
        return createGorOptions(String.join(" ", args));
    }

    public static GorOptions createGorOptions(String query) {
        return createGorOptions(query, org.gorpipe.gor.session.ProjectContext.DEFAULT_READER);
    }

    public static GorOptions createGorOptions(String query, org.gorpipe.gor.model.FileReader fileReader) {
        String[] arguments = CommandParseUtilities.quoteSafeSplit(query, ' ');
        GorSession session = new GorSession("-1");
        SystemContext systemContext = new SystemContext.Builder().build();
        org.gorpipe.gor.session.ProjectContext projectContext = new org.gorpipe.gor.session.ProjectContext.Builder().setFileReader(fileReader).build();
        GorSessionCache gorSessionCache = new GorSessionCache();
        session.init(projectContext,systemContext,gorSessionCache);
        return createGorOptions(session.getGorContext(), arguments);
    }

    public static GorOptions createGorOptions(GorContext context, String[] arguments) {
        Tuple2<String[], String[]> result =  CommandParseUtilities.validateCommandArguments(arguments, new CommandArguments("-fs -nf -Y -P -stdin -n -nowithin",
                "-f -ff -m -M -p -sc -ref -idx -Z -H -z -X -s -b -dict -parts -seek", -1, -1, false));

        String[] inputArguments = result._1;
        String[] illegalArguments = result._2;

        if (illegalArguments.length > 0) {
            // We have invalid arguments and need to throw exception
            throw new GorParsingException(String.format("Invalid arguments in gor query. Argument(s) %1$s not a part of this command", String.join(",", illegalArguments)));
        }

        return new GorOptions(context, extractQuotedFiles(inputArguments), arguments);
    }

    /**
     * Parse GorOptions from the option map and files list
     *
     * @param context             The associated context object, or null if not used
     * @param iargs             Input arguments
     * @param options
     */
    public GorOptions(GorContext context, String[] iargs, String[] options) {
        this.context = context;
        this.session = context.getSession();
        this. commonRoot = context.getSession().getProjectContext().getFileReader().getCommonRoot();
        boolean insertSourceOpt = false;

        // Non null iff list of input files was read from a file
        String tagfile = null;

        boolean silentTagFilter = CommandParseUtilities.hasOption(options, "-fs");
        boolean noLineFilter = CommandParseUtilities.hasOption(options, "-nf");
        boolean allowBucketAccess = !CommandParseUtilities.hasOption(options, "-Y");
        boolean hasTagFiltering = false;
        enforceResourceRelativeToRoot = CommandParseUtilities.hasOption (options, "-P");
        String tmpIdxFile = CommandParseUtilities.stringValueOfOptionWithDefault(options, "-idx", null);
        String tmpRefFile = CommandParseUtilities.stringValueOfOptionWithDefault(options, "-ref", null);
        String monId = CommandParseUtilities.stringValueOfOptionWithDefault(options, "-H", null);
        String securityKey = CommandParseUtilities.replaceSingleQuotes(CommandParseUtilities.stringValueOfOptionWithDefault(options, "-Z", null));
        int tmpParallelBlocks = CommandParseUtilities.intValueOfOptionWithDefault(options, "-z", 0);

        this.columnTags = tagsFromOptions(session, options);
        hasTagFiltering = this.columnTags != null;

        if (CommandParseUtilities.hasOption(options, "-s")) {
            insertSourceOpt = true;
            try {
                this.sourceColName = stringValueOfOptionWithDefault(options, "-s", null);
            } catch (GorParsingException gpe) {
                log.debug("Error parsing -s", gpe);
            }
        }

        if (securityKey == null) {
            securityKey = System.getProperty("gor.security.context");

            if (securityKey == null && session != null && session.getProjectContext() != null
                    && session.getProjectContext().getFileReader().getSecurityContext() != null) {
                securityKey = session.getProjectContext().getFileReader().getSecurityContext();
            }
        }
        this.idxFile = tmpIdxFile;
        this.refFile = tmpRefFile;

        this.parallelBlocks = tmpParallelBlocks;
        this.monitor = monId != null ? ResourceMonitor.find(monId) : null;


        if (CommandParseUtilities.hasOption(options, "-p")) {
            // Need to get the last range if multiple ranges are given
            String[] allRanges = CommandParseUtilities.stringArrayOfOption(options, "-p");
            String rangeSource = extractNonQuotedText(allRanges[allRanges.length - 1], 0);
            GenomicRange.Range range = CommandParseUtilities.parseRange(rangeSource);
            if(StringUtil.isAllDigit(range.chromosome())) {
                this.chromo = Integer.parseInt(range.chromosome());
            } else {
                this.chromo = chrcache.prefixToChromosomeIdOrUnknown(range.chromosome() + "\t", true);
            }
            this.begin = range.start();
            this.end = range.stop();
            this.chrname = range.chromosome();
        } else {
            this.chromo = -1;
            this.begin = 0;
            this.end = Integer.MAX_VALUE;
            this.chrname = null;
        }

        this.isSilentTagFilter = silentTagFilter;
        this.isNoLineFilter = noLineFilter;

        // Resolve sources, must be done after all fields are populated
        ProjectContext projectContext = new ProjectContext(securityKey, commonRoot);

        final HashSet<String> alltags = new HashSet<>(); // Collect all tags in parsed dictionary files into one set
        resolveSources(iargs, projectContext, allowBucketAccess, alltags);

        // dictionary files will always insert sources
        if (insertSourceOpt || hasLocalDictonaryFile || hasTagFiltering) {
            // Extract an unique file name part for the file. Used in source ref to get name for the file (used for example in getting sensible column names).
            // This is only used if alias is not defined.
            setUniqueSourceNames();
        }

        // Moved this assignment to last, so that dictionary discovery will correctly trigger insert source
        this.insertSource = insertSourceOpt
                || (!isNoLineFilter && (hasTagFiltering || hasLocalDictonaryFile));
    }

    public GenomicIterator getIterator() {
        return getIterator(null);
    }

    public GenomicIterator getIterator(GorMonitor gm) {
        List<GenomicIterator> genomicIterators = getIterators();

        GenomicIterator theIterator;
        if(genomicIterators.size() > 1 || insertSource) {
            theIterator = new MergeIterator(genomicIterators, insertSource, sourceColName, gm);
        } else {
            theIterator = genomicIterators.get(0);
            if (theIterator instanceof RangeMergeIterator || theIterator instanceof GorpIterator) {
                theIterator = theIterator.filter(r -> !r.isProgress);
            }
        }

        if (sourceName != null) {
            theIterator.setSourceName(sourceName);
        }

        theIterator.setContext(context);
        if (context != null) {
            context.iteratorCreated(toString());
        }

        if (chrname != null && !chrname.equals("")) {
            theIterator = new BoundedIterator(theIterator, chrname, begin, end);
        }
        return theIterator;
    }

    List<GenomicIterator> getIterators() {
        Stream<SourceRef> inRange = files.stream().filter(ref -> chrname == null || ref.isInRange(chrname, begin, end));
        Stream<SourceRef> withTag = inRange.filter( ref -> columnTags == null || ref.analyzeQueryTags(columnTags, insertSource) != SourceRef.NO_TAG);

        // Prepare the driver frameworks for the files
        Stream<SourceRef> preparedSources = prepareSources(withTag);

        Stream<GenomicIterator> iteratorStream = preparedSources.parallel().map(this::createGenomicIteratorFromRef);

        List<GenomicIterator> genomicIterators = iteratorStream.collect(Collectors.toList());

        if (genomicIterators.isEmpty()) {
            // No iterator in range, add dummy one (that will not return any rows) as we must return at least one.
            if (!files.isEmpty()) {
                SourceRef ref = files.get(0);
                genomicIterators.add(new BoundedIterator(createGenomicIteratorFromRef(ref), "", 0, "", 0));
            } else if(tableHeader!=null) {
                genomicIterators.add(new EmptyIterator(tableHeader));
            } else {
                throw new GorDataException("Dictionary " + sourceName + " has no active lines.");
            }
        }
        return genomicIterators;
    }

    private Stream<SourceRef> prepareSources(Stream<SourceRef> sources) {
        if (this.session != null) {
            return this.session.getProjectContext().getFileReader().prepareSources(sources);
        } else {
            return sources;
        }
    }

    private GenomicIterator createGenomicIteratorFromRef(SourceRef ref) {
        GorSession.currentSession.set(session);
        GenomicIterator i;
        try {
            i = ref.iterate(new DefaultChromoLookup(), session);
        } catch (IOException e) {
            throw new GorResourceException("Couldn't open file", ref.getName(), e);
        }
        i.init(getSession());
        i.setSourceName(ref.getName());
        final byte tagStatus = columnTags == null ? SourceRef.NO_TAG : ref.analyzeQueryTags(columnTags, insertSource);
        if (!isNoLineFilter && tagStatus == SourceRef.POSSIBLE_TAG) {
            final int tagColIdx = i.getHeader().split("\t").length - 1;
            final RowFilter rf;
            if (!columnTags.isEmpty()) {
                final RowFilter inf = new InFilter(tagColIdx, this.columnTags);
                if (ref.deletedTags != null && !ref.deletedTags.isEmpty()) {
                    rf = inf.and(new InFilter(tagColIdx, ref.deletedTags).not());
                } else {
                    rf = inf;
                }
                i = i.filter(rf);
            }
        }
        i.setSourceAlreadyInserted(ref.sourceAlreadyInserted);

        return i;
    }

    // TODO:  Merge this with AnalysisUtilites.getFilterTags
    public static Set<String> tagsFromOptions(GorSession session, String[] options) {
        Set<String> tags = null;
        if (CommandParseUtilities.hasOption(options, "-ff")) {
            var tagFile = CommandParseUtilities.stringValueOfOption(options, "-ff");
            tags = loadAndCacheTags(session, options, tagFile);
        } else if (CommandParseUtilities.hasOption(options, "-f")) {
            tags = Arrays.stream(
                    CommandParseUtilities.quoteSafeSplit(
                        CommandParseUtilities.stringValueOfOption(options, "-f")
                             , ','))
                    //.split(",",-1))
                    .map(tag -> CommandParseUtilities.replaceSingleQuotes(tag))
                    .collect(Collectors.toSet());

//            String taglist =  CommandParseUtilities.stringValueOfOption(options, "-f")
//                    .replace("'", "")
//                    .replace("\"", ""); // Incase ' is used to qoute strings, just remove it
//            tags = new HashSet<>(StringUtil.split(taglist, 0, ','));
        }
        return tags;
    }

    // Load tags from cache, nested query or file (and cache the results)
    public static Set<String> loadAndCacheTags(GorSession session, String[] iargs, String tagfile) {
        Set<String> tags = null;
        if (tagfile != null) { // Parse the specified tag file for tag values to filter on
            try {
                String key = String.join("", iargs);

                if (CommandParseUtilities.isNestedCommand(tagfile)) {
                    String iteratorCommand = CommandParseUtilities.parseNestedCommand(tagfile);
                    key += iteratorCommand.hashCode();
                    Option<Set<String>> opt = MapAndListUtilities.syncGetSet(key, session);
                    if (opt.isDefined()) {
                        tags = opt.get();
                    } else {
                        tags = new LinkedHashSet<>();
                        loadTagsFromIterator(session.getGorContext(), tags, iteratorCommand);
                        MapAndListUtilities.syncAddSet(key, tags, session);
                    }
                } else {
                    key += tagfile;
                    Option<Set<String>> opt = session != null ? MapAndListUtilities.syncGetSet(key, session) : Option.empty();
                    if (opt.isDefined()) {
                        tags = opt.get();
                    } else {
                        tags = readTagsFromFile(session, tagfile);
                        if (session != null) MapAndListUtilities.syncAddSet(key, tags, session);
                    }
                }
            } catch (FileNotFoundException e) {
                throw new GorResourceException(ERROR_INITIALIZING_QUERY + "Tag file " + tagfile + IS_NOT_FOUND, tagfile);
            } catch (IOException e) {
                throw new GorSystemException(ERROR_INITIALIZING_QUERY + "Tag file " + tagfile + " can not be read!", e);
            }
        }
        return tags;
    }

    private static void loadTagsFromIterator(GorContext context, Set<String> tags, String iteratorCommand) {
        try (GenomicIterator dSource = new DynIterator.DynamicRowSource(iteratorCommand, context, true)) {
            while (dSource.hasNext()) {
                final String line = dSource.next().toString();
                final int i = line.indexOf('\t', line.indexOf('\t') + 1);
                final int l = line.indexOf('\t', i + 1);
                String tag = line.substring(i + 1, l == -1 ? line.length() : l);
                tags.add(tag);
            }
        }
    }

    private String extractNonQuotedText(String option, int start) {
        if (option.charAt(start) == '\"') {
            return option.substring(start + 1, option.charAt(option.length() - 1) == '\"' ? option.length() - 1 : option.length());
        }
        return option.substring(start, option.charAt(option.length() - 1) == '\"' ? option.length() - 1 : option.length());
    }

    /**
     * Note must be called at the end of constructor when all fields have been set, or optionally from createFilter
     * as the second last thing in the constructor, and in that case the filter expression will be wrong
     */
    private void resolveSources(String[] fileList, ProjectContext projectContext, boolean allowBucketAccess, Set<String> alltags) {
        sourceName = String.join(":", fileList);

        // Start by resolve simple filelists
        for (final String file : fileList) {
            createStandardSources(projectContext, allowBucketAccess, alltags, file);
        }
    }

    private void createStandardSources(ProjectContext projectContext, boolean allowBucketAccess, Set<String> alltags, String file) {
        final ArrayList<String> parts = StringUtil.split(file);
        final int length = parts.size();
        if (length == 1) { // Simple file name
            if (file.equals("-")) {
                files.add(SourceRef.STANDARD_IN);
            } else {
                addSourceRef(file, projectContext, allowBucketAccess, alltags);
            }
        } else {
            Dictionary.DictionaryLine sf = Dictionary.parseDictionaryLine(file, null, file);

            if (sf != null) {
            hasLocalDictonaryFile = hasLocalDictonaryFile || !Strings.isNullOrEmpty(sf.alias) || (sf.tags != null && !sf.tags.isEmpty()) ;
            addSourceRef(sf.fileRef.physical, sf.fileRef.logical, sf.fileRef.isAcceptedAbsoluteRef, projectContext,
                    sf.alias, sf.startChr, sf.startPos, sf.stopChr, sf.stopPos, sf.tags, allowBucketAccess, alltags, isSilentTagFilter);
            }
        }
    }

    /*
      Updates the files (sourceRefs) with a unique file name part (in case we need to add source column.)
     */
    private void setUniqueSourceNames() {
        // Keep and index array with the last checked position for /, from the right
        final int[] indices = new int[files.size()];
        for (int i = 0; i < files.size(); i++) {
            indices[i] = files.get(i).file.length() - 1; // Initialize at the end of the stream
        }
        final HashMap<String, SourceRef> map = new HashMap<>();
        boolean isUnique = false;
        while ( !isUnique ) {
            // Haven't found a unique part, advance index left to the next slash
            map.clear();
            boolean foundDuplicate = false;
            for (int i = 0; i < files.size(); i++) {
                final SourceRef source = files.get(i);
                if (source.alias == null) {
                    if (indices[i] != -1) {
                        int index = source.file.lastIndexOf('/', indices[i] - 1);
                        indices[i] = index != indices[i] ? index : -1;
                    }
                    String key = source.file.substring(indices[i] + 1);
                    String originalKey = key;
                    for (int k = 2; indices[i] == -1 && map.containsKey(key); k++) {
                        key = originalKey + "_" + k; // Filepath is not unique, lets suffix it with a unique number, making it unique
                    }
                    if (map.containsKey(key)) {
                        foundDuplicate = true;
                    }
                    map.put(key, source);
                }
            }
            isUnique = !foundDuplicate;
        }

        // The map contains the unique names to use for each SourceRef
        for (Map.Entry<String, SourceRef> entry : map.entrySet()) {
            entry.getValue().setUniqueFileNamePart(entry.getKey());
        }
    }

    public static Set<String> readTagsFromFile(GorSession session, String filename) throws IOException {
        final boolean isNorFile = filename.toLowerCase().endsWith("nor");
        final Set<String> set = new LinkedHashSet<>();
        try (Stream<String> stream = session.getProjectContext().getFileReader().readFile(filename)) {
                stream.filter(line -> !line.startsWith("#"))
                    .map(line -> {
                        final int beginIdx = isNorFile ? line.indexOf('\t', line.indexOf('\t') + 1) : 0;
                        final int endIdx = line.indexOf('\t', beginIdx);
                        return endIdx == -1 ? line.substring(beginIdx) : line.substring(beginIdx, endIdx);
                    })
                    .map(String::trim)
                    .forEach(set::add);
        }
        return Collections.unmodifiableSet(set);
    }

    @Override
    public String toString() {
        // Convert the options into a command line string
        final StringBuilder text = new StringBuilder();

        // Specify source column tags, if any
        if (columnTags != null && !columnTags.isEmpty()) {
            appendWithSep(text, " ", "-f ");
            int cnt = 0;
            for (String tag : columnTags) {
                if (cnt++ != 0) {
                    text.append(",");
                }
                text.append(tag);
            }
        }

        // Extra filter flags
        if (isSilentTagFilter) {
            appendWithSep(text, " ", "-fs");
        }
        if (isNoLineFilter) {
            appendWithSep(text, " ", "-nf");
        }

        if (insertSource) {
            appendWithSep(text, " ", "-s ");
            if (sourceColName != null) {
                text.append(sourceColName);
            }
        }

        if (chromo != -1) {
            appendWithSep(text, " ", "-p " + chromo + ":" + begin + "-" + end);
        }

        // Add files, including alias and tags
        for (SourceRef source : files) {
            if (!text.isEmpty()) {
                text.append(" ");
            }
            String file = source.file;
            if (commonRoot != null && file.startsWith(commonRoot)) {
                file = file.substring(commonRoot.length());
            }
            text.append(file);
        }
        return text.toString();
    }

    private static void appendWithSep(StringBuilder sb, String sep, String text) {
        if (sb.length() > 0) {
            sb.append(sep);
        }
        sb.append(text);
    }

    private void addSourceRef(String file, ProjectContext projectContext, boolean allowBucketAccess, Set<String> alltags) {
        // A call to this can only take place at the end of a constructor, when all parameters have been set
        addSourceRef(file, file, session.getProjectContext().getFileReader().allowsAbsolutePaths(), projectContext, null, null, -1, null, -1, null, allowBucketAccess, alltags, isSilentTagFilter);
    }

    private void addSourceRef(String physicalFile, String logicalFile, boolean isAcceptedAbsoluteRef, ProjectContext projectContext, String alias,
                              String startChr, int startPos, String stopChr, int stopPos, Set<String> tags, boolean allowBucketAccess, Set<String> alltags, boolean isSilentTagFilter) {
        final String lowerfile = physicalFile.toLowerCase();
        final boolean isDictionary = DataUtil.isGord(lowerfile) && !lowerfile.startsWith("mem:");
        final boolean isBGenMultiFile = lowerfile.matches(DataUtil.toFile(".*#\\{.*\\}.*\\", DataType.BGEN));

        // Windows full path hack
        if (physicalFile.length() > 2 && physicalFile.charAt(1) == ':' && Util.isWindowsOS() ) {
            physicalFile = "file://" + physicalFile;
        }

        final String fileName = getFullPath(physicalFile, logicalFile, isAcceptedAbsoluteRef);
        final String indexFileName = getFullPath(idxFile, idxFile, isAcceptedAbsoluteRef);
        final String referenceFileName = getFullPath(refFile, refFile, isAcceptedAbsoluteRef);

        if (isDictionary) {
            // Must decide how to treat range and tag information for dictionary files
            try {
                if (useTable) {
                    processDictionaryTable(fileName, allowBucketAccess, projectContext, isSilentTagFilter, alltags);
                } else {
                    processDictionary(fileName, allowBucketAccess, projectContext, isSilentTagFilter, alltags);
                }
            } catch (NoSuchFileException nsfe) {
                throw new GorResourceException(String.format("Dictionary file '%s' was not found", fileName), fileName, nsfe);
            } catch (IOException e) {
                throw new GorSystemException(e);
            }
        } else if (isBGenMultiFile) {
            processBGenMultiFile(fileName, indexFileName, referenceFileName, alias, tags, projectContext);
        } else {
            files.add(new SourceRef(fileName, indexFileName, referenceFileName, alias, startChr, startPos, stopChr, stopPos, tags, false, projectContext.securityKey, projectContext.commonRoot));
        }
    }

    private String getFullPath(String physicalFile, String logicalFile, boolean isAcceptedAbsoluteRef) {

        // Test if input is empty
        if (StringUtil.isEmpty(physicalFile)) return physicalFile;

        physicalFile = PathUtils.fixFileSchema(PathUtils.convertSlashes(physicalFile));
        return !isAcceptedAbsoluteRef && commonRoot != null && (enforceResourceRelativeToRoot || !PathUtils.isAbsolutePath(physicalFile)) ?
                concatFolderFile(commonRoot, physicalFile, logicalFile, enforceResourceRelativeToRoot) : physicalFile;
    }

    private void processBGenMultiFile(String fileName, String indexFileName, String referenceFileName, String alias, Set<String> tags, ProjectContext projectContext) {
        final String[] chromos = {"1", "10", "11", "12",
                "13", "14", "15", "16", "17", "18", "19", "2", "20", "21", "22", "3", "4", "5", "6", "7", "8", "9", "M", "X", "Y"};
        final Matcher matcher = Pattern.compile("#\\{.*\\}").matcher(fileName);
        matcher.find();
        final int internalBegin = matcher.start();
        final int internalEnd = matcher.end();
        assert !matcher.find();

        final String prefix = fileName.substring(0, internalBegin);
        final String suffix = fileName.substring(internalEnd);
        for (String chr : chromos) {
            final String path = prefix + chr + suffix;
            if (Files.isRegularFile(Paths.get(path))) {
                this.files.add(new SourceRef(path, indexFileName, referenceFileName, alias, "chr" + chr,
                        0, "chr" + chr, 1000000000, tags, false,
                        projectContext.securityKey, projectContext.commonRoot));
            }
        }
    }

    private Path resolveFolderDict(Path gordPath) {
        Path gorDictPath = gordPath.resolve(gordPath.getFileName());
        if (!Files.exists(gorDictPath)) {
            return gordPath.resolve(DEFAULT_FOLDER_DICTIONARY_NAME);
        }
        return gorDictPath;
    }

    private void processDictionary(String fileName, boolean allowBucketAccess, ProjectContext projectContext, boolean isSilentTagFilter, Set<String> alltags) throws IOException {
        DictionaryTableReader table = GorDictionaryCache.dictCache.getTable(fileName, session.getProjectContext().getFileReader());
        final Dictionary gord = Dictionary.getDictionary(table, this.useDictionaryCache);

        isNoLineFilter = isNoLineFilter || !table.getLineFilter();
        this.hasLocalDictonaryFile = hasLocalDictonaryFile || !gord.getValidTags().isEmpty() /*!table.getAllActiveTags().isEmpty()*/;  // Does not count as dictionary if no tags

        final Dictionary.DictionaryLine[] fileList = gord.getSources(this.columnTags, allowBucketAccess, isSilentTagFilter);
        this.isDictionaryWithBuckets = gord.isDictionaryWithBuckets; //Arrays.stream(fileList).anyMatch(file -> file.sourceInserted);
        final boolean hasTags = !(this.columnTags == null || this.columnTags.isEmpty());
        if (!hasTags && gord.getAnyBucketHasDeletedFile()) {
            if (this.columnTags == null) this.columnTags = new HashSet<>(gord.getValidTags());
            else this.columnTags.addAll(gord.getValidTags());
        }
        for (Dictionary.DictionaryLine line : fileList) {
            subProcessOfProcessDictionary(line, allowBucketAccess, projectContext, alltags,
                    gord.getBucketDeletedFiles(Paths.get(line.fileRef.logical).getFileName().toString()), true );
        }

        if (sourceColName == null) {
            // Note:  if multiple dicts or dicts and files the first dict with source column defined will
            //        determine the source column name.
            sourceColName = table.getProperty(GorDictionaryTableMeta.HEADER_SOURCE_COLUMN_KEY);
        }

        if (tableHeader == null) {
            tableHeader = table.getProperty(TableHeader.HEADER_COLUMNS_KEY);
            if (tableHeader!=null) tableHeader = tableHeader.replace(',','\t');
        }
    }

    private void subProcessOfProcessDictionary(Dictionary.DictionaryLine dictionaryLine, boolean allowBucketAccess, ProjectContext projectContext, Set<String> alltags, Collection<String> deletedTags, boolean isSilentTagFilter) {
        if (dictionaryLine.sourceInserted) {
            files.add(new SourceRef(dictionaryLine.fileRef.logical, dictionaryLine.alias, null, null,
                    dictionaryLine.startChr, dictionaryLine.startPos, dictionaryLine.stopChr, dictionaryLine.stopPos,
                    dictionaryLine.tags, deletedTags, dictionaryLine.sourceInserted,
                    projectContext.securityKey, projectContext.commonRoot));
        } else {
            addSourceRef(dictionaryLine.fileRef.physical, dictionaryLine.fileRef.logical, dictionaryLine.fileRef.isAcceptedAbsoluteRef, projectContext,
                    dictionaryLine.alias, dictionaryLine.startChr, dictionaryLine.startPos, dictionaryLine.stopChr, dictionaryLine.stopPos, dictionaryLine.tags, allowBucketAccess, alltags, isSilentTagFilter);
        }
    }

    private void processDictionaryTable(String fileName, boolean allowBucketAccess, ProjectContext projectContext, boolean isSilentTagFilter, Set<String> alltags) throws IOException {
        GorDictionaryTable table = GorDictionaryCache.dictCache.getTable(fileName, session.getProjectContext().getFileReader());

        this.isNoLineFilter = isNoLineFilter || !table.getLineFilter();
        this.hasLocalDictonaryFile = hasLocalDictonaryFile || !table.getAllActiveTags().isEmpty() /*!table.getAllActiveTags().isEmpty()*/;  // Does not count as dictionary if no tags

        final List<GorDictionaryEntry> fileList = table.getOptimizedLines(this.columnTags, allowBucketAccess, isSilentTagFilter);
        this.isDictionaryWithBuckets = table.hasBuckets();

        final boolean hasTags = !(this.columnTags == null || this.columnTags.isEmpty());
        if (!hasTags && table.hasDeletedEntries()) {
            if (this.columnTags == null) {
                this.columnTags = new HashSet<>(table.getAllActiveTags());
            } else {
                this.columnTags.addAll(table.getAllActiveTags());
            }
        }

        if (sourceColName == null) {
            // Note:  if multiple dicts or dicts and files the first dict with source column defined will
            //        determine the source column name.
            sourceColName = table.getProperty(GorDictionaryTableMeta.HEADER_SOURCE_COLUMN_KEY);
        }

        if (tableHeader == null) {
            tableHeader = table.getProperty(TableHeader.HEADER_COLUMNS_KEY);
            if (tableHeader!=null) tableHeader = tableHeader.replace(',','\t');
        }

        for (GorDictionaryEntry line : fileList) {
            subProcessOfProcessDictionaryTable(table, line, allowBucketAccess, projectContext, alltags,
                    table.getBucketDeletedFiles(PathUtils.getFileName(line.getContent())), true );
        }
    }

    private void subProcessOfProcessDictionaryTable(GorDictionaryTable table, GorDictionaryEntry dictionaryLine, boolean allowBucketAccess, ProjectContext projectContext, Set<String> alltags, Collection<String> deletedTags, boolean isSilentTagFilter) {
        String contentReal = table.getContentProjectRelative(dictionaryLine);

        if (dictionaryLine.isSourceInserted()) {
            files.add(new SourceRef(contentReal, dictionaryLine.getAlias(), null, null,
                    dictionaryLine.getRange().getStartChr(), dictionaryLine.getRange().getStartPos(), dictionaryLine.getRange().getStopChr(), dictionaryLine.getRange().getStopPos(),
                    new HashSet<>(Arrays.asList(dictionaryLine.getTags())), deletedTags, dictionaryLine.isSourceInserted(),
                    projectContext.securityKey, projectContext.commonRoot));
        } else {
            addSourceRef(contentReal, contentReal, false, projectContext,
                    dictionaryLine.getAlias(), dictionaryLine.getRange().getStartChr(), dictionaryLine.getRange().getStartPos(), dictionaryLine.getRange().getStopChr(), dictionaryLine.getRange().getStopPos(),
                    new HashSet<>(Arrays.asList(dictionaryLine.getTags())), allowBucketAccess, alltags, isSilentTagFilter);
        }
    }

    public static String getFileSignature(DataSource source) throws IOException {
        if (source != null) {
            return source.getSourceMetadata().getUniqueId();
        } else {
            return Util.md5(Long.toString(System.currentTimeMillis()));
        }
    }


    static String concatFolderFile(String folder, String physical, String logical, boolean checkRelativeToRoot) {
        if (physical.contains("://")) { // The file is not a filesystem reference, so do not apply common file system root
            return physical;
        }
        if (checkRelativeToRoot) { // Need to ensure that the file doesn't refer above the root in the file hierarchy
            checkFileNameIsRelativeToRoot(logical);
        }

        if (Path.of(physical).isAbsolute()) {
            return physical;
        }

        return folder.endsWith("/") || folder.endsWith("\\") ? folder + physical : folder + '/' + physical;
    }
}
