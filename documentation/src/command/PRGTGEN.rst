.. raw:: html

   <span style="float:right; padding-top:10px; color:#BABABA;">Used in: gor only</span>

.. _PRGTGEN:

=======
PRGTGEN
=======
The **PRGTGEN** command is a probabilistic version of **GTGEN**, i.e. it does the same but also does a simple joint
variant calling and returns the genotypes in probabilistic form.

The incoming stream is a gor stream containing genotypes, one pn per line. The genotypes can come in the following:
i) Probabilistic: A triplet containing the probabilities of Ref-Ref, Ref-Alt, Alt-Alt. Should be stored in a column
named GP. Can also be in a column called GL if the probabilities are in a log10 scale.
ii) Call and genotype likelihood. The call should be stored as a single character and the genotype likelihood should be
the difference between the probability of the called genotype and the second most likely one, in log10 scale, rounded
to nearest integer. This number should be stored in a column named GL and the called genotype in a column named
'CallCopies'.
iii) Raw: Then there should be one column called 'depth' containing the reading depth and another named 'callratio'
containing the call ratio.

As input, there should be one tag-bucket source, a coverage source and optionally a gor source containing prior allele
frequencies estimates and the number of samples behind that estimate.

The coverage file should contain segments, a depth column and a pn column.

The gor source containing the allele frequencies and the number of samples behind the estimates, should contain an AF
column with the allele frequencies and an AN column with the number of samples. It should be interpreted as follows:
The tag list from the tag-bucket file is thought of as coming from a larger collection of pns and the allele frequency
given in the source should be thought of as having been estimated for some number of samples from the cohort, excluding
the ones we are calling now.

Usage
=====

.. code-block:: gor

    gor ... | PRGTGEN tagbucketrelation coveragefile priorfile [ attributes ]

Options
=======
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-gc cols``        | Grouping columns other than (chrom,pos)                                                            |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-tag tagcolname`` | Override the default PN colunm name used to represent the tag value.                               |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-maxseg bpsize``  | The maximum span size of the coverage segments.  Defaults to 10000 (10kb).                         |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-e error rate``   | The error rate of the reads.                                                                       |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-af allele freq`` | A 'global allele frequency' assumed to have been used for computing the genotypes from the input.  |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-maxit mi``       | The maximum number of iterations. Default value is 20.                                             |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-tol tolerance``  | The tolerance. Default value is 1e-5.                                                              |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-gl col``         | The GL col (either from i) or ii)). Is looked up by name if not given.                             |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-gp col``         | The GP col (from i)). Is looked up by name if not given.                                           |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-afc col``        | The allele frequency column in the prior source. Is looked up by name if not given.                |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-anc col``        | The allele number column in the prior source. Is looked up by name if not given.                   |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-cc col``         | The call copies column from ii) Looked up by name if not given.                                    |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-crc col``        | The call ratio column from iii) Looked up by name if not given.                                    |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-ld col``         | The depth column from the incoming source in ii). Looked up by name if not given.                  |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-rd col``         | The depth column from the prior source. Lookup up by name if not given.                            |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-th threshold``   | The threshold to use, if the genotypes should be written out as discrete genotypes. Giving the     |
|                     | threshold indicates that the hard calls should be written out.                                     |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-psep separator`` | The separator in the probability triplet. Default value is ';'. Must be a single character.        |
+---------------------+----------------------------------------------------------------------------------------------------+
| ``-osep separator`` | The separator to separate the genotypes, in case the triplets should we written out.               |
+---------------------+----------------------------------------------------------------------------------------------------+
Examples
========

.. code-block:: gor

    /* An example of creating horizontal variations stored in buckets */



Related commands
----------------

:ref:`CSVCC` :ref:`CSVSEL` :ref:`GTLD` :ref:`GTGEN`