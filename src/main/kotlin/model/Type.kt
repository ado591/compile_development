package model

open class Type {
    object Number : Type() { override fun toString() = "Number" }
    object String: Type() { override fun toString() = "String"}
    object Boolean : Type() { override fun toString() = "Boolean" }
    object Null : Type() { override fun toString() = "Null" }

    class Array(val elementType: Type) : Type() {
        override fun toString(): kotlin.String = "Array<$elementType>"

        override fun equals(other: Any?): kotlin.Boolean =
            other is Array && elementType == other.elementType

        override fun hashCode(): Int = elementType.hashCode()
    }

    class Function(val params: List<Type>, val returnType: Type) : Type() {
        override fun toString(): kotlin.String =
            "(${params.joinToString(", ")}) -> $returnType"

        override fun equals(other: Any?): kotlin.Boolean =
            other is Function && params == other.params && returnType == other.returnType

        override fun hashCode(): Int = 31 * params.hashCode() + returnType.hashCode()
    }
}
