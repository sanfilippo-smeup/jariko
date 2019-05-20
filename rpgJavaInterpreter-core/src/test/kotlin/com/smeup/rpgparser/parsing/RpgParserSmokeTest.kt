package com.smeup.rpgparser.parsing

import com.smeup.rpgparser.assertASTCanBeProduced
import com.smeup.rpgparser.assertCanBeParsed
import org.junit.Test
import kotlin.test.assertEquals

class RpgParserSmokeTest {

    @Test
    fun parseJD_001() {
        assertCanBeParsed("JD_001")
    }

    @Test
    fun parseJD_001_alt() {
        assertCanBeParsed("JD_001_alt")
    }

    @Test
    fun parseJD_001_justdirectives() {
        assertCanBeParsed("JD_001_justdirectives")
    }

    @Test
    fun parseJD_001_onedatadecl() {
        assertCanBeParsed("JD_001_onedatadecl")
    }

    @Test
    fun parseJD_001_onedatadecl_simple() {
        assertCanBeParsed("JD_001_onedatadecl_simple")
    }

    @Test
    fun parseJD_002() {
        assertCanBeParsed("JD_002")
    }

    @Test
    fun parseJD_004() {
        assertCanBeParsed("JD_004")
    }

    @Test
    fun parseJD_005() {
        assertCanBeParsed("JD_005")
    }

    @Test
    fun parseJD_006() {
        assertCanBeParsed("JD_006")
    }

    @Test
    fun parseJD_003() {
        assertCanBeParsed("JD_003")
    }

    @Test
    fun parsePROOF() {
        assertCanBeParsed("PROOF")
    }

    @Test
    fun parseJCODFISD() {
        assertCanBeParsed("JCODFISD")
    }

    @Test
    fun parseJCODFISS() {
        assertCanBeParsed("JCODFISS")
    }

    @Test
    fun parseJFTCPR() {
        assertCanBeParsed("JFTCPR")
    }

    @Test
    fun parseJDATWD() {
        assertCanBeParsed("JDATWD")
    }

    @Test
    fun parseJDATWDK() {
        assertCanBeParsed("JDATWDK")
    }

    @Test
    fun parseCALCFIB() {
        assertCanBeParsed("CALCFIB")
    }

    @Test
    fun parseHELLO() {
        assertCanBeParsed("HELLO")
    }

    @Test
    fun parseHELLO1() {
        assertCanBeParsed("HELLO1")
    }

    @Test
    fun parseJRANDOMCAL() {
        assertCanBeParsed("JRANDOMCAL")
    }

    @Test
    fun parseJRANDOMA() {
        assertCanBeParsed("JRANDOMA")
    }

    @Test
    fun parseCHAINHOSTS() {
        assertCanBeParsed("CHAINHOSTS")
    }

    @Test
    fun parseTIMESTDIFF() {
        assertCanBeParsed("TIMESTDIFF")
    }

    @Test
    fun parseDSEX01() {
        assertCanBeParsed("DSEX01")
    }

    @Test
    fun parseDSEX02() {
        assertCanBeParsed("DSEX02")
    }

    @Test
    fun parseMUTE05_02() {
        assertCanBeParsed("MUTE05_02")
    }

    @Test
    fun parseERRORVARST() {
        assertCanBeParsed("ERRORVARST")
    }

    @Test
    fun parseHELLOVARST() {
        assertCanBeParsed("HELLOVARST")
    }


}