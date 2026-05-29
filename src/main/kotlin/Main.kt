import model.Interpreter
import model.Lexer
import model.Optimizer
import model.Parser
import model.TypeChecker

fun runProgram(title: String, source: String) {
    println("$title")

    val tokens = Lexer(source).tokenize()
    val ast = Parser(tokens).parse()

    println("Исходное дерево")
    AstPrinter().print(ast)

    try {
        TypeChecker().check(ast)
        println("Типы корректны.")
    } catch (e: RuntimeException) {
        println(e.message)
        return
    }

    val optimized = Optimizer().optimize(ast)
    println()
    println("Оптимизированное дерево")
    AstPrinter().print(optimized)

    println()
    println("Вывод программы")
    Interpreter().interpret(optimized)
    println()
}

fun main() {
    val example1 = """
        fun add(a: Number, b: Number): Number {
            return a + b;
        }

        fun factorial(n: Number): Number {
            if (n <= 1) return 1;
            return n * factorial(n - 1);
        }

        fun greet(name: String): String {
            return "Привет, " + name;
        }

        var sum = add(10, 32);
        var folded = 2 * 3 + 4 * 1 + sum * 0;
        print(sum);
        print(factorial(5));
        print(greet("Oleg" + " " + "Shipduler"));
        if (false) { print("недостижимо"); }
        if (3 < 5) { print(folded); }
    """.trimIndent()

    val example2 = """
        fun power(base: Number, exp: Number): Number {
            var result = 1;
            var i = 0;
            while (i < exp) {
                result = result * base;
                i = i + 1;
            }
            return result;
            print("недостижимо после return");
        }

        fun classify(n: Number): String {
            if (n < 0) return "отрицательное";
            if (n == 0) return "ноль";
            return "положительное";
        }

        print(power(2, 10));
        print(classify(0 - 5));
        print(classify(0));
        print(classify(42));
    """.trimIndent()

    val example3 = """
        var nums = [10, 20, 30, 40];
        print(nums[0]);
        nums[1] = nums[0] + nums[3];
        print(nums[1]);
        var i = 0;
        while (i < 4) {
            print(nums[i]);
            i = i + 1;
        }
        var words = ["раз" + "два", "три"];
        print(words[0]);
        var matrix = [[1, 2], [3, 4]];
        print(matrix[1][0]);
    """.trimIndent()

    runProgram("Пример: функции и оптимизатор", example1)
    runProgram("Пример: цикл, ветвление и мёртвый код", example2)
    runProgram("Пример: массивы", example3)
}
