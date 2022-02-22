package com.attafitamim.mtproto.core.generator.parsers

import com.attafitamim.mtproto.core.generator.specs.MTTypeSpec

object MTTypeParser {

    private const val LIST_OPENING_BRACKET = "["
    private const val LIST_CLOSING_BRACKET = "]"

    private const val NAMESPACE_SEPARATOR = "."

    private const val GENERIC_OPENING_BRACKET = "<"
    private const val GENERIC_CLOSING_BRACKET = ">"
    private const val GENERIC_TYPE_INDICATOR = ":"
    private const val GENERIC_SEPARATOR = ","

    private val primitiveTypes = mapOf(
        "string" to String::class,
        "int" to Int::class,
        "long" to Long::class,
        "true" to Boolean::class,
        "double" to Double::class,
        "bytes" to ByteArray::class,
        "Type" to Any::class
    )

    fun parseGenericVariable(
        genericScheme: String,
        genericVariables: Map<String, MTTypeSpec.Generic.Variable>?
    ): MTTypeSpec.Generic.Variable {
        val name = genericScheme.substringBefore(GENERIC_TYPE_INDICATOR)
        val typeDescription = genericScheme.substringAfter(GENERIC_TYPE_INDICATOR)

        val superTypeSpec = parseType(typeDescription, genericVariables)
        return MTTypeSpec.Generic.Variable(name, superTypeSpec)
    }

    fun parseType(
        typeScheme: String,
        genericVariables: Map<String, MTTypeSpec.Generic.Variable>?
    ): MTTypeSpec = when {
        // is a generic variable
        !genericVariables.isNullOrEmpty() && genericVariables.containsKey(typeScheme) -> {
            genericVariables.getValue(typeScheme)
        }

        // is a local primitive type
        primitiveTypes.containsKey(typeScheme) -> {
            val typeClass = primitiveTypes.getValue(typeScheme)
            MTTypeSpec.Local(typeClass)
        }

        // is a list of types
        typeScheme.startsWith(LIST_OPENING_BRACKET) && typeScheme.endsWith(LIST_CLOSING_BRACKET) -> {
            val genericName = typeScheme.removePrefix(LIST_OPENING_BRACKET)
                .removeSuffix(LIST_CLOSING_BRACKET)
                .trim()

            val genericSpec = parseGeneric(genericName, genericVariables)
            MTTypeSpec.Structure.Collection(List::class, genericSpec)
        }

        // is an MTObject type
        else -> {
            var namespace: String? = null
            var name: String = typeScheme

            var generics: List<MTTypeSpec.Generic>? = null
            if (name.contains(GENERIC_OPENING_BRACKET) && name.endsWith(GENERIC_CLOSING_BRACKET)) {
                generics = name.substringAfter(GENERIC_OPENING_BRACKET)
                    .removeSuffix(GENERIC_CLOSING_BRACKET)
                    .split(GENERIC_SEPARATOR)
                    .map { genericScheme ->
                        parseGeneric(genericScheme, genericVariables)
                    }

                name = name.substringBefore(GENERIC_OPENING_BRACKET)
            }

            if (name.contains(NAMESPACE_SEPARATOR)) {
                namespace = name.substringBeforeLast(NAMESPACE_SEPARATOR)
                name = name.substringAfterLast(NAMESPACE_SEPARATOR)
            }

            MTTypeSpec.Object(namespace, name, generics)
        }
    }


    private fun parseGeneric(
        genericScheme: String,
        genericVariables: Map<String, MTTypeSpec.Generic.Variable>?
    ): MTTypeSpec.Generic {
        val genericVariable = genericVariables?.get(genericScheme)
        if (genericVariable != null) return genericVariable

        val typeSpec = parseType(genericScheme, genericVariables)
        return MTTypeSpec.Generic.Parameter(typeSpec)
    }
}