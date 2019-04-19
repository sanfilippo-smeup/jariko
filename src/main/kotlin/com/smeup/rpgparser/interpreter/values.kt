package com.smeup.rpgparser.interpreter

import java.math.BigDecimal
import java.util.*
import kotlin.streams.toList

abstract class Value {
    open fun asInt() : IntValue = throw UnsupportedOperationException("${this.javaClass.simpleName} cannot be seen as an Int")
    open fun asString() : StringValue = throw UnsupportedOperationException()
    open fun asBoolean() : BooleanValue = throw UnsupportedOperationException()
    abstract fun assignableTo(expectedType: Type): Boolean
}

data class StringValue(var value: String) : Value() {
    override fun assignableTo(expectedType: Type): Boolean {
        // TODO check size
        return expectedType is StringType
    }

    val valueWithoutPadding : String = value.removeNullChars()

    companion object {
        fun blank(length: Int) = StringValue("\u0000".repeat(length))
    }

    override fun equals(other: Any?): Boolean {
        return if (other is StringValue) {
            this.valueWithoutPadding == other.valueWithoutPadding
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return valueWithoutPadding.hashCode()
    }

    fun setSubstring(startOffset: Int, endOffset: Int, substringValue: StringValue) {
        require(startOffset >= 0)
        require(startOffset <= value.length)
        require(endOffset >= startOffset)
        require(endOffset <= value.length) { "Asked startOffset=$startOffset, endOffset=$endOffset on string of length ${value.length}" }
        value = value.substring(0, startOffset) + substringValue + value.substring(endOffset)
    }

    fun getSubstring(startOffset: Int, endOffset: Int) : StringValue {
        require(startOffset >= 0)
        require(startOffset <= value.length)
        require(endOffset >= startOffset)
        require(endOffset <= value.length) { "Asked startOffset=$startOffset, endOffset=$endOffset on string of length ${value.length}" }
        return StringValue(value.substring(startOffset, endOffset))
    }

    override fun toString(): String {
        return "StringValue($valueWithoutPadding)"
    }

    override fun asString() = this
}

fun String.removeNullChars() : String {
    val firstNullChar = this.chars().toList().indexOfFirst { it == 0 }
    return if (firstNullChar == -1) {
        this
    } else {
        this.substring(0, firstNullChar)
    }
}

data class IntValue(val value: Long) : Value() {
    override fun assignableTo(expectedType: Type): Boolean {
        // TODO check decimals
        return expectedType is NumberType
    }

    override fun asInt() = this
}
data class DecimalValue(val value: BigDecimal) : Value() {
    override fun assignableTo(expectedType: Type): Boolean {
        // TODO check decimals
        return expectedType is NumberType
    }

}
data class BooleanValue(val value: Boolean) : Value() {
    override fun assignableTo(expectedType: Type): Boolean {
        return expectedType is BooleanType
    }

    override fun asBoolean() = this
}
abstract class ArrayValue : Value() {
    abstract fun arrayLength() : Int
    abstract fun elementSize() : Int
    abstract fun setElement(index: Int, value: Value)
    abstract fun getElement(index: Int) : Value
    fun elements() : List<Value> {
        val elements = LinkedList<Value>()
        for (i in 0 until (arrayLength())) {
            elements.add(getElement(i))
        }
        return elements
    }

    override fun assignableTo(expectedType: Type): Boolean {
        // FIXME
        return true
    }
}
data class ConcreteArrayValue(val elements: MutableList<Value>, val elementType: Type) : ArrayValue() {
    override fun elementSize() = elementType.size.toInt()

    override fun arrayLength() = elements.size

    override fun setElement(index: Int, value: Value) {
        elements[index] = value
    }

    override fun getElement(index: Int) = elements[index]

}
object BlanksValue : Value() {
    override fun toString(): String {
        return "BlanksValue"
    }

    override fun assignableTo(expectedType: Type): Boolean {
        // FIXME
        return true
    }
}

class StructValue(val elements: MutableMap<FieldDefinition, Value>) : Value() {
    override fun assignableTo(expectedType: Type): Boolean {
        // FIXME
        return true
    }
}

class ProjectedArrayValue(val container: ArrayValue, val field: FieldDefinition) : ArrayValue() {
    override fun elementSize(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun arrayLength() = container.arrayLength()

    override fun setElement(index: Int, value: Value) {
        val containerElement =  container.getElement(index)
        if (containerElement is StringValue) {
            if (value is StringValue) {
                containerElement.setSubstring(field.startOffset, field.endOffset, value)
            } else {
                TODO()
            }
        } else {
            TODO()
        }
    }

    override fun getElement(index: Int) : Value {
        val containerElement = container.getElement(index)
        if (containerElement is StringValue) {
            return containerElement.getSubstring(field.startOffset, field.endOffset)
        } else {
            TODO()
        }
    }

}

fun createArrayValue(elementType: Type, n: Int, creator: (Int) -> Value) = ConcreteArrayValue(Array(n, creator).toMutableList(), elementType)

fun blankString(length: Int) = StringValue("\u0000".repeat(length))

fun Long.asValue() = IntValue(this)

fun String.asValue() = StringValue(this)