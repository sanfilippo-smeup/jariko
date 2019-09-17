package com.smeup.rpgparser.parsing

import com.smeup.rpgparser.assertASTCanBeProduced
import com.smeup.rpgparser.assertCanBeParsed
import com.smeup.rpgparser.assertDataDefinitionIsPresent
import com.smeup.rpgparser.execute
import com.smeup.rpgparser.interpreter.DummySystemInterface
import com.smeup.rpgparser.interpreter.InternalInterpreter
import com.smeup.rpgparser.parsing.parsetreetoast.resolve
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class RpgParserDataStruct {

    @Test
    fun parseSTRUCT_01_MYDS_isRecognizedCorrectly() {
        val cu = assertASTCanBeProduced("struct/STRUCT_01", true)
        cu.resolve()

        val dataDefinition = cu.getDataDefinition("MYDS")
        assertEquals(0, dataDefinition.fields[0].startOffset)
        assertEquals(5, dataDefinition.fields[0].endOffset)
        assertEquals(5, dataDefinition.fields[1].startOffset)
        assertEquals(15, dataDefinition.fields[1].endOffset)
        assertEquals(15, dataDefinition.elementSize())
    }

    @Test
    fun parseSTRUCT_01() {
        val result = assertCanBeParsed("struct/STRUCT_01", withMuteSupport = true)

        val cu = assertASTCanBeProduced("struct/STRUCT_01", true)

        cu.resolve()
        execute(cu, mapOf())
    }

    /**
     * Test for QUALIFIED support
     */
    @Test
    @Ignore // The parser does not handle the dot notation for accessing fields
    fun parseSTRUCT_02() {
        val result = assertCanBeParsed("struct/STRUCT_02", withMuteSupport = true)

        val cu = assertASTCanBeProduced("struct/STRUCT_02", true)
        cu.resolve()
        execute(cu, mapOf())
    }

    @Test
    @Ignore // this is probably failing because of TIMESTAMP()
    fun parseSTRUCT_03() {
        val result = assertCanBeParsed("struct/STRUCT_03", withMuteSupport = true)

        val cu = assertASTCanBeProduced("struct/STRUCT_03", true)
        cu.resolve()
        execute(cu, mapOf())
    }

    @Test
    @Ignore // I am not sure we should handle the definition of two consecutive DS
    fun parseSTRUCT_04() {
        val result = assertCanBeParsed("struct/STRUCT_04", withMuteSupport = true)

        val cu = assertASTCanBeProduced("struct/STRUCT_04", true)
        cu.resolve()
        execute(cu, mapOf())
    }

    /**
     * Test for TEMPLATE and LIKEDS support
     */
    @Test
    @Ignore // the parser does not handle this
    fun parseSTRUCT_05() {
        val result = assertCanBeParsed("struct/STRUCT_05", withMuteSupport = true)

        val cu = assertASTCanBeProduced("struct/STRUCT_05", true)
        cu.resolve()
        execute(cu, mapOf())
    }

}