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

import org.gorpipe.exceptions.GorSystemException;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * An iterator which takes in genomic iterators with a specified range. The iterator merges
 * the data from the underlying iterators with minimum load on the file system, by keeping a
 * minimum number of files open at a time.
 *
 * Notes on implementation and usage:
 *
 * 1) We refer to the sources with their index in the list {@code references}
 *
 * 2) We keep a queue containing the indices of the sources that have not yet been opened.
 *    The indices are ordered according to lower bounds of ranges of the corresponding sources.
 *
 * 3) We keep a queue containing the indices of the sources which are opened. The indices are ordered
 *    by the next row from the corresponding iterator.
 *
 * 4) When we must return the next row, we check whether we have something in the queue of rows from active iterators.
 *    If that is not the case we activate a new iterator if possible and start again. Else we take the first row and
 *    check whether it is below the lowest lower bound of the waiting iterators. If that is the case, we return it, else
 *    we read from a new iterator if possible and start again.
 *
 * 5) When our queue of rows from active iterators is empty or its first line is beyond the lowest lower bound of
 *    unopened sources, we send a progress row with the lowest lower bound. If the user asks for more lines, then we
 *    open all the sources whose lower bound is equal to the lowest lower bound (we do not send many progress rows
 *    although we might be opening many iterators starting at the same locus.)
 *
 * 6) In order to make sure that the progress rows do not enter the analysis steps in GORpipe, one should either use
 *    this class from MergeIterator or apply a filter to it in the following way:
 *    
 *    {@code final GenomicIterator git = new RangeMergeIterator(sources).filter(r -> !r.isProgress);}
 *
 * @author Hjalti Thor Isleifsson
 */
public class RangeMergeIterator extends GenomicIteratorBase {
    private final int numberOfSources;
    private final List<SourceRef> sources;
    private final GenomicIterator[] iterators;
    private final Row[] rows;
    private final Queue<Integer> waitingIterators;
    private final Queue<Integer> waitingRows;
    private String waitingChr;
    private int waitingPos;
    private boolean mustReport = false;
    private boolean progressReported = false;
    private Row progressRow;
    private Predicate<Row> rf;
    private int[] cols;

    public RangeMergeIterator(List<SourceRef> references) {
        this.numberOfSources = references.size();
        if (this.numberOfSources == 0) {
            throw new IllegalArgumentException("There must be at least one source");
        }
        this.sources = references;
        this.iterators = new GenomicIterator[this.numberOfSources];
        this.rows = new Row[this.numberOfSources];
        this.waitingIterators = new PriorityQueue<>(this.numberOfSources, getInComparator());
        this.waitingRows = new PriorityQueue<>(getWaitingRowComparator());
        IntStream.range(0, this.numberOfSources).forEach(this.waitingIterators::add);
        updateWaitingBound();
    }

    private Comparator<Integer> getWaitingRowComparator() {
        return (o1, o2) -> {
            if (o1.equals(o2)) return 0;
            final Row r1 = this.rows[o1];
            final Row r2 = this.rows[o2];
            final int chrCmp = r1.chr.compareTo(r2.chr);
            if (chrCmp != 0) return chrCmp;
            final int posCmp = Integer.compare(r1.pos, r2.pos);
            if (posCmp != 0) return posCmp;
            return Integer.compare(o1, o2);
        };
    }

    private Comparator<Integer> getInComparator() {
        return (o1, o2) -> {
            if (o1.equals(o2)) return 0;

            final SourceRef ref1 = sources.get(o1);
            final SourceRef ref2 = sources.get(o2);

            final int chrCmp = ref1.startChr.compareTo(ref2.startChr);
            if (chrCmp != 0) return chrCmp;
            final int posCmp = Integer.compare(ref1.startPos, ref2.startPos);
            if (posCmp != 0) return posCmp;
            return Integer.compare(o1, o2);
        };
    }

    private void updateQueue() throws IOException {
        if (mustActivateNew()) {
            if (this.progressReported) {
                final String oldChr = this.waitingChr;
                final int oldPos = this.waitingPos;
                do {
                    activateNextIterator();
                } while (this.waitingIterators.size() > 0 && this.waitingChr.equals(oldChr) && this.waitingPos == oldPos);
                this.mustReport = mustActivateNew();
                this.progressReported = false;
            } else {
                this.mustReport = true;
            }
        }
    }

    private void activateNextIterator() throws IOException {
        final int next = this.waitingIterators.poll();
        final GenomicIterator nextIt = getIterator(next);
        if (nextIt.hasNext()) {
            this.rows[next] = nextIt.next();
            this.iterators[next] = nextIt;
            this.waitingRows.add(next);
        } else {
            nextIt.close();
        }
        updateWaitingBound();
    }

    private GenomicIterator getIterator(int idx) throws IOException {
        GenomicIterator nextIt = this.sources.get(idx).iterate(new DefaultChromoLookup(),
                getContext() != null ? getContext().getSession() : null);
        nextIt.init(null);
        if (this.rf != null) {
            nextIt = nextIt.filter(this.rf);
        }
        if (this.cols != null) {
            nextIt = nextIt.select(this.cols);
        }
        return nextIt;
    }

    private void readFromIterator(int idx) {
        final GenomicIterator it = this.iterators[idx];
        if (it.hasNext()) {
            this.rows[idx] = it.next();
            this.waitingRows.add(idx);
        } else {
            this.rows[idx] = null;
            it.close();
            this.iterators[idx] = null;
        }
    }

    private boolean mustActivateNew() {
        if (this.waitingIterators.isEmpty()) {
            return false;
        } else if (this.waitingRows.isEmpty()) {
            return true;
        } else {
            final int nextIdx = this.waitingRows.peek();
            final Row nextRow = this.rows[nextIdx];
            final String nextChr = nextRow.chr;
            final int nextPos = nextRow.pos;
            final int chrCmp = nextChr.compareTo(this.waitingChr);
            return chrCmp > 0 || (chrCmp == 0 && nextPos >= this.waitingPos);
        }
    }

    private void updateWaitingBound() {
        if (this.waitingIterators.size() > 0) {
            final int then = this.waitingIterators.peek();
            final SourceRef thenRef = this.sources.get(then);
            this.waitingChr = thenRef.startChr;
            this.waitingPos = thenRef.startPos;
        }
    }

    @Override
    public String getHeader() {
        final String candidateHeader = super.getHeader();
        if (candidateHeader == null || candidateHeader.equals("")) {
            if (this.waitingRows.isEmpty()) {
                return activateAndRead();
            } else {
                return this.iterators[this.waitingRows.peek()].getHeader();
            }
        } else {
            return candidateHeader;
        }
    }

    private String activateAndRead() {
        String toReturn;
        try {
            if (this.waitingIterators.isEmpty()) {
                toReturn = getHeaderFromFirst();
            } else {
                toReturn = tryActivateAndThenGetHeader();
            }
        } catch (IOException e) {
            throw new GorSystemException(e);
        }
        return toReturn;
    }

    private String tryActivateAndThenGetHeader() throws IOException {
        this.activateNextIterator();
        if (this.waitingRows.isEmpty()) {
            //This may happen if the iterator does not return anything
            return getHeaderFromFirst();
        } else {
            return this.iterators[this.waitingRows.peek()].getHeader();
        }
    }

    private String getHeaderFromFirst() throws IOException {
        final GenomicIterator git = this.getIterator(0);
        final String toReturn = git.getHeader();
        git.close();
        return toReturn;
    }

    @Override
    public boolean seek(String chr, int pos) {
        this.waitingRows.clear();
        this.waitingIterators.clear();
        IntStream.range(0, this.numberOfSources).filter(i -> {
            final SourceRef sr = this.sources.get(i);
            final int chrCmp = sr.stopChr.compareTo(chr);
            if (chrCmp < 0 || (chrCmp == 0 && sr.stopPos < pos)) {
                if (this.iterators[i] != null) {
                    this.iterators[i].close();
                    this.iterators[i] = null;
                }
                this.rows[i] = null;
                this.waitingIterators.remove(i);
                return false;
            } else {
                return true;
            }
        }).forEach(this.waitingIterators::add);
        updateWaitingBound();

        //Now the waiting queue contains all candidates.
        //Iterators before us are closed (nice).
        //Iterators left might be open (maybe not nice). (Must check whether we should close some of them.)
        try {
            seekToNextIterators(chr, pos);
        } catch (IOException e) {
            throw new GorSystemException(e);
        }
        return this.hasNext();
    }

    private void seekToNextIterators(String chr, int pos) throws IOException {
        while (compareToWaiting(chr, pos) >= 0 && mustActivateNew()) {
            seekToNextIterator(chr, pos);
        }
    }

    private void seekToNextIterator(String chr, int pos) throws IOException {
        if (this.waitingIterators.size() > 0) {
            final int nextItIdx = this.waitingIterators.poll();

            final GenomicIterator nextGIt;
            if (this.iterators[nextItIdx] == null) {
                nextGIt = getIterator(nextItIdx);
            } else {
                nextGIt = this.iterators[nextItIdx];
            }

            nextGIt.seek(chr, pos);
            if (nextGIt.hasNext()) {
                final Row next = nextGIt.next();
                this.rows[nextItIdx] = next;
                this.iterators[nextItIdx] = nextGIt;
                this.waitingRows.add(nextItIdx);
            } else {
                nextGIt.close();
                this.iterators[nextItIdx] = null;
                this.rows[nextItIdx] = null;
            }

            updateWaitingBound();
        }
    }

    @Override
    public boolean hasNext() {
        try {
            updateQueue();
        } catch (IOException e) {
            throw new GorSystemException(e);
        }
        if (this.mustReport) {
            this.progressRow = RowBase.getProgressRow(this.waitingChr, this.waitingPos);
            if (this.rf == null || this.rf.test(this.progressRow)) {
                return true;
            } else {
                this.mustReport = false;
                this.progressReported = true;
                this.progressRow = null;
                return this.hasNext();
            }
        } else {
            return this.waitingRows.size() > 0;
        }
    }

    @Override
    public Row next() {
        if (this.mustReport) {
            this.mustReport = false;
            this.progressReported = true;
            return this.progressRow;
        } else if (this.waitingRows.isEmpty()) {
            throw new IllegalStateException("hasNext must be called before calling next.");
        } else {
            final int itIdx = this.waitingRows.poll();
            final Row row = this.rows[itIdx];
            readFromIterator(itIdx);
            return row;
        }
    }

    @Override
    public void close() {
        for (final GenomicIterator git : this.iterators) {
            if (git != null) {
                git.close();
            }
        }
    }

    @Override
    public GenomicIterator filter(Predicate<Row> rf) {
        this.rf = rf;
        return this;
    }

    private int compareToWaiting(String chr, int pos) {
        return compareKeys(chr, pos, this.waitingChr, this.waitingPos);
    }

    private static int compareKeys(String chr1, int pos1, String chr2, int pos2) {
        final int chrCmp = chr1.compareTo(chr2);
        if (chrCmp != 0) {
            return chrCmp;
        } else {
            return Integer.compare(pos1, pos2);
        }
    }
}
