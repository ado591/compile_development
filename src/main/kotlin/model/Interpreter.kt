package model


sealed class RuntimeValue {
    data class Number(val value: Double) : RuntimeValue() {
        override fun toString(): String =
            if (value == kotlin.math.floor(value) && !value.isInfinite())
                value.toLong().toString() else value.toString()
    }

    data class StringVal(val value: String) : RuntimeValue() {
        override fun toString(): String = value
    }

    data class Boolean(val value: kotlin.Boolean) : RuntimeValue() {
        override fun toString(): String = value.toString()
    }

    data class FunctionVal(
        val declaration: Statement.FunctionStatement,
        val closure: Environment
    ) : RuntimeValue() {
        override fun toString(): String = "<функция ${declaration.name}>"
    }

    data class ArrayVal(val elements: MutableList<RuntimeValue>) : RuntimeValue() {
        override fun toString(): String = elements.joinToString(", ", "[", "]")
    }

    object Null : RuntimeValue() {
        override fun toString(): String = "null"
    }
}

class ReturnException(val value: RuntimeValue) : RuntimeException()

class Environment(private val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, RuntimeValue>()

    fun define(name: String, value: RuntimeValue) {
        values[name] = value
    }

    fun get(name: String): RuntimeValue =
        values[name] ?: enclosing?.get(name)
        ?: throw RuntimeException("Ошибка выполнения: переменная '$name' не объявлена.")

    fun assign(name: String, value: RuntimeValue) {
        if (values.containsKey(name)) { values[name] = value; return }
        if (enclosing != null) { enclosing.assign(name, value); return }
        throw RuntimeException("Ошибка выполнения: переменная '$name' не объявлена.")
    }
}

class Interpreter {
    private var env = Environment()

    fun interpret(statements: List<Statement>) {
        for (stmt in statements) {
            if (stmt is Statement.FunctionStatement) {
                env.define(stmt.name, RuntimeValue.FunctionVal(stmt, env))
            }
        }
        for (stmt in statements) execute(stmt)
    }

    private fun execute(stmt: Statement) {
        when (stmt) {
            is Statement.ExpressionStatement -> evaluate(stmt.expression)
            is Statement.PrintStatement -> println(evaluate(stmt.expression))
            is Statement.VarStatement -> {
                val value = if (stmt.initializer != null) evaluate(stmt.initializer) else RuntimeValue.Null
                env.define(stmt.name, value)
            }
            is Statement.BlockStatement -> {
                val outer = env
                env = Environment(outer)
                try {
                    for (s in stmt.statements) execute(s)
                } finally {
                    env = outer
                }
            }
            is Statement.IfStatement -> {
                if (isTruthy(evaluate(stmt.condition))) execute(stmt.thenBranch)
                else stmt.elseBranch?.let { execute(it) }
            }
            is Statement.WhileStatement -> {
                while (isTruthy(evaluate(stmt.condition))) execute(stmt.body)
            }
            is Statement.FunctionStatement -> {
                env.define(stmt.name, RuntimeValue.FunctionVal(stmt, env))
            }
            is Statement.ReturnStatement -> {
                val value = if (stmt.value != null) evaluate(stmt.value) else RuntimeValue.Null
                throw ReturnException(value)
            }
        }
    }

    private fun evaluate(expr: Expression): RuntimeValue = when (expr) {
        is Expression.NumberExpression -> RuntimeValue.Number(expr.value)
        is Expression.StringExpression -> RuntimeValue.StringVal(expr.value)
        is Expression.BooleanExpression -> RuntimeValue.Boolean(expr.value)
        is Expression.VariableExpression -> env.get(expr.name)
        is Expression.AssignExpression -> {
            val value = evaluate(expr.value)
            env.assign(expr.name, value)
            value
        }
        is Expression.UnaryExpression -> when (expr.operator) {
            TokenType.MINUS -> {
                val v = evaluate(expr.right) as RuntimeValue.Number
                RuntimeValue.Number(-v.value)
            }
            TokenType.EXCL -> {
                val v = evaluate(expr.right) as RuntimeValue.Boolean
                RuntimeValue.Boolean(!v.value)
            }
            else -> throw RuntimeException("Неизвестный унарный оператор: ${expr.operator}")
        }
        is Expression.BinaryExpression -> evaluateBinary(expr)
        is Expression.CallExpression -> evaluateCall(expr)
        is Expression.ArrayExpression ->
            RuntimeValue.ArrayVal(expr.elements.map { evaluate(it) }.toMutableList())
        is Expression.IndexExpression -> {
            val arr = asArray(evaluate(expr.array))
            arr.elements[checkedIndex(arr, expr.index)]
        }
        is Expression.IndexAssignExpression -> {
            val arr = asArray(evaluate(expr.array))
            val i = checkedIndex(arr, expr.index)
            val value = evaluate(expr.value)
            arr.elements[i] = value
            value
        }
    }

    private fun asArray(v: RuntimeValue): RuntimeValue.ArrayVal =
        v as? RuntimeValue.ArrayVal
            ?: throw RuntimeException("Ошибка выполнения: значение не является массивом.")

    private fun checkedIndex(arr: RuntimeValue.ArrayVal, indexExpr: Expression): Int {
        val d = (evaluate(indexExpr) as? RuntimeValue.Number)?.value
            ?: throw RuntimeException("Ошибка выполнения: индекс массива должен быть числом.")
        val i = d.toInt()
        if (d != i.toDouble())
            throw RuntimeException("Ошибка выполнения: индекс должен быть целым, получено $d.")
        if (i < 0 || i >= arr.elements.size)
            throw RuntimeException(
                "Ошибка выполнения: индекс $i вне границ массива [0, ${arr.elements.size})."
            )
        return i
    }

    private fun evaluateCall(expr: Expression.CallExpression): RuntimeValue {
        val callee = evaluate(expr.callee)
        if (callee !is RuntimeValue.FunctionVal)
            throw RuntimeException("Ошибка выполнения: вызываемое значение не является функцией.")

        val fn = callee.declaration
        val args = expr.arguments.map { evaluate(it) }
        if (args.size != fn.params.size)
            throw RuntimeException(
                "Ошибка выполнения: функция '${fn.name}' ожидает ${fn.params.size} аргументов, " +
                    "получено ${args.size}."
            )

        val funcEnv = Environment(callee.closure)
        for (i in fn.params.indices) funcEnv.define(fn.params[i].name, args[i])

        val outer = env
        env = funcEnv
        return try {
            for (s in fn.body) execute(s)
            RuntimeValue.Null
        } catch (r: ReturnException) {
            r.value
        } finally {
            env = outer
        }
    }

    private fun evaluateBinary(expr: Expression.BinaryExpression): RuntimeValue {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)
        return when (expr.operator) {
            TokenType.PLUS  -> when {
                left is RuntimeValue.Number && right is RuntimeValue.Number ->
                    RuntimeValue.Number(left.value + right.value)
                left is RuntimeValue.StringVal && right is RuntimeValue.StringVal ->
                    RuntimeValue.StringVal(left.value + right.value)
                else -> throw RuntimeException(
                    "Ошибка выполнения: нельзя применить '+' к ${left::class.simpleName} и ${right::class.simpleName}."
                )
            }
            TokenType.MINUS -> RuntimeValue.Number(num(left) - num(right))
            TokenType.STAR  -> RuntimeValue.Number(num(left) * num(right))
            TokenType.SLASH -> {
                val r = num(right)
                if (r == 0.0) throw RuntimeException("Ошибка выполнения: деление на ноль.")
                RuntimeValue.Number(num(left) / r)
            }
            TokenType.LT   -> RuntimeValue.Boolean(num(left) < num(right))
            TokenType.LTEQ -> RuntimeValue.Boolean(num(left) <= num(right))
            TokenType.GT   -> RuntimeValue.Boolean(num(left) > num(right))
            TokenType.GTEQ -> RuntimeValue.Boolean(num(left) >= num(right))
            TokenType.EQEQ -> RuntimeValue.Boolean(left == right)
            TokenType.NEQ  -> RuntimeValue.Boolean(left != right)
            TokenType.AND  -> RuntimeValue.Boolean(bool(left) && bool(right))
            TokenType.OR   -> RuntimeValue.Boolean(bool(left) || bool(right))
            else -> throw RuntimeException("Неизвестный оператор: ${expr.operator}")
        }
    }

    private fun num(v: RuntimeValue) = (v as RuntimeValue.Number).value
    private fun bool(v: RuntimeValue) = (v as RuntimeValue.Boolean).value

    private fun isTruthy(v: RuntimeValue) = when (v) {
        is RuntimeValue.Boolean -> v.value
        is RuntimeValue.Number  -> v.value != 0.0
        RuntimeValue.Null       -> false
        else -> false
    }
}
