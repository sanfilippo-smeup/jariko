      *---------------------------------------------------------------
      * This test defines a data structure with two fields
      * It should possible to reference the fields using the unqualified
      * name.
      *---------------------------------------------------------------
     DMYDS             DS
     DFLD1                            5 0
     DFLD2                           10
      *---------------------------------------------------------------
      * M A I N
      *---------------------------------------------------------------
      *MU* VAL1(FLD1) VAL2(21) COMP(EQ)
     C                   EVAL FLD1 = 21
     C                   EVAL FLD2 = 'ABCD'
     C                   DSPLY                   FLD1
     C                   DSPLY                   FLD2
      *
     C                   SETON                                        RT
