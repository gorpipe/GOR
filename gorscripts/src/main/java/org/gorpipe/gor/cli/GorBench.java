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

import org.gorpipe.gor.driver.GorDriver;
import org.gorpipe.gor.driver.GorDriverFactory;
import org.gorpipe.gor.driver.meta.SourceReferenceBuilder;
import org.gorpipe.gor.model.GenomicIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GorBench {
    String[] chrNames = {"chr1", "chr2", "chr3", "chr4", "chr5", "chr6", "chr7", "chr8", "chr9", "chr10", "chr11", "chr12", "chr13", "chr14", "chr15", "chr16", "chr17", "chr18", "chr19", "chr20", "chr21", "chr22", "chrX", "chrY"};
    int[] chrBases = {249250621, 243199373, 198022430, 191154276, 180915260, 171115067, 159138663, 146364022, 141213431, 135534747, 135006516, 133851895, 115169878, 107349540, 102531392, 90354753, 81195210, 78077248, 59128983, 63025520, 48129895, 51304566, 155270560, 59373566};
    String[] files;
    int seeks;
    int readBases;
    List<BenchmarkThread> threads = new ArrayList<>();
    GorDriver gorDriver;
    long totalTimeMs = 0;
    long totalSeekNanos = 0;
    long elapsedMs = 0;
    String label;
    private boolean newSource;
    private String subset;
    Integer fixedIndex;
    long actualLines;

    public GorBench(String label, String[] files, int seeks, int readBases) {
        this.label = label;
        this.files = files;
        this.seeks = seeks;
        this.readBases = readBases;

        this.gorDriver = GorDriverFactory.fromConfig();
    }

    public void run() throws InterruptedException {

        if (subset != null) {
            for (int i = 0; i < chrNames.length; i++) {
                if (chrNames[i].equals(subset)) {
                    fixedIndex = i;
                    break;
                }
            }
            if (fixedIndex == null) {
                throw new IllegalArgumentException("Illegal subset: " + subset + " expected one of chr1-chr22,chrX or chrY");
            }
        }
        long startTime = System.currentTimeMillis();
        for (String file : files) {
            BenchmarkThread thread = new BenchmarkThread(file);
            thread.start();
            threads.add(thread);
        }
        System.err.println("All threads started");
        for (BenchmarkThread thread : threads) {
            thread.join();
            totalTimeMs += thread.elapsedMs;
            totalSeekNanos += thread.seekNanos;
            actualLines += thread.actualLines;
        }
        elapsedMs = System.currentTimeMillis() - startTime;
        String format = "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\n";
        System.out.println("#Label\tFileCount\tSeekCount\tThreads\tLinesPerSeek\tActualLines\tElapsedMs\tAccumMs\tAccumSeekMs");
        System.out.format(format, label, files.length, seeks, threads.size(), readBases, actualLines, elapsedMs, totalTimeMs, totalSeekNanos / 1000000);
    }

    class BenchmarkThread extends Thread {
        String file;
        long elapsedMs;
        Random random;
        long seekNanos = 0;
        long actualLines;

        public BenchmarkThread(String file) {
            this.file = file;
            random = new Random();
        }

        @Override
        public void run() {
            try {
                long startTime = System.currentTimeMillis();
                GenomicIterator iter = null;
                try {
                    iter = gorDriver.createIterator(new SourceReferenceBuilder(file).build());
                    if (seeks == 0) {
                        for (int j = 0; j < readBases - 1; j++) {
                            if (!iter.hasNext()) {
                                break;
                            }
                            iter.next();
                            actualLines++;
                        }

                    } else {
                        for (int i = 0; i < seeks; i++) {
                            int chromIndex;
                            if (fixedIndex != null) {
                                chromIndex = fixedIndex;
                            } else {
                                chromIndex = random.nextInt(chrNames.length);
                            }
                            String chrom = chrNames[chromIndex];

                            int pos = random.nextInt(chrBases[chromIndex] - 10 * readBases);
                            long seekStart = System.nanoTime();
                            iter.seek(chrom, pos);
                            iter.next();
                            seekNanos += (System.nanoTime() - seekStart);
                            for (int j = 0; j < readBases - 1; j++) {
                                if (!iter.hasNext()) {
                                    break;
                                }
                                iter.next();
                                actualLines++;
                            }
                            if (newSource) {
                                iter.close();
                                iter = gorDriver.createIterator(new SourceReferenceBuilder(file).build());
                            }
                        }
                    }
                } finally {
                    if (iter != null) {
                        iter.close();
                    }
                }
                elapsedMs = System.currentTimeMillis() - startTime;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }

    private static void help(String extraMessage) {
        if (extraMessage != null) {
            System.err.println(extraMessage);
        }
        System.err.println("Usage: gorbench [-n] [-s subset] <label> <seeks> <lines> <files ...>\n\n" +
                "Starts a thead per file and repeatedly seeks and reads\n" +
                "-n: If set, will request a new source for each seek\n" +
                "-s: Can be used to specify subset (chromosome) - will request subset and only seek within that\n" +
                "label: Label this run (will appear in output)\n" +
                "seeks: How many seeks to perform per file (use 0 to open file once at beginning)\n" +
                "lines: How many lines to read after each seek\n\n" +
                "files: Any number of file paths/urls supporte by the gor driver\n");
        System.exit(-1);
    }

    private void setNewSourceOnSeek(boolean newSource) {
        this.newSource = newSource;
    }

    private void setSubset(String subset) {
        this.subset = subset;
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            boolean newSource = false;
            String subset = null;
            while (args[0].startsWith("-")) {
                if (args[0].equals("-n")) {
                    newSource = true;
                    args = Arrays.copyOfRange(args, 1, args.length);
                } else if (args[0].equals("-s")) {
                    subset = args[1];
                    args = Arrays.copyOfRange(args, 2, args.length);
                } else {
                    help("Illegal option " + args[0]);
                }
            }
            String[] files = Arrays.copyOfRange(args, 3, args.length);
            GorBench bench = new GorBench(args[0], files, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            bench.setNewSourceOnSeek(newSource);
            bench.setSubset(subset);
            bench.run();
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            help(e.getMessage());
        }
    }

}
