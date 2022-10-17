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

package org.gorpipe.gor.table.util;

import org.gorpipe.exceptions.GorDataException;
import org.gorpipe.exceptions.GorSystemException;

import java.util.Arrays;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Helper class to map genomic range to string and back.
 * <p>
 * Created by gisli on 22/08/16.
 */
public class GenomicRange {

    public static final GenomicRange EMPTY_RANGE = new GenomicRange();

    // How to treat empty values in the string rep.   These values are picked for backward compatibility with existing code.
    private static final String EMPTY_START_CHR = "";
    private static final int EMPTY_START_POS = 0;
    private static final String EMPTY_STOP_CHR = "~"; // ~ is last in ascii.
    private static final int EMPTY_STOP_POS = Integer.MAX_VALUE;

    private final String startChr;
    private final int startPos;
    private final String stopChr;
    private final int stopPos;
    boolean isEmpty;


    public GenomicRange() {
        this(EMPTY_START_CHR, EMPTY_START_POS, EMPTY_STOP_CHR, EMPTY_STOP_POS);
        this.isEmpty = true;

    }

    public GenomicRange(String startChr, int startPos, String stopChr, int stopPos) {
        // TODO:  Add more validation (if pos then chr, start <= stop)
        this.startChr = !isNullOrEmpty(startChr) ? startChr : EMPTY_START_CHR;
        this.startPos = startPos >= 0 ? startPos : EMPTY_START_POS;
        this.stopChr = !isNullOrEmpty(stopChr) ? stopChr : EMPTY_STOP_CHR;
        this.stopPos = stopPos >= 0 ? stopPos : EMPTY_STOP_POS;
        this.isEmpty = false;
    }

    public GenomicRange(String startChr, int startPos) {
        this(startChr, startPos, "", -1);
    }

    public String getStartChr() {
        return startChr;
    }

    public int getStartPos() {
        return startPos;
    }

    public String getStopChr() {
        return stopChr;
    }

    public int getStopPos() {
        return stopPos;
    }

    @Override
    public String toString() {
        return String.format("%s%s%s%s%s%s%s",
                !EMPTY_START_CHR.equals(this.startChr) ? this.startChr : "",
                !EMPTY_START_CHR.equals(this.startChr) && (this.startPos != EMPTY_START_POS ||  this.stopPos != EMPTY_STOP_POS)  ? ":" : "",
                !EMPTY_START_CHR.equals(this.startChr) && (this.startPos != EMPTY_START_POS ||  this.stopPos != EMPTY_STOP_POS)  ? this.startPos : "",
                !EMPTY_STOP_CHR.equals(this.stopChr) || this.stopPos != EMPTY_STOP_POS ? "-" : "",
                !this.startChr.equals(this.stopChr) && !EMPTY_STOP_CHR.equals(this.stopChr)  ? this.stopChr : "",
                !this.startChr.equals(this.stopChr) && !EMPTY_STOP_CHR.equals(this.stopChr) && this.stopPos != EMPTY_STOP_POS ? ":" : "",
                this.stopPos != EMPTY_STOP_POS ? this.stopPos : "");
    }

    public String formatAsTabDelimited() {
        if (isEmpty) return "\t\t\t";
        StringBuilder sb =  new StringBuilder();
        formatAsTabDelimited(sb);
        return sb.toString();
    }

    public StringBuilder formatAsTabDelimited(StringBuilder sb) {
        if (isEmpty) {
            sb.append("\t\t\t");
        } else {
            sb.append(!EMPTY_START_CHR.equals(this.startChr) ? this.startChr : "");
            sb.append('\t');
            sb.append(this.startPos != EMPTY_START_POS || (!EMPTY_START_CHR.equals(this.startChr) && this.startPos >= 0) ? this.startPos : "");
            sb.append('\t');
            sb.append(!EMPTY_STOP_CHR.equals(this.stopChr) ? this.stopChr : "");
            sb.append('\t');
            sb.append(this.stopPos != EMPTY_STOP_POS ? this.stopPos : "");
        }
        return sb;
    }

    /**
     * Parse genomic range string.
     *
     * @param genomicRange string to parse.
     * @return new GenomicRange object constructed from the string.
     */
    public static GenomicRange parseGenomicRange(String genomicRange) {
        if (genomicRange == null) {
            return null;
        }

        if (genomicRange.isEmpty()) {
            return new GenomicRange("",-1, "", -1);
        }

        Boolean testChromosom = false;
        String[] startLoc;
        String[] stopLoc;
        if (genomicRange.contains("\t")) {
            String[] cols = genomicRange.split("\t", -1);
            startLoc = Arrays.copyOfRange(cols, 0, 2);
            stopLoc = Arrays.copyOfRange(cols, 2, 4);
        } else {
            testChromosom = true;
            String[] pos = genomicRange.split("[\\-]");
            startLoc = pos[0].contains(":") ? pos[0].split(":") : new String[]{pos[0], "-1"};
            stopLoc = pos.length > 1 ? pos[1].split(":") : new String[]{"", "-1"};

            if (stopLoc.length == 1) {
                // Have only one item in stopLoc, create correct length two stopLoc.
                stopLoc = stopLoc[0].startsWith("chr") ? new String[]{stopLoc[0], "-1"} : new String[]{"", stopLoc[0]};
            }
        }

        if (startLoc.length != 2 || stopLoc.length != 2) {
            throw new GorSystemException("Could not parse genomic range: " + genomicRange, null);
        }

        if (stopLoc[0].length() == 0 && !stopLoc[0].equals("-1")) {
            stopLoc[0] = startLoc[0];
        }

        // See if the chromosome is available
        String startChromosome = startLoc[0].trim();
        String stopChromosome = stopLoc[0].trim();

        // We only accept a start chromosome so lets test it for validity
        if (testChromosom && startChromosome.isEmpty() && stopChromosome.isEmpty()) {
            throw new GorDataException("Invalid chromosome defined: " + startLoc[0]);
        }

        int startLocation =  startLoc[1].length() > 0 ? parseLocationWithError(startLoc[1]) : -1;
        int stopLocation = stopLoc[1].length() > 0 ? parseLocationWithError(stopLoc[1]) : -1;

        return new GenomicRange(startChromosome, startLocation,
                                stopChromosome, stopLocation);
    }

    private static int parseLocationWithError(String location) {
        try {
            return Integer.parseInt(location.trim());
        } catch (NumberFormatException e) {
            throw new GorDataException("Invalid chromosome location at " + location, e);
        }
    }
}
