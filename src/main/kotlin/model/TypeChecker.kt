package model


class TypeEnvironment(private val enclosing: TypeEnvironment? = null) {
    private val types = mutableMapOf<String, Type>()

    fun define(name: String, type: Type) {
        types[name] = type
    }

    fun get(name: String): Type =
        types[name] ?: enclosing?.get(name)
        ?: throw RuntimeException("Ошибка типов: переменная '$name' не объявлена.")

    fun assign(name: String, type: Type) {
        if (types.containsKey(name)) { types[name] = type; return }
        if (enclosing != null) { enclosing.assign(name, type); return }
        throw RuntimeException("Ошибка типов: переменная '$name' не объявлена.")
    }
}

class TypeChecker {
    private var env = TypeEnvironment()
    private var currentReturnType: Type? = null

    fun check(statements: List<Statement>) {
        for (stmt in statements) {
            if (stmt is Statement.FunctionStatement) {
                env.define(stmt.name, functionTypeOf(stmt))
            }
        }
        for (stmt in statements) checkStatement(stmt)
    }

    private fun functionTypeOf(stmt: Statement.FunctionStatement): Type.Function =
        Type.Function(stmt.params.map { it.type }, stmt.returnType)

    private fun checkStatement(stmt: Statement) {
        when (stmt) {
            is Statement.ExpressionStatement -> inferType(stmt.expression)
            is Statement.PrintStatement -> inferType(stmt.expression)
            is Statement.VarStatement -> {
                val type = if (stmt.initializer != null) inferType(stmt.initializer) else Type.Null
                env.define(stmt.name, type)
            }
            is Statement.BlockStatement -> {
                val outer = env
                env = TypeEnvironment(outer)
                for (s in stmt.statements) checkStatement(s)
                env = outer
            }
            is Statement.IfStatement -> {
                val condType = inferType(stmt.condition)
                if (condType != Type.Boolean)
                    throw RuntimeException("Ошибка типов: условие 'if' должно быть Boolean, получено $condType.")
                checkStatement(stmt.thenBranch)
                stmt.elseBranch?.let { checkStatement(it) }
            }
            is Statement.WhileStatement -> {
                val condType = inferType(stmt.condition)
                if (condType != Type.Boolean)
                    throw RuntimeException("Ошибка типов: условие 'while' должно быть Boolean, получено $condType.")
                checkStatement(stmt.body)
            }
            is Statement.FunctionStatement -> {
                env.define(stmt.name, functionTypeOf(stmt))

                val outerEnv = env
                val outerReturn = currentReturnType
                env = TypeEnvironment(outerEnv)
                currentReturnType = stmt.returnType
                for (param in stmt.params) env.define(param.name, param.type)
                for (s in stmt.body) checkStatement(s)
                env = outerEnv
                currentReturnType = outerReturn
            }
            is Statement.ReturnStatement -> {
                val expected = currentReturnType
                    ?: throw RuntimeException("Ошибка типов: 'return' вне тела функции.")
                val actual = if (stmt.value != null) inferType(stmt.value) else Type.Null
                if (actual != expected)
                    throw RuntimeException(
                        "Ошибка типов: 'return' должен возвращать $expected, получено $actual."
                    )
            }
        }
    }

    fun inferType(expr: Expression): Type = when (expr) {
        is Expression.NumberExpression -> Type.Number
        is Expression.StringExpression -> Type.String
        is Expression.BooleanExpression -> Type.Boolean
        is Expression.VariableExpression -> env.get(expr.name)
        is Expression.AssignExpression -> {
            val valueType = inferType(expr.value)
            env.assign(expr.name, valueType)
            valueType
        }
        is Expression.CallExpression -> {
            val calleeType = inferType(expr.callee)
            if (calleeType !is Type.Function)
                throw RuntimeException(
                    "Ошибка типов: вызываемое значение не является функцией (тип $calleeType)."
                )
            if (expr.arguments.size != calleeType.params.size)
                throw RuntimeException(
                    "Ошибка типов: ожидается ${calleeType.params.size} аргументов, " +
                        "получено ${expr.arguments.size}."
                )
            for (i in expr.arguments.indices) {
                val argType = inferType(expr.arguments[i])
                if (argType != calleeType.params[i])
                    throw RuntimeException(
                        "Ошибка типов: аргумент ${i + 1} должен быть ${calleeType.params[i]}, " +
                            "получено $argType."
                    )
            }
            calleeType.returnType
        }
        is Expression.ArrayExpression -> {
            if (expr.elements.isEmpty())
                throw RuntimeException("Ошибка типов: невозможно вывести тип пустого массива.")
            val elementType = inferType(expr.elements[0])
            for (i in 1 until expr.elements.size) {
                val t = inferType(expr.elements[i])
                if (t != elementType)
                    throw RuntimeException(
                        "Ошибка типов: элементы массива должны быть одного типа, " +
                            "ожидался $elementType, получен $t."
                    )
            }
            Type.Array(elementType)
        }
        is Expression.IndexExpression -> {
            val arrayType = inferType(expr.array)
            if (arrayType !is Type.Array)
                throw RuntimeException("Ошибка типов: индексация применима только к массиву, получен $arrayType.")
            if (inferType(expr.index) != Type.Number)
                throw RuntimeException("Ошибка типов: индекс массива должен быть Number.")
            arrayType.elementType
        }
        is Expression.IndexAssignExpression -> {
            val arrayType = inferType(expr.array)
            if (arrayType !is Type.Array)
                throw RuntimeException("Ошибка типов: индексное присваивание применимо только к массиву, получен $arrayType.")
            if (inferType(expr.index) != Type.Number)
                throw RuntimeException("Ошибка типов: индекс массива должен быть Number.")
            val valueType = inferType(expr.value)
            if (valueType != arrayType.elementType)
                throw RuntimeException(
                    "Ошибка типов: нельзя присвоить $valueType элементу массива типа ${arrayType.elementType}."
                )
            valueType
        }
        is Expression.UnaryExpression -> when (expr.operator) {
            TokenType.MINUS -> {
                val t = inferType(expr.right)
                if (t != Type.Number)
                    throw RuntimeException("Ошибка типов: '-' применим только к Number, получено $t.")
                Type.Number
            }
            TokenType.EXCL -> {
                val t = inferType(expr.right)
                if (t != Type.Boolean)
                    throw RuntimeException("Ошибка типов: '!' применим только к Boolean, получено $t.")
                Type.Boolean
            }
            else -> throw RuntimeException("Ошибка типов: неизвестный унарный оператор ${expr.operator}.")
        }
        is Expression.BinaryExpression -> when (expr.operator) {
            TokenType.PLUS -> {
                val l = inferType(expr.left); val r = inferType(expr.right)
                when {
                    l == Type.Number && r == Type.Number -> Type.Number
                    l == Type.String && r == Type.String -> Type.String
                    else -> throw RuntimeException("Ошибка типов: нельзя применить '+' к $l и $r.")
                }
            }
            TokenType.MINUS, TokenType.STAR, TokenType.SLASH -> {
                val l = inferType(expr.left); val r = inferType(expr.right)
                if (l != Type.Number || r != Type.Number)
                    throw RuntimeException("Ошибка типов: арифметика требует Number, получено $l и $r.")
                Type.Number
            }
            TokenType.LT, TokenType.LTEQ, TokenType.GT, TokenType.GTEQ -> {
                val l = inferType(expr.left); val r = inferType(expr.right)
                if (l != Type.Number || r != Type.Number)
                    throw RuntimeException("Ошибка типов: операторы сравнения требуют Number, получено $l и $r.")
                Type.Boolean
            }
            TokenType.EQEQ, TokenType.NEQ -> {
                val l = inferType(expr.left); val r = inferType(expr.right)
                if (l != r)
                    throw RuntimeException("Ошибка типов: нельзя сравнивать $l и $r.")
                Type.Boolean
            }
            TokenType.AND, TokenType.OR -> {
                val l = inferType(expr.left); val r = inferType(expr.right)
                if (l != Type.Boolean || r != Type.Boolean)
                    throw RuntimeException("Ошибка типов: логические операторы требуют Boolean, получено $l и $r.")
                Type.Boolean
            }
            else -> throw RuntimeException("Ошибка типов: неизвестный оператор ${expr.operator}.")
        }
    }
}
