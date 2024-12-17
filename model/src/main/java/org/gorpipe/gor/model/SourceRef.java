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

import org.gorpipe.exceptions.ExceptionUtilities;
import org.gorpipe.gor.driver.DataSource;
import org.gorpipe.gor.driver.GorDriverFactory;
import org.gorpipe.gor.driver.meta.IndexableSourceReference;
import org.gorpipe.gor.driver.meta.SourceReference;
import org.gorpipe.gor.binsearch.GorSeekableIterator;
import org.gorpipe.gor.session.GorSession;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * FileRef contains a path to a file to be used along with an optional alias to use for the file
 *
 * @version $Id$
 */
public class SourceRef {

    /**
     * Type of the SourceRef, 0 is file, 1 is http URI
     */
    public final byte type;
    /**
     * The name of the file
     */
    public String file;
    /**
     * The name of the index file
     */
    public final String indexFile;
    /**
     * The name of the reference file
     */
    public final String referenceFile;
    /**
     * The chromosome number of the first data line in the file, or -1 if this is not specified.
     */
    public final String startChr;
    /**
     * The position with in the chromosome of the first data line in the file, or -1 if this is not specified.
     */
    public final int startPos;
    /**
     * The chromosome number of the last data line in the file, or -1 if this is not specified.
     */
    public final String stopChr;
    /**
     * The position with in the chromosome of the last data line in the file, or -1 if this is not specified.
     */
    public final int stopPos;
    /**
     * Set of all tags. If a tag is provided it is assumed that all possible tag values for that column are provided.
     */
    public final Set<String> tags;
    /**
     * Set of deleted tags. .
     */
    public final Collection<String> deletedTags;
    /**
     * The alias to use for the file
     */
    public final String alias;
    /**
     * True if source column is already inserted, false otherwise
     */
    public final boolean sourceAlreadyInserted;

    /**
     * The minimum part of the filename, from the right side, to form a unique name
     */
    private String uniqueFileNamePart;

    private final String securityContext; // The client specified security context needed to access the sources
    private final String commonRoot; // The common root to apply to file names, e.g. if specified then link files content will be prefixed with this path

    /**
     * Placeholder for standard input
     */
    public static final SourceRef STANDARD_IN = new SourceRef("StdIn", "in", null);

    /**
     * Given set of tags are not represented in the source
     */
    public static final byte NO_TAG = 0;
    /**
     * There is a possibility that at least one of a given set of tags is represented in the source
     */
    public static final byte POSSIBLE_TAG = 1;
    /**
     * All tags of the source are represented in the given set of query tags
     */
    public static final byte ALL_TAGS = 2;

    /**
     * The SourceRef type of file
     */
    public static final byte TYPE_FILE = 0;
    /**
     * The SourceRef type of http URI
     */
    public static final byte TYPE_HTTP = 1;
    /**
     * The SourceRef type of tcp URI
     */
    public static final byte TYPE_TCP = 2;

    /**
     * Construct
     *
     * @param file
     * @param securityContext The client specified security context to access the sources
     * @param commonRoot      The common root path
     */
    public SourceRef(String file, String securityContext, String commonRoot) {
        this(TYPE_FILE, file, null, false, securityContext, commonRoot);
    }

    /**
     * Construct
     *
     * @param file
     * @param alias
     * @param securityContext The client specified security context to access the sources
     * @param commonRoot      The common root path
     */
    public SourceRef(String file, String alias, String securityContext, String commonRoot) {
        this(TYPE_FILE, file, alias, false, securityContext, commonRoot);
    }

    /**
     * Construct
     *
     * @param type            The type of source, 0 is file, 1 is http URL
     * @param file
     * @param alias
     * @param sourceInserted  True if source column is already inserted into the data
     * @param securityContext The client specified security context to access the sources
     * @param commonRoot      The common root path
     */
    public SourceRef(byte type, String file, String alias, boolean sourceInserted, String securityContext, String commonRoot) {
        this(type, file, null, null, alias, null, -1, null, -1, null, null, sourceInserted, securityContext, commonRoot);
    }

    /**
     * Construct
     *
     * @param file
     * @param alias
     * @param startChr
     * @param startPos
     * @param stopChr
     * @param stopPos
     * @param tags            Any tags associated with a specified column.
     * @param sourceInserted  True if source column is already inserted into the data
     * @param securityContext The client specified security context to access the sources
     * @param commonRoot      The common root path
     */
    public SourceRef(String file, String index, String reference, String alias, String startChr, int startPos, String stopChr, int stopPos, Set<String> tags, boolean sourceInserted, String securityContext, String commonRoot) {
        this(TYPE_FILE, file, index, reference, alias, startChr, startPos, stopChr, stopPos, tags, null, sourceInserted, securityContext, commonRoot);
    }

    /**
     * Construct
     *
     * @param file
     * @param alias
     * @param startChr
     * @param startPos
     * @param stopChr
     * @param stopPos
     * @param tags            Any tags associated with a specified column.
     * @param deletedTags
     * @param sourceInserted  True if source column is already inserted into the data
     * @param securityContext The client specified security context to access the sources
     * @param commonRoot      The common root path
     */
    public SourceRef(String file, String index, String reference, String alias, String startChr, int startPos, String stopChr, int stopPos, Set<String> tags, Collection<String> deletedTags, boolean sourceInserted, String securityContext, String commonRoot) {
        this(TYPE_FILE, file, index, reference, alias, startChr, startPos, stopChr, stopPos, tags, deletedTags, sourceInserted, securityContext, commonRoot);
    }


    /**
     * Construct
     *
     * @param type            The type of source, 0 is file, 1 is http URL
     * @param file
     * @param index
     * @param reference
     * @param alias
     * @param startChr
     * @param startPos
     * @param stopChr
     * @param stopPos
     * @param tags            Any tags associated with a specified column.
     * @param sourceInserted  True if source column is already inserted into the data
     * @param securityContext The client specified security context to access the sources
     * @param commonRoot      The common root path
     */
    public SourceRef(byte type, String file, String index, String reference, String alias, String startChr, int startPos, String stopChr, int stopPos, Set<String> tags, Collection<String> deletedTags, boolean sourceInserted, String securityContext, String commonRoot) {
        assert file != null;
        this.type = type;
        this.file = file.replace('\\', '/');
        this.alias = alias;
        this.startChr = startChr;
        this.startPos = startPos;
        this.stopChr = stopChr;
        this.stopPos = stopPos;
        this.tags = tags;
        this.deletedTags = deletedTags;
        this.sourceAlreadyInserted = sourceInserted;
        this.securityContext = securityContext;
        this.commonRoot = commonRoot;
        this.indexFile = index;
        this.referenceFile = reference;
    }

    @Override
    public String toString() {
        return alias != null ? file + '(' + alias + ')' : file;
    }


    /**
     * @param name The rightmost unique part of the file name to use
     */
    public void setUniqueFileNamePart(String name) {
        this.uniqueFileNamePart = name;
    }

    /**
     * @return True if the file reference is to standard in
     */
    public final boolean isStandardIn() {
        return this == STANDARD_IN;
    }

    /**
     * @return True if the file reference is an http URL
     */
    public final boolean isHttp() {
        return type == 1;
    }

    /**
     * @return True if the source reference is for an remote source
     */
    public final boolean isRemote() {
        return type != 0;
    }

    /**
     * Analyze the specified tags and see how they are represented in the source.
     *
     * @param queryTags  The tags to analyze. Each tag is unique, i.e. the same tag has only one occurrence in the list.
     * @param aliasIsTag If the source has no tag, assume the alias is a tag
     * @return NO_TAG if none of the query tags can exist in the source,
     * ALL_TAG if all the source tags exist in the query tags.
     * POSSIBLE_TAG in all other cases.
     */
    public final byte analyzeQueryTags(Set<String> queryTags, boolean aliasIsTag) {
        return analyzeQueryTags(tags, queryTags, alias, aliasIsTag, deletedTags);
    }

    /**
     * Analyze the specified tags and see how they are represented in the source.
     *
     * @param tags       The tags to compare with
     * @param queryTags  The tags to analyze. Each tag is unique, i.e. the same tag has only one occurrence in the list.
     * @param alias      The alias to use
     * @param aliasIsTag If the source has no tag, assume the alias is a tag
     * @return NO_TAG if none of the query tags can exist in the source,
     * ALL_TAG if all the source tags exist in the query tags.
     * POSSIBLE_TAG in all other cases.
     */
    public static byte analyzeQueryTags(Set<String> tags, Set<String> queryTags, String alias, boolean aliasIsTag, Collection<String> deletedTags) {
        if (tags == null || tags.isEmpty()) {
            if (aliasIsTag && alias != null) {
                if (queryTags.contains(alias) && (deletedTags == null || !deletedTags.contains(alias))) return ALL_TAGS;
                else return NO_TAG;
            } else return POSSIBLE_TAG;
        } else {
            if (tags.size() <= queryTags.size()) {
                boolean all = true, none = true, tmp;
                final Iterator<String> tagIt = tags.iterator();
                while ((all || none) && tagIt.hasNext()) {
                    String tag = tagIt.next();
                    tmp = queryTags.contains(tag) && (deletedTags == null || !deletedTags.contains(tag));
                    all &= tmp;
                    none &= !tmp;
                }
                if (all) return ALL_TAGS;
                else if (none) return NO_TAG;
            } else {
                if (queryTags.stream().allMatch(tag -> !tags.contains(tag))) return NO_TAG;
            }
            return POSSIBLE_TAG;
        }
    }

    /**
     * @param chr   The chromosome id of the chromosome data access is restricted to
     * @param start The start position the data access is restricted to
     * @param stop  The stop position the data access is restricted to
     * @return True if the source might have data in the specified range. False if it does not have data in the range.
     */
    public final boolean isInRange(String chr, int start, int stop) {
        // If source range is prior to the query range, then this source is not in range
        if (stopChr != null && (stopChr.compareTo(chr) < 0 || (stopChr.equals(chr) && stopPos < start)))
            return false;

        // If source range is after the query range, then this source is not in range
        return startChr == null || (startChr.compareTo(chr) <= 0 && (!startChr.equals(chr) || startPos <= stop));
    }

    /**
     * Get the name of the file, which is either the alias of it (if provided) or the filename from the full filepath
     *
     * @return The name of the file
     */
    public String getName() {
        if (alias != null && !alias.isEmpty()) {
            return alias;
        }
        if (uniqueFileNamePart != null) {
            return uniqueFileNamePart;
        }
        int idx = file.lastIndexOf('/', file.length() - 2);
        final int idxFileBegin = idx > 0 ? idx + 1 : 0;
        idx = file.lastIndexOf('.');
        final int idxEndingBegin = idx > idxFileBegin ? idx : file.length();
        return file.substring(idxFileBegin, idxEndingBegin);
    }

    /**
     * Create a new iterator for this FileRef object
     *
     * @param lookup    ChromosomeLookup map
     * @return The GenomicIterator from this source
     * @throws IOException
     */
    public GenomicIterator iterate(ChromoLookup lookup, GorSession session) throws IOException {
        // All files, except specific endings are assumed to be tab delimited text files in genomic order
        // With first two fields as chromosome and position
        if (isStandardIn()) {
            return new StdInGenomicIterator(lookup);
        }

        return iterateFile(file, indexFile, referenceFile, securityContext, commonRoot, lookup, session);
    }

    private static GenomicIterator iterateFile(String file, String index, String reference, String securityContext,
                                               String commonRoot, ChromoLookup lookup, GorSession session) throws IOException {
        try {
            var isMem = file.startsWith("mem:");
            if (!isMem && GorDriverFactory.fromConfig().config().enabled()) {
                SourceReference sourceReference = new IndexableSourceReference(file, index, reference, securityContext, commonRoot, lookup);
                GenomicIterator newIt;
                if (session != null) {
                    // Use the datasource if possible.
                    DataSource dataSource = session.getProjectContext().getFileReader().resolveUrl(sourceReference);
                    newIt = GorDriverFactory.fromConfig().createIterator(dataSource);
                } else {
                    newIt = GorDriverFactory.fromConfig().createIterator(sourceReference);
                }
                if (newIt != null) {
                    return newIt;
                }
            }

            // Issue SM-48  Named in memory table iterator
            if (isMem) {
                String name = file.substring(4);
                return NamedTableGorIterators.getIterator(name);
            } else {
                Path path = Paths.get(file);
                if (!Files.exists(path)) {
                    Path root = Paths.get(commonRoot);
                    path = root.resolve(path);
                }

                if (Files.isDirectory(path)) { // Note this will need to talk to the filesystem so it is relatively costly
                    return new SeqBasesGenomicIterator(path, lookup);
                } else {
                    return new GorSeekableIterator(new RacSeekableFile(new RandomAccessFile(path.toFile(), "r"), file));
                }
            }
        } catch (FileNotFoundException fileNotFoundexception) {

            throw ExceptionUtilities.mapGorResourceException(fileNotFoundexception.getMessage(), file, fileNotFoundexception);
        }
    }

}
