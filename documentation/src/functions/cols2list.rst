.. _cols2list:

=========
COLS2LIST
=========

The **COLS2LIST** function collapses columns into a single column, with a separator between the values.
The separator defaults to ",", but can be set to anything.

Usage
=====

``COLS2LIST(string) : string``

``COLS2LIST(string, string) : string``

Example
=======
.. code-block:: gor

   gor ... | CALC test COLS2LIST('cols*')

``test`` now contains the values from all columns starting with 'cols', separated by commas.

.. code-block:: gor

   gor ... | CALC test COLS2LIST('cols*', ':')

``test`` now contains the values from all columns starting with 'cols', separated by colons.

.. code-block:: gor

   gor ... | CALC test IF(CONTAINS(COLS2LIST('cols*'), 'foo'), 1, 0)

``test`` now contains 1 if any of the values from all columns starting with 'cols' contain the string 'foo'.
