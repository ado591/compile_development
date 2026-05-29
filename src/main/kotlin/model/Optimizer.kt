package model

class Optimizer {

    fun optimize(statements: List<Statement>): List<Statement> = optimizeBlock(statements)

    private fun optimizeBlock(statements: List<Statement>): List<Statement> {
        val result = mutableListOf<Statement>()
        for (stmt in statements) {
            val optimized = optimizeStatement(stmt)
            result.add(optimized)
            if (optimized is Statement.ReturnStatement) break
        }
        return result
    }

    private fun optimizeStatement(stmt: Statement): Statement = when (stmt) {
        is Statement.ExpressionStatement ->
            Statement.ExpressionStatement(optimizeExpr(stmt.expression))

        is Statement.PrintStatement ->
            Statement.PrintStatement(optimizeExpr(stmt.expression))

        is Statement.VarStatement ->
            Statement.VarStatement(stmt.name, stmt.initializer?.let { optimizeExpr(it) })

        is Statement.BlockStatement ->
            Statement.BlockStatement(optimizeBlock(stmt.statements))

        is Statement.IfStatement -> {
            val cond = optimizeExpr(stmt.condition)
            when {
                cond is Expression.BooleanExpression && cond.value ->
                    optimizeStatement(stmt.thenBranch)
                cond is Expression.BooleanExpression && !cond.value ->
                    stmt.elseBranch?.let { optimizeStatement(it) }
                        ?: Statement.BlockStatement(emptyList())
                else -> Statement.IfStatement(
                    cond,
                    optimizeStatement(stmt.thenBranch),
                    stmt.elseBranch?.let { optimizeStatement(it) }
                )
            }
        }

        is Statement.WhileStatement -> {
            val cond = optimizeExpr(stmt.condition)
            if (cond is Expression.BooleanExpression && !cond.value)
                Statement.BlockStatement(emptyList())
            else
                Statement.WhileStatement(cond, optimizeStatement(stmt.body))
        }

        is Statement.FunctionStatement ->
            Statement.FunctionStatement(
                stmt.name, stmt.params, stmt.returnType, optimizeBlock(stmt.body)
            )

        is Statement.ReturnStatement ->
            Statement.ReturnStatement(stmt.value?.let { optimizeExpr(it) })
    }

    private fun optimizeExpr(expr: Expression): Expression = when (expr) {
        is Expression.NumberExpression,
        is Expression.StringExpression,
        is Expression.BooleanExpression,
        is Expression.VariableExpression -> expr

        is Expression.AssignExpression ->
            Expression.AssignExpression(expr.name, optimizeExpr(expr.value))

        is Expression.UnaryExpression ->
            foldUnary(expr.operator, optimizeExpr(expr.right))

        is Expression.BinaryExpression ->
            foldBinary(expr.operator, optimizeExpr(expr.left), optimizeExpr(expr.right))

        is Expression.CallExpression ->
            Expression.CallExpression(
                optimizeExpr(expr.callee),
                expr.arguments.map { optimizeExpr(it) }
            )

        is Expression.ArrayExpression ->
            Expression.ArrayExpression(expr.elements.map { optimizeExpr(it) })

        is Expression.IndexExpression ->
            Expression.IndexExpression(optimizeExpr(expr.array), optimizeExpr(expr.index))

        is Expression.IndexAssignExpression ->
            Expression.IndexAssignExpression(
                optimizeExpr(expr.array),
                optimizeExpr(expr.index),
                optimizeExpr(expr.value)
            )
    }

    private fun foldUnary(op: TokenType, right: Expression): Expression {
        if (op == TokenType.MINUS && right is Expression.NumberExpression)
            return Expression.NumberExpression(-right.value)
        if (op == TokenType.EXCL && right is Expression.BooleanExpression)
            return Expression.BooleanExpression(!right.value)
        return Expression.UnaryExpression(op, right)
    }

    private fun foldBinary(op: TokenType, left: Expression, right: Expression): Expression {
        if (left is Expression.NumberExpression && right is Expression.NumberExpression) {
            val l = left.value
            val r = right.value
            when (op) {
                TokenType.PLUS  -> return Expression.NumberExpression(l + r)
                TokenType.MINUS -> return Expression.NumberExpression(l - r)
                TokenType.STAR  -> return Expression.NumberExpression(l * r)
                TokenType.SLASH -> if (r != 0.0) return Expression.NumberExpression(l / r)
                TokenType.LT    -> return Expression.BooleanExpression(l < r)
                TokenType.LTEQ  -> return Expression.BooleanExpression(l <= r)
                TokenType.GT    -> return Expression.BooleanExpression(l > r)
                TokenType.GTEQ  -> return Expression.BooleanExpression(l >= r)
                TokenType.EQEQ  -> return Expression.BooleanExpression(l == r)
                TokenType.NEQ   -> return Expression.BooleanExpression(l != r)
                else -> {}
            }
        }

        if (left is Expression.StringExpression && right is Expression.StringExpression) {
            when (op) {
                TokenType.PLUS -> return Expression.StringExpression(left.value + right.value)
                TokenType.EQEQ -> return Expression.BooleanExpression(left.value == right.value)
                TokenType.NEQ  -> return Expression.BooleanExpression(left.value != right.value)
                else -> {}
            }
        }

        if (left is Expression.BooleanExpression && right is Expression.BooleanExpression) {
            when (op) {
                TokenType.AND  -> return Expression.BooleanExpression(left.value && right.value)
                TokenType.OR   -> return Expression.BooleanExpression(left.value || right.value)
                TokenType.EQEQ -> return Expression.BooleanExpression(left.value == right.value)
                TokenType.NEQ  -> return Expression.BooleanExpression(left.value != right.value)
                else -> {}
            }
        }

        when (op) {
            TokenType.PLUS -> {
                if (isNumber(right, 0.0)) return left
                if (isNumber(left, 0.0)) return right
            }
            TokenType.MINUS -> {
                if (isNumber(right, 0.0)) return left
            }
            TokenType.STAR -> {
                if (isNumber(right, 1.0)) return left
                if (isNumber(left, 1.0)) return right
                if (isNumber(right, 0.0) && isPure(left)) return Expression.NumberExpression(0.0)
                if (isNumber(left, 0.0) && isPure(right)) return Expression.NumberExpression(0.0)
            }
            TokenType.SLASH -> {
                if (isNumber(right, 1.0)) return left
            }
            TokenType.AND -> {
                if (left is Expression.BooleanExpression)
                    return if (left.value) right
                    else if (isPure(right)) left
                    else Expression.BinaryExpression(left, op, right)
                if (right is Expression.BooleanExpression && right.value) return left
            }
            TokenType.OR -> {
                if (left is Expression.BooleanExpression)
                    return if (!left.value) right
                    else if (isPure(right)) left
                    else Expression.BinaryExpression(left, op, right)
                if (right is Expression.BooleanExpression && !right.value) return left
            }
            else -> {}
        }

        return Expression.BinaryExpression(left, op, right)
    }

    private fun isNumber(expr: Expression, value: Double): Boolean =
        expr is Expression.NumberExpression && expr.value == value

    private fun isPure(expr: Expression): Boolean = when (expr) {
        is Expression.NumberExpression,
        is Expression.StringExpression,
        is Expression.BooleanExpression,
        is Expression.VariableExpression -> true
        is Expression.UnaryExpression -> isPure(expr.right)
        is Expression.BinaryExpression -> isPure(expr.left) && isPure(expr.right)
        is Expression.AssignExpression -> false
        is Expression.CallExpression -> false
        is Expression.ArrayExpression -> expr.elements.all { isPure(it) }
        is Expression.IndexExpression -> false
        is Expression.IndexAssignExpression -> false
    }
}
