package com.smeup.rpgparser.db.sql

import com.smeup.rpgparser.interpreter.*

fun FileMetadata.toSQL(): List<String> =
    listOf(
        "CREATE TABLE ${this.tableName} (${this.fields.toSQL()})",
        "COMMENT ON TABLE ${this.tableName} IS '${this.formatName}'")

private fun Collection<DBField>.toSQL(): String =
    joinToString {
        "${it.name} ${it.sqlType()}"
    }

private fun DBField.sqlType(): String =
    when (this.type) {
        is StringType -> "CHAR (${this.type.size}) NOT NULL"
        is NumberType -> "DECIMAL (${this.type.size}, ${this.type.decimalDigits}) NOT NULL"
        else -> TODO("Conversion to SQL Type not yet implemented: ${this.type}")
    }

infix fun String.withType(type: Type): DBField = DBField(this, type)

fun String.insertSQL(values: List<Pair<String, Value>>): String {
    val names = values.joinToString { it.first }
    val questionMarks = values.joinToString { "?" }
    return "INSERT INTO $this ($names) VALUES($questionMarks)"
}

fun List<Pair<String, Value>>.whereSQL(): String =
    " WHERE " + this.joinToString(" AND ") { "${it.first} = ?" }