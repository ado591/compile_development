import model.Statement
import model.Expression.*
import model.Statement.*

class AstPrinter {

    fun print(statements: List<Statement>) {
        println("Программа")
        for (i in statements.indices) {
            printNode(statements[i], "", i == statements.size - 1)
        }
    }

    private fun printNode(node: Any?, indent: String, isLast: Boolean) {
        if (node == null) return

        val marker = if (isLast) "└── " else "├── " //тут скопипастила из гита, не умею рисовать красивые палочки :(
        print(indent + marker)

        val childIndent = indent + if (isLast) "    " else "│   "

        when (node) {
            is VarStatement -> {
                println("VarStatement: ${node.name}")
                node.initializer?.let { printNode(it, childIndent, true) }
            }

            is PrintStatement -> {
                println("PrintStatement")
                printNode(node.expression, childIndent, true)
            }

            is IfStatement -> {
                println("IfStatement")
                printNode(node.condition, childIndent, false)
                printNode(node.thenBranch, childIndent, node.elseBranch == null)

                node.elseBranch?.let { printNode(it, childIndent, true) }
            }

            is WhileStatement -> {
                println("WhileStatement")
                printNode(node.condition, childIndent, false)
                printNode(node.body, childIndent, true)
            }

            is FunctionStatement -> {
                val params = node.params.joinToString(", ") { "${it.name}: ${it.type}" }
                println("FunctionStatement: ${node.name}($params): ${node.returnType}")
                for (j in node.body.indices) {
                    printNode(node.body[j], childIndent, j == node.body.size - 1)
                }
            }

            is ReturnStatement -> {
                println("ReturnStatement")
                node.value?.let { printNode(it, childIndent, true) }
            }

            is CallExpression -> {
                println("CallExpression")
                printNode(node.callee, childIndent, node.arguments.isEmpty())
                for (j in node.arguments.indices) {
                    printNode(node.arguments[j], childIndent, j == node.arguments.size - 1)
                }
            }

            is BlockStatement -> {
                println("BlockStatement")
                for (j in node.statements.indices) {
                    printNode(node.statements[j], childIndent, j == node.statements.size - 1)
                }
            }

            is ExpressionStatement -> {
                println("ExpressionStatement")
                printNode(node.expression, childIndent, true)
            }

            is ArrayExpression -> {
                println("ArrayExpression")
                for (j in node.elements.indices) {
                    printNode(node.elements[j], childIndent, j == node.elements.size - 1)
                }
            }

            is IndexExpression -> {
                println("IndexExpression")
                printNode(node.array, childIndent, false)
                printNode(node.index, childIndent, true)
            }

            is IndexAssignExpression -> {
                println("IndexAssignExpression")
                printNode(node.array, childIndent, false)
                printNode(node.index, childIndent, false)
                printNode(node.value, childIndent, true)
            }

            is BinaryExpression -> {
                println("BinaryExpression: ${node.operator}")
                printNode(node.left, childIndent, false)
                printNode(node.right, childIndent, true)
            }

            is UnaryExpression -> {
                println("UnaryExpression: ${node.operator}")
                printNode(node.right, childIndent, true)
            }

            is AssignExpression -> {
                println("AssignExpression: ${node.name} =")
                printNode(node.value, childIndent, true)
            }

            is NumberExpression -> {
                println("Number: ${node.value}")
            }

            is StringExpression -> {
                println("String: \"${node.value}\"")
            }

            is BooleanExpression -> {
                println("Boolean: ${node.value}")
            }

            is VariableExpression -> {
                println("Variable: ${node.name}")
            }

            else -> {
                println("Unknown Node: ${node::class.simpleName}")
            }
        }
    }
}