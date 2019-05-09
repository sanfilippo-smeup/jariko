package com.smeup.rpgparser.interpreter

import com.smeup.rpgparser.ast.*
import java.math.BigDecimal
import java.util.*
import kotlin.collections.HashMap

/**
 * This represent the interface to the external world.
 * Printing, accessing databases, all sort of interactions should go through this interface.
 */
interface SystemInterface {
    fun display(value: String)
    fun findProgram(name: String) : Program?
}

class SymbolTable {
    private val values = HashMap<AbstractDataDefinition, Value>()

    operator fun get(data: AbstractDataDefinition) : Value {
        if (data is FieldDefinition) {
            val containerValue = get(data.container)
            return if (data.container.isArray()) {
                ProjectedArrayValue(containerValue as ArrayValue, data)
            } else {
                (containerValue as StructValue).elements[data]!!
            }
        }
        return values[data] ?: throw IllegalArgumentException("Cannot find searchedValued for $data")
    }

    operator fun get(dataName: String) : Value {
        val data = values.keys.firstOrNull { it.name == dataName }
        if (data != null) {
            return values[data] ?: throw IllegalArgumentException("Cannot find searchedValued for $data")
        }
        for (e in values) {
            val field = (e.key as DataDefinition).fields.firstOrNull { it.name == dataName }
            if (field != null) {
                return ProjectedArrayValue(e.value as ArrayValue, field)
            }
        }
        throw IllegalArgumentException("Cannot find searchedValued for $dataName")
    }

    operator fun set(data: AbstractDataDefinition, value: Value) {
        values[data] = value
    }

}

abstract class LogEntry
data class SubroutineExecutionLogEntry(val subroutine: Subroutine) : LogEntry() {
    override fun toString(): String {
        return "executing ${subroutine.name}"
    }
}
data class ExpressionEvaluationLogEntry(val expression: Expression, val value: Value) : LogEntry() {
    override fun toString(): String {
        return "evaluating $expression as $value"
    }
}
data class AssignmentLogEntry(val data: AbstractDataDefinition, val value: Value) : LogEntry() {
    override fun toString(): String {
        return "assigning to $data value $value"
    }
}
data class AssignmentOfElementLogEntry(val array: Expression, val index: Int, val value: Value) : LogEntry() {
    override fun toString(): String {
        return "assigning to $array[$index] value $value"
    }
}

class LeaveException : Exception()
class IterException : Exception()

class Interpreter(val systemInterface: SystemInterface, val programName : String = "<UNNAMED>") {
    private val globalSymbolTable = SymbolTable()
    private val logs = LinkedList<LogEntry>()
    private val predefinedIndicators = HashMap<Int, Value>()
    var traceMode : Boolean = false
    var cycleLimit : Int? = null

    fun getLogs() = logs
    fun getExecutedSubroutines() = logs.asSequence().filterIsInstance(SubroutineExecutionLogEntry::class.java).map { it.subroutine }.toList()
    fun getExecutedSubroutineNames() = getExecutedSubroutines().map { it.name }
    fun getEvaluatedExpressions() = logs.filterIsInstance(ExpressionEvaluationLogEntry::class.java)
    fun getAssignments() = logs.filterIsInstance(AssignmentLogEntry::class.java)
    /**
     * Remove an expression if the last time the same expression was evaluated it had the same searchedValued
     */
    fun getEvaluatedExpressionsConcise() : List<ExpressionEvaluationLogEntry> {
        val base= logs.asSequence().filterIsInstance(ExpressionEvaluationLogEntry::class.java).toMutableList()
        var i = 0
        while (i < base.size) {
            val current = base[i]
            val found = base.subList(0, i).reversed().firstOrNull {
                it.expression == current.expression
            }?.value == current.value
            if (found) {
                base.removeAt(i)
            } else {
                i++
            }
        }
        return base
    }

    operator fun get(data: AbstractDataDefinition) = globalSymbolTable[data]
    operator fun get(dataName: String) = globalSymbolTable[dataName]
    operator fun set(data: AbstractDataDefinition, value: Value) {
        require(data.canBeAssigned(value)) {
            "$data cannot be assigned the value $value"}

        log(AssignmentLogEntry(data, value))
        globalSymbolTable[data] = coerce(value, data.type)
    }

    private fun log(logEntry: LogEntry) {
        if (traceMode) {
            println("[LOG] $logEntry")
        }
        logs.add(logEntry)
    }

    private fun initialize(compilationUnit: CompilationUnit, initialValues: Map<String, Value>, reinitialization : Boolean = true) {
        // Assigning initial values received from outside and consider INZ clauses
        if (reinitialization) {
            compilationUnit.dataDefinitions.forEach {
                set(it, coerce(when {
                    it.name in initialValues -> initialValues[it.name]!!
                    it.initializationValue != null -> interpret(it.initializationValue)
                    else -> blankValue(it)
                }, it.type))
            }
        } else {
            initialValues.forEach { iv ->
                val def = compilationUnit.allDataDefinitions.find { it.name == iv.key }!!
                set(def, coerce(iv.value, def.type))
            }
        }
    }

    fun simplyInitialize(compilationUnit: CompilationUnit, initialValues: Map<String, Value>) {
        initialize(compilationUnit, initialValues)
    }

    fun execute(compilationUnit: CompilationUnit, initialValues: Map<String, Value>, reinitialization : Boolean = true) {
        initialize(compilationUnit, initialValues, reinitialization)
        compilationUnit.main.stmts.forEach {
            execute(it)
        }
    }

    private fun execute(statements: List<Statement>) {
        statements.forEach { execute(it) }
    }

    private fun execute(statement: Statement) {
        try {
            when (statement) {
                is ExecuteSubroutine -> {
                    log(SubroutineExecutionLogEntry(statement.subroutine.referred!!))
                    execute(statement.subroutine.referred!!.stmts)
                }
                is EvalStmt -> assign(statement.target, statement.expression)
                is SelectStmt -> {
                    for (case in statement.cases) {
                        if (interpret(case.condition).asBoolean().value) {
                            execute(case.body)
                            return
                        }
                    }
                    if (statement.other != null) {
                        execute(statement.other!!.body)
                    }
                }
                is SetOnStmt -> null /* Nothing to do here */
                is PlistStmt -> null /* Nothing to do here */
                is ClearStmt -> {
                    return when (statement.value) {
                        is DataRefExpr -> {
                            assign(statement.value, BlanksRefExpr())
                            Unit
                        }
                        else -> throw UnsupportedOperationException("I do not know how to clear ${statement.value}")
                    }
                }
                is DisplayStmt -> {
                    val value = interpret(statement.value)
                    systemInterface.display(render(value))
                }
                is ForStmt -> {
                    eval(statement.init)
                    // TODO consider DOWNTO
                    while (isEqualOrSmaller(this[statement.iterDataDefinition()], eval(statement.endValue))) {
                        execute(statement.body)
                        increment(statement.iterDataDefinition())
                    }
                }
                is IfStmt -> {
                    val condition = eval(statement.condition).asBoolean().value
                    if (condition) {
                        execute(statement.body)
                    } else {
                        for (elseIfClause in statement.elseIfClauses) {
                            val c = eval(elseIfClause.condition).asBoolean().value
                            if (c) {
                                execute(elseIfClause.body)
                                return
                            }
                        }
                        if (statement.elseClause != null) {
                            execute(statement.elseClause.body)
                        }
                    }
                }
                is CallStmt -> {
                    val programToCall = eval(statement.expression).asString().value
                    val program = systemInterface.findProgram(programToCall) ?: throw RuntimeException("Program $programToCall cannot be found")
                    val params = statement.params.mapIndexed { index, it -> program.params()[index].name to get(it.param.name) }.toMap()
                    val paramValuesAtTheEnd = program.execute(systemInterface, params)
                    paramValuesAtTheEnd.forEachIndexed { index, value ->
                        assign(statement.params[index].param.referred!!, value)
                    }
                }
                is DoStmt -> {
                    if (statement.index == null) {
                        var myIterValue = eval(statement.startLimit).asInt()
                        try {
                            while ((cycleLimit == null || (cycleLimit as Int) >= myIterValue.value) &&
                                    isEqualOrSmaller(myIterValue, eval(statement.endLimit))) {
                                try {
                                    execute(statement.body)
                                } catch (e : IterException) {
                                    // nothing to do here
                                }
                                myIterValue = myIterValue.increment()
                            }
                        } catch (e: LeaveException) {
                            // nothing to do here
                        }
                    } else {
                        assign(statement.index, statement.startLimit)
                        try {
                            while ((cycleLimit == null || (cycleLimit as Int) >= eval(statement.index).asInt().value) &&
                                    isEqualOrSmaller(eval(statement.index), eval(statement.endLimit))) {
                                try {
                                    execute(statement.body)
                                } catch (e : IterException) {
                                    // nothing to do here
                                }
                                assign(statement.index, PlusExpr(statement.index, IntLiteral(1)))
                            }
                        } catch (e: LeaveException) {
                            // nothing to do here
                        }
                    }
                }
                is LeaveStmt -> throw LeaveException()
                is IterStmt -> throw IterException()
                else -> TODO(statement.toString())
            }
        } catch (e : InterruptForDebuggingPurposes) {
            throw e
        } catch (e : RuntimeException) {
            throw RuntimeException("Issue executing statement $statement", e)
        }
    }

    enum class Comparison {
        SMALLER,
        EQUAL,
        GREATER
    }


    private fun isEqualOrSmaller(value1: Value, value2: Value) : Boolean {
        val cmp = compare(value1, value2)
        return cmp == Comparison.SMALLER || cmp == Comparison.EQUAL
    }

    private fun isGreaterThan(value1: Value, value2: Value) : Boolean {
        val cmp = compare(value1, value2)
        return cmp == Comparison.GREATER
    }

    private fun compare(value1: Value, value2: Value) : Comparison {
        return when {
            value1 is IntValue && value2 is IntValue -> when {
                value1.value == value2.value -> Comparison.EQUAL
                value1.value < value2.value -> Comparison.SMALLER
                else -> Comparison.GREATER
            }
            value1 is IntValue && value2 is StringValue -> throw RuntimeException("Cannot compare int and string")
            value2 is HiValValue -> Comparison.SMALLER
            else -> TODO("Value 1 is $value1, Value 2 is $value2")
        }
    }

    private fun increment(dataDefinition: AbstractDataDefinition) {
        val value = this[dataDefinition]
        if (value is IntValue) {
            this[dataDefinition] = IntValue(value.value + 1)
        } else {
            throw UnsupportedOperationException()
        }
    }

    private fun areEquals(value1: Value, value2: Value) : Boolean {
        return when {
            value1 is BlanksValue && value2 is StringValue -> value2.isBlank()
            value2 is BlanksValue && value1 is StringValue -> value1.isBlank()
            else -> return value1 == value2
        }
    }

    private fun render(value: Value) : String {
        return when (value) {
            is StringValue -> value.valueWithoutPadding
            is BooleanValue -> value.value.toString()
            is IntValue -> value.value.toString()
            else -> TODO(value.javaClass.canonicalName)
        }
    }

    private fun eval(expression: Expression) : Value {
        return when (expression) {
            is AssignmentExpr -> {
                assign(expression.target, expression.value)
            }
            else -> interpret(expression)
        }
    }

    private fun assign(dataDefinition: AbstractDataDefinition, value: Value) : Value {
        val coercedValue = coerce(value, dataDefinition.type)
        set(dataDefinition, coercedValue)
        return coercedValue
    }

    private fun assign(target: AssignableExpression, value: Value) : Value {
        when (target) {
            is DataRefExpr -> {
                return assign(target.variable.referred!!, value)
            }
            is ArrayAccessExpr -> {
                val arrayValue = interpret(target.array) as ArrayValue
                require(arrayValue.assignableTo(target.array.type()))
                val indexValue = interpret(target.index)
                val elementType = (target.array.type() as ArrayType).element
                val evaluatedValue = coerce(value, elementType)
                val index = indexValue.asInt().value.toInt()
                log(AssignmentOfElementLogEntry(target.array, index, evaluatedValue))
                arrayValue.setElement(index, evaluatedValue)
                return evaluatedValue
            }
            else -> TODO(target.toString())
        }
    }

    private fun assign(target: AssignableExpression, value: Expression) : Value {
        return assign(target, eval(value))
    }

    // TODO put it outside Interpreter
    fun coerce(value: Value, type: Type) : Value {
        // TODO to be completed
        return when (value) {
            is BlanksValue -> {
                when (type) {
                    is StringType -> {
                        blankValue(type.length.toInt())
                    }
                    is ArrayType -> {
                        createArrayValue(type.element, type.nElements) {
                            blankValue(type.element)
                        }
                    }
                    is NumberType -> {
                        if (type.integer) {
                            IntValue.ZERO
                        } else {
                            DecimalValue.ZERO
                        }
                    }
                    else -> TODO(type.toString())
                }
            }
            is StringValue -> {
                when (type) {
                    is StringType -> {
                        var s = value.value.padEnd(type.length.toInt(), '\u0000')
                        if (value.value.length > type.length) {
                           s = s.substring(0, type.length.toInt())
                        }
                        return StringValue(s)
                    }
                    else -> TODO(type.toString())
                }
            }
            else -> value
        }
    }

    fun interpret(expression: Expression) : Value {
        val value = interpretConcrete(expression)
        log(ExpressionEvaluationLogEntry(expression, value))
        return value
    }

    private fun interpretConcrete(expression: Expression) : Value {
        return when (expression) {
            is StringLiteral -> StringValue(expression.value)
            is IntLiteral -> IntValue(expression.value)
            is NumberOfElementsExpr -> {
                val value = interpret(expression.value)
                when (value) {
                    is ArrayValue -> value.arrayLength().asValue()
                    else -> throw IllegalStateException("Cannot ask number of elements of $value")
                }
            }
            is DataRefExpr -> get(expression.variable.referred
                    ?: throw IllegalStateException("[$programName] Unsolved reference ${expression.variable.name} at ${expression.position}"))
            is EqualityExpr -> {
                val left = interpret(expression.left)
                val right = interpret(expression.right)
                return areEquals(left, right).asValue()
            }
            is DifferentThanExpr -> {
                val left = interpret(expression.left)
                val right = interpret(expression.right)
                return (!areEquals(left, right)).asValue()
            }
            is GreaterThanExpr -> {
                val left = interpret(expression.left)
                val right = interpret(expression.right)
                return isGreaterThan(left, right).asValue()
            }
            is BlanksRefExpr -> {
                return BlanksValue
            }
            is DecExpr -> {
                val decDigits = interpret(expression.decDigits).asInt().value
                val valueAsString = interpret(expression.value).asString().value
                return if (decDigits == 0L) {
                    IntValue(valueAsString.removeNullChars().toLong())
                } else {
                    DecimalValue(BigDecimal(valueAsString))
                }
            }
            is PlusExpr -> {
                val left = interpret(expression.left)
                val right = interpret(expression.right)
                when {
                    left is StringValue && right is StringValue -> {
                        val s = left.valueWithoutPadding + right.valueWithoutPadding
                        StringValue(s)
                    }
                    left is IntValue && right is IntValue -> IntValue(left.value + right.value)
                    else -> throw UnsupportedOperationException("I do not know how to sum $left and $right at ${expression.position}")
                }
            }
            is MinusExpr -> {
                val left = interpret(expression.left)
                val right = interpret(expression.right)
                when {
                    left is IntValue && right is IntValue -> IntValue(left.value - right.value)
                    else -> throw UnsupportedOperationException("I do not know how to sum $left and $right at ${expression.position}")
                }
            }
            is CharExpr -> {
                val value = interpret(expression.value)
                return StringValue(render(value))
            }
            is LookupExpr -> {
                val searchValued = interpret(expression.searchedValued)
                val array = interpret(expression.array) as ArrayValue
                val index = array.elements().indexOfFirst { it == searchValued }
                return if (index == -1) 0.asValue() else (index + 1).asValue()
            }
            is ArrayAccessExpr -> {
                val arrayValue = interpret(expression.array) as ArrayValue
                val indexValue = interpret(expression.index)
                return arrayValue.getElement(indexValue.asInt().value.toInt())

            }
            is HiValExpr -> return HiValValue
            is TranslateExpr -> {
                val originalChars = eval(expression.from).asString().valueWithoutPadding
                val newChars = eval(expression.to).asString().valueWithoutPadding
                var s = eval(expression.string).asString().valueWithoutPadding
                originalChars.forEachIndexed { i, c ->
                    s = s.replace(c, newChars[i])
                }
                return StringValue(s)
            }
            is LogicalAndExpr -> {
                val left = eval(expression.left).asBoolean().value
                return if (left) {
                    eval(expression.right)
                } else {
                    BooleanValue(false)
                }
            }
            is LogicalOrExpr -> {
                val left = eval(expression.left).asBoolean().value
                return if (left) {
                    BooleanValue(true)
                } else {
                    eval(expression.right)
                }
            }
            is OnRefExpr -> {
                return BooleanValue(true)
            }
            is NotExpr -> {
                return BooleanValue(!eval(expression.base).asBoolean().value)
            }
            is TrimExpr -> {
                return StringValue(eval(expression.value).asString().value.trim())
            }
            is ScanExpr -> {
                var startIndex = 0
                if (expression.start != null) {
                    startIndex = eval(expression.start).asInt().value.toInt()
                }
                val value = eval(expression.value).asString().valueWithoutPadding
                val source = eval(expression.source).asString().valueWithoutPadding
                val result = source.indexOf(value, startIndex)
                return IntValue(if (result == -1) 0 else result.toLong() + 1)
            }
            is SubstExpr -> {
                val length = if (expression.length != null) eval(expression.length).asInt().value.toInt() else null
                val start = eval(expression.start).asInt().value.toInt() - 1
                val originalString = eval(expression.string).asString().value
                return if (length == null) {
                    StringValue(originalString.substring(start))
                } else {
                    StringValue(originalString.substring(start, start + length))
                }
            }
            is LenExpr -> {
                val value = eval(expression.value)
                return when (value) {
                    is StringValue -> value.valueWithoutPadding.length.asValue()
                    else -> TODO(value.toString())
                }
            }
            is OffRefExpr -> {
                return BooleanValue(false)
            }
            is PredefinedIndicatorExpr -> {
                return predefinedIndicators[expression.index] ?: BooleanValue.FALSE
            }
            else -> TODO(expression.toString())
        }
    }

    fun blankValue(size: Int) = StringValue(" ".repeat(size))

    fun blankValue(type: Type): Value {
        return when (type){
            is ArrayType -> createArrayValue(type.element, type.nElements) {
                blankValue(type.element)
            }
            is DataStructureType -> StringValue.blank(type.size.toInt())
            is StringType ->  StringValue.blank(type.size.toInt())
            is NumberType -> IntValue(0)
            is BooleanType -> BooleanValue(false)
        }
    }

    fun blankValue(dataDefinition: DataDefinition, forceElement: Boolean = false): Value {
        if (forceElement) TODO()
        return blankValue(dataDefinition.type)
    }
}

private fun AbstractDataDefinition.canBeAssigned(value: Value): Boolean {
    return type.canBeAssigned(value)
}

private fun Int.asValue() = IntValue(this.toLong())
private fun Boolean.asValue() = BooleanValue(this)

object DummySystemInterface : SystemInterface {
    override fun findProgram(name: String): Program? {
        return null
    }

    override fun display(value: String) {
        // doing nothing
    }

}

// Useful to interrupt infinite cycles in tests
class InterruptForDebuggingPurposes : RuntimeException()
