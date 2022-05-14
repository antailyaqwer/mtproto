package com.attafitamim.mtproto.core.generator.utils

import com.attafitamim.mtproto.core.generator.poet.factories.TypeNameFactory
import com.attafitamim.mtproto.core.generator.scheme.specs.TLPropertySpec
import com.attafitamim.mtproto.core.generator.scheme.specs.TLTypeSpec
import com.attafitamim.mtproto.core.generator.syntax.*
import com.attafitamim.mtproto.core.serialization.helpers.SerializationHelper
import com.attafitamim.mtproto.core.serialization.helpers.getTypeParseMethod
import com.attafitamim.mtproto.core.serialization.helpers.getTypeSerializeMethod
import com.attafitamim.mtproto.core.serialization.streams.TLInputStream
import com.attafitamim.mtproto.core.serialization.streams.TLOutputStream
import com.attafitamim.mtproto.core.types.TLMethod
import com.attafitamim.mtproto.core.types.TLObject
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.*
import kotlin.math.pow
import kotlin.reflect.*
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType

fun TypeSpec.Builder.addPrimaryConstructor(properties: List<PropertySpec>): TypeSpec.Builder {
    val propertySpecs = properties.map { property ->
        property.toBuilder().initializer(property.name).build()
    }

    val parameters = propertySpecs.map { propertySpec ->
        ParameterSpec.builder(propertySpec.name, propertySpec.type).build()
    }

    val constructor = FunSpec.constructorBuilder()
        .addParameters(parameters)
        .build()

    return this.primaryConstructor(constructor)
        .addProperties(propertySpecs)
}

fun createConstantPropertySpec(name: String, value: Any): PropertySpec {
    val constantName = camelToSnakeCase(name).uppercase(Locale.ROOT)
    return PropertySpec.builder(constantName, value::class)
        .mutable(false)
        .addModifiers(KModifier.CONST)
        .initializer("%L", value)
        .build()
}

fun KParameter.asParameterSpec() = ParameterSpec.builder(
    name.orEmpty(),
    type.javaType
).build()

fun Collection<KParameter>.asParameterSpecs() = map { kParameter ->
    kParameter.asParameterSpec()
}

fun KFunction3<*, *, *, *>.asFun3Builder(
    superTypeVariables: List<TypeName>?,
    returnType: TypeName
): FunSpec.Builder {
   val builder = FunSpec.builder(name)
       .addParameters(valueParameters.asParameterSpecs())

    val actualReturnType = if (!superTypeVariables.isNullOrEmpty() && returnType is ClassName) {
        builder.addModifiers(KModifier.INLINE)

        superTypeVariables.forEach { typeName ->
            if (typeName is TypeVariableName) {
                val reifiedTypeName = typeName.copy(reified = true)
                builder.addTypeVariable(reifiedTypeName)
            }
        }

        returnType.parameterizedBy(superTypeVariables)
    } else returnType

    return builder.returns(actualReturnType)
}

fun KFunction2<*, *, *>.asFun2Builder(
    superTypeVariables: List<TypeName>?,
    returnType: TypeName
): FunSpec.Builder {
    val builder = FunSpec.builder(name)
        .addParameters(valueParameters.asParameterSpecs())

    val actualReturnType = if (!superTypeVariables.isNullOrEmpty() && returnType is ClassName) {
        builder.addModifiers(KModifier.INLINE)

        superTypeVariables.forEach { typeName ->
            if (typeName is TypeVariableName) {
                val reifiedTypeName = typeName.copy(reified = true)
                builder.addTypeVariable(reifiedTypeName)
            }
        }

        returnType.parameterizedBy(superTypeVariables)
    } else returnType

    return builder.returns(actualReturnType)
}


fun KFunction1<*, *>.asFun1Builder(): FunSpec.Builder
    = FunSpec.builder(name)
        .addParameters(valueParameters.asParameterSpecs())
        .returns(returnType.asTypeName())

fun createFunctionCallStatement(
    parentName: String,
    functionName: String,
    vararg parameters: String
): String {
    val builder = StringBuilder()
        .append(
            parentName,
            INSTANCE_ACCESS_KEY,
            functionName,
            PARAMETER_OPEN_PARENTHESIS
        )

    if (!parameters.isNullOrEmpty()) {
        for (index in 0 until parameters.lastIndex) builder.append(
            parameters[index],
            PARAMETER_SEPARATOR
        )

        builder.append(parameters.last())
    }

    return builder.append(PARAMETER_CLOSE_PARENTHESIS)
        .toString()
}

fun createCostructorCallStatement(properties: List<String>? = null): String {
    val builder = StringBuilder()
        .append(TYPE_CONCAT_INDICATOR, PARAMETER_OPEN_PARENTHESIS)

    if (!properties.isNullOrEmpty()) {
        for (index in 0 until properties.lastIndex) builder.append(
            properties[index],
            PARAMETER_SEPARATOR
        )

        builder.append(properties.last())
    }

    return builder.append(PARAMETER_CLOSE_PARENTHESIS)
        .toString()
}

fun FunSpec.Builder.addReturnStatement(
    statement: String,
    vararg typeName: TypeName
): FunSpec.Builder {
    val returnStatement = StringBuilder()
        .append(RETURN_KEYWORD, KEYWORD_SEPARATOR, statement)
        .toString()

    return addStatement(returnStatement, *typeName)
}

fun FunSpec.Builder.addReturnConstructorStatement(
    returnType: TypeName,
    properties: List<PropertySpec>?,
    typeVariables: List<TypeVariableName>?
): FunSpec.Builder {
    val propertyNames = properties?.map(PropertySpec::name)
    val statement = createCostructorCallStatement(propertyNames)
    val actualClassName = if (!typeVariables.isNullOrEmpty() && returnType is ClassName) {
        returnType.parameterizedBy(typeVariables)
    } else returnType

    return addReturnStatement(statement, actualClassName)
}


fun FunSpec.Builder.addPropertyParseStatement(
    name: String,
    parseStatement: String,
    flag: Int? = null,
    vararg typeName: TypeName
): FunSpec.Builder = this.apply {
    if (flag != null) {
        val definitionStatement = java.lang.StringBuilder().append(
            VARIABLE_KEYWORD,
            KEYWORD_SEPARATOR,
            name,
            TYPE_SIGN,
            KEYWORD_SEPARATOR,
            TYPE_CONCAT_INDICATOR,
            NULLABLE_SIGN,
            INITIALIZATION_SIGN,
            NULL_KEYWORD
        ).toString()

        addStatement(definitionStatement, *typeName)

        val flagPosition = DEFAULT_FLAG_BASE.pow(flag).toInt()
        val flagCheckStatement = java.lang.StringBuilder().append(
            IF_KEYWORD,
            PARAMETER_OPEN_PARENTHESIS,
            PARAMETER_OPEN_PARENTHESIS,
            FLAGS_PROPERTY_NAME,
            AND_KEYWORD,
            flagPosition,
            PARAMETER_CLOSE_PARENTHESIS,
            NOT_EQUAL_SIGN,
            DEFAULT_FLAG_VALUE,
            PARAMETER_CLOSE_PARENTHESIS
        ).toString()

        beginControlFlow(flagCheckStatement)
        val assignStatement = java.lang.StringBuilder().append(
            name,
            INITIALIZATION_SIGN,
            parseStatement
        ).toString()

        addStatement(assignStatement, *typeName)
        endControlFlow()
    } else {
        val initializationStatement = java.lang.StringBuilder().append(
            IMMUTABLE_KEYWORD,
            KEYWORD_SEPARATOR,
            name,
            TYPE_SIGN,
            KEYWORD_SEPARATOR,
            TYPE_CONCAT_INDICATOR,
            INITIALIZATION_SIGN,
            parseStatement
        ).toString()

        addStatement(initializationStatement, *typeName)
    }
}


fun FunSpec.Builder.addLocalPropertySerializeStatement(
    name: String,
    type: KClass<*>,
    flag: Int? = null
): FunSpec.Builder = this.apply {
    val serializeMethod = getTypeSerializeMethod(type)

    val serializeStatement = java.lang.StringBuilder().append(
        OUTPUT_STREAM_NAME,
        INSTANCE_ACCESS_KEY,
        serializeMethod.name,
        PARAMETER_OPEN_PARENTHESIS,
        name,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()

    addPropertySerializeStatement(name, serializeStatement, flag)
}

fun FunSpec.Builder.addPropertySerializeStatement(
    name: String,
    serializeStatement: String,
    flag: Int? = null
): FunSpec.Builder = addSerializeFlagCheckStatement(name, flag) {
    addStatement(serializeStatement)
}

fun FunSpec.Builder.addSerializeFlagCheckStatement(
    name: String,
    flag: Int?,
    action: FunSpec.Builder.() -> FunSpec.Builder
): FunSpec.Builder = this.apply {
    if (flag != null) {
        val flagCheckStatement = java.lang.StringBuilder().append(
            IF_KEYWORD,
            PARAMETER_OPEN_PARENTHESIS,
            name,
            NOT_EQUAL_SIGN,
            NULL_KEYWORD,
            PARAMETER_CLOSE_PARENTHESIS,
        ).toString()

        beginControlFlow(flagCheckStatement)
        action()
        endControlFlow()
    } else {
        action()
    }
}

fun FunSpec.Builder.addLocalPropertyParseStatement(
    name: String,
    type: KClass<*>,
    flag: Int? = null
): FunSpec.Builder = this.apply {
    val parseMethod = getTypeParseMethod(type)
    val parseStatement = createFunctionCallStatement(INPUT_STREAM_NAME, parseMethod.name)

    addPropertyParseStatement(
        name,
        parseStatement,
        flag,
        type.asClassName()
    )
}

fun getParseStatement(type: KClass<*>): String {
    val parseMethod = getTypeParseMethod(type)

    return java.lang.StringBuilder().append(
        INPUT_STREAM_NAME,
        INSTANCE_ACCESS_KEY,
        parseMethod.name,
        PARAMETER_OPEN_PARENTHESIS,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()
}


fun FunSpec.Builder.addCollectionParseStatement(
    name: String,
    elementGeneric: TLTypeSpec.Generic,
    flag: Int?,
    typeNameFactory: TypeNameFactory
): FunSpec.Builder = this.apply {
    val sizeFieldPostfix = camelToTitleCase(List<*>::size.name)
    val sizeFieldName = java.lang.StringBuilder().append(
        name,
        sizeFieldPostfix
    ).toString()

    addLocalPropertyParseStatement(sizeFieldName, Int::class)

    val arrayInitializeStatement = buildString {
        append(
            IMMUTABLE_KEYWORD,
            KEYWORD_SEPARATOR,
            name,
            INITIALIZATION_SIGN,
            TYPE_CONCAT_INDICATOR,
            PARAMETER_OPEN_PARENTHESIS,
            sizeFieldName,
            PARAMETER_CLOSE_PARENTHESIS
        )
    }

    val genericTypeName = typeNameFactory.createTypeName(elementGeneric)
    val arrayTypeName = ArrayList::class.asTypeName().parameterizedBy(genericTypeName)
    addStatement(arrayInitializeStatement, arrayTypeName)

    val collectionSizeName = java.lang.StringBuilder().append(
        name,
        INSTANCE_ACCESS_KEY,
        List<*>::size.name
    ).toString()

    val iterationStatement = java.lang.StringBuilder().append(
        WHILE_KEYWORD,
        PARAMETER_OPEN_PARENTHESIS,
        collectionSizeName,
        LESS_THAN_SIGN,
        sizeFieldName,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()

    beginControlFlow(iterationStatement)
    addPropertyParseStatement(ARRAY_ELEMENT_NAME, elementGeneric, typeNameFactory)

    val arrayAddStatement = buildString {
        append(
            name,
            INSTANCE_ACCESS_KEY,
            "add",
            PARAMETER_OPEN_PARENTHESIS,
            ARRAY_ELEMENT_NAME,
            PARAMETER_CLOSE_PARENTHESIS
        )
    }

    addStatement(arrayAddStatement)
    endControlFlow()
}

fun FunSpec.Builder.addBytesParseStatement(
    name: String,
    typeSpec: TLTypeSpec.Structure.Bytes,
    flag: Int?,
    typeNameFactory: TypeNameFactory
): FunSpec.Builder = this.apply {
    val parseStatement = if (typeSpec.fixedSize != null) buildString {
        append(
            INPUT_STREAM_NAME,
            INSTANCE_ACCESS_KEY,
            TLInputStream::readBytes.name,
            PARAMETER_OPEN_PARENTHESIS,
            typeSpec.fixedSize,
            PARAMETER_CLOSE_PARENTHESIS
        )
    } else buildString {
        append(
            INPUT_STREAM_NAME,
            INSTANCE_ACCESS_KEY,
            TLInputStream::readByteArray.name,
            PARAMETER_OPEN_PARENTHESIS,
            PARAMETER_CLOSE_PARENTHESIS
        )
    }

    val typeName = typeNameFactory.createTypeName(typeSpec)
    addPropertyParseStatement(
        name,
        parseStatement,
        flag,
        typeName
    )
}


fun FunSpec.Builder.addPropertySerializeStatement(
    name: String,
    typeSpec: TLTypeSpec,
    flag: Int? = null
): FunSpec.Builder = this.apply {
    when(typeSpec) {
        is TLTypeSpec.TLType.Object,
        TLTypeSpec.TLType.SuperContainer,
        TLTypeSpec.TLType.SuperObject -> addObjectSerializeStatement(name, flag)
        is TLTypeSpec.TLType.Container -> addObjectSerializeStatement(name, flag)
        is TLTypeSpec.Generic.Parameter -> addPropertySerializeStatement(name, typeSpec.type, flag)
        is TLTypeSpec.Generic.Variable -> addPropertySerializeStatement(name, typeSpec.superType, flag)
        is TLTypeSpec.Primitive -> addLocalPropertySerializeStatement(name, typeSpec.clazz, flag)
        is TLTypeSpec.Structure.Collection -> addCollectionSerializeStatement(name, typeSpec.elementGeneric, flag)
        is TLTypeSpec.Structure.Bytes -> addBytesSerializeStatement(name, typeSpec, flag)
        TLTypeSpec.Flag -> { /* Do not serialize to stream */}
        TLTypeSpec.Type -> addGenericSerializeStatement(name, flag)
    }
}

fun FunSpec.Builder.addGenericSerializeStatement(
    name: String,
    flag: Int?
): FunSpec.Builder = this.apply {
    val serializeStatement = java.lang.StringBuilder().append(
        TYPE_CONCAT_INDICATOR,
        INSTANCE_ACCESS_KEY,
        TLObject::serialize.name,
        PARAMETER_OPEN_PARENTHESIS,
        OUTPUT_STREAM_NAME,
        PARAMETER_SEPARATOR,
        name,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()

    addSerializeFlagCheckStatement(name, flag) {
        addStatement(serializeStatement, SerializationHelper.javaClass.asTypeName())
    }
}

fun FunSpec.Builder.addPropertyParseStatement(
    name: String,
    typeSpec: TLTypeSpec,
    typeNameFactory: TypeNameFactory,
    flag: Int? = null
): FunSpec.Builder = this.apply {
    when(typeSpec) {
        is TLTypeSpec.TLType.Object -> addObjectParseStatement(name, typeSpec, flag, typeNameFactory)
        is TLTypeSpec.TLType.Container -> addContainerParseStatement(name, typeSpec, flag, typeNameFactory)
        is TLTypeSpec.Generic.Parameter -> addPropertyParseStatement(name, typeSpec.type, typeNameFactory, flag)
        is TLTypeSpec.Generic.Variable -> addGenericParseStatement(name, typeSpec, flag, typeNameFactory)
        is TLTypeSpec.Primitive -> addLocalPropertyParseStatement(name, typeSpec.clazz, flag)
        is TLTypeSpec.Structure.Bytes -> addBytesParseStatement(name, typeSpec, flag, typeNameFactory)
        is TLTypeSpec.Structure.Collection -> addCollectionParseStatement(
            name,
            typeSpec.elementGeneric,
            flag,
            typeNameFactory
        )

        TLTypeSpec.TLType.SuperContainer,
        TLTypeSpec.TLType.SuperObject -> addTypeParseStatement(
            name,
            TLTypeSpec.TLType.SuperObject::class.java.simpleName,
            typeNameFactory.createTypeName(typeSpec),
            flag
        )

        TLTypeSpec.Flag -> addFlagParseStatement(name, requireNotNull(flag))
        TLTypeSpec.Type ->  { /* Can't parse property without type info */ }
    }
}

fun FunSpec.Builder.addGenericParseStatement(
    name: String,
    typeSpec: TLTypeSpec.Generic.Variable,
    flag: Int?,
    typeNameFactory: TypeNameFactory
): FunSpec.Builder = this.apply {
    val typeName = typeNameFactory.createTypeVariableName(typeSpec)
    addTypeParseStatement(name, typeSpec.name, typeName, flag)
}

fun FunSpec.Builder.addTypeParseStatement(
    name: String,
    type: String,
    typeName: TypeName,
    flag: Int?
): FunSpec.Builder = this.apply {
    val typeClassName = createTypeVariableClassName(type)

    val parseStatement = java.lang.StringBuilder().append(
        TYPE_CONCAT_INDICATOR,
        INSTANCE_ACCESS_KEY,
        TLMethod<*>::parse.name,
        PARAMETER_OPEN_PARENTHESIS,
        INPUT_STREAM_NAME,
        PARAMETER_SEPARATOR,
        typeClassName,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()

    addPropertyParseStatement(
        name,
        parseStatement,
        flag,
        typeName,
        SerializationHelper.javaClass.asTypeName()
    )
}

fun FunSpec.Builder.addFlagParseStatement(
    name: String,
    position: Int
): FunSpec.Builder = this.apply {
    val flagPosition = DEFAULT_FLAG_BASE.pow(position).toInt()
    val flagCheckStatement = java.lang.StringBuilder().append(
        PARAMETER_OPEN_PARENTHESIS,
        FLAGS_PROPERTY_NAME,
        AND_KEYWORD,
        flagPosition,
        PARAMETER_CLOSE_PARENTHESIS,
        NOT_EQUAL_SIGN,
        DEFAULT_FLAG_VALUE
    ).toString()

    val initializationStatement = java.lang.StringBuilder().append(
        IMMUTABLE_KEYWORD,
        KEYWORD_SEPARATOR,
        name,
        TYPE_SIGN,
        KEYWORD_SEPARATOR,
        TYPE_CONCAT_INDICATOR,
        INITIALIZATION_SIGN,
        flagCheckStatement
    ).toString()

    addStatement(initializationStatement, Boolean::class)
}

fun FunSpec.Builder.addObjectSerializeStatement(
    name: String,
    flag: Int?
): FunSpec.Builder = this.apply {
    val objectSerializeStatement = java.lang.StringBuilder().append(
        name,
        INSTANCE_ACCESS_KEY,
        TLObject::serialize.name,
        PARAMETER_OPEN_PARENTHESIS,
        OUTPUT_STREAM_NAME,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()

    addPropertySerializeStatement(name, objectSerializeStatement, flag)
}

fun FunSpec.Builder.addObjectParseStatement(
    name: String,
    typeSpec: TLTypeSpec.TLType.Object,
    flag: Int?,
    typeNameFactory: TypeNameFactory
): FunSpec.Builder = this.apply {
    val genericTypes = typeSpec.generics?.map { generic ->
        typeNameFactory.createTypeName(generic)
    }

    val objectClassName = typeNameFactory.createClassName(typeSpec)

    val objectParseStatement = createFunctionCallStatement(
        objectClassName.simpleName,
        TLMethod<*>::parse.name,
        INPUT_STREAM_NAME
    )

    val objectTypeName = if (!genericTypes.isNullOrEmpty()) {
        objectClassName.parameterizedBy(genericTypes)
    } else objectClassName

    addPropertyParseStatement(
        name,
        objectParseStatement,
        flag,
        objectTypeName
    )
}

fun FunSpec.Builder.addContainerParseStatement(
    name: String,
    typeSpec: TLTypeSpec.TLType.Container,
    flag: Int?,
    typeNameFactory: TypeNameFactory
): FunSpec.Builder = this.apply {
    val genericTypes = typeSpec.generics?.map { generic ->
        typeNameFactory.createTypeName(generic)
    }

    val objectClassName = typeNameFactory.createClassName(typeSpec)

    val objectParseStatement = createFunctionCallStatement(
        objectClassName.simpleName,
        TLMethod<*>::parse.name,
        INPUT_STREAM_NAME
    )

    val objectTypeName = if (!genericTypes.isNullOrEmpty()) {
        objectClassName.parameterizedBy(genericTypes)
    } else objectClassName

    addPropertyParseStatement(
        name,
        objectParseStatement,
        flag,
        objectTypeName
    )
}

fun FunSpec.Builder.addCollectionSerializeStatement(
    name: String,
    elementGeneric: TLTypeSpec.Generic,
    flag: Int?
): FunSpec.Builder = addSerializeFlagCheckStatement(name, flag) {
    val collectionSizeName = java.lang.StringBuilder().append(
        name,
        INSTANCE_ACCESS_KEY,
        List<*>::size.name
    ).toString()

    addLocalPropertySerializeStatement(collectionSizeName, Int::class)

    val iterationStatement = java.lang.StringBuilder().append(
        name,
        INSTANCE_ACCESS_KEY,
        FOR_EACH_METHOD
    ).toString()

    beginControlFlow(iterationStatement)
    addPropertySerializeStatement(IT_KEYWORD, elementGeneric)
    endControlFlow()
}


fun FunSpec.Builder.addBytesSerializeStatement(
    name: String,
    typeSpec: TLTypeSpec.Structure.Bytes,
    flag: Int?
): FunSpec.Builder = this.addSerializeFlagCheckStatement(name, flag) {
    if (typeSpec.fixedSize != null) {
        val byteArraySizeName = buildString {
            append(
                name,
                INSTANCE_ACCESS_KEY,
                ByteArray::size.name
            )
        }

        val sizeValidationStatement = buildString {
            append(
                REQUIRE_METHOD,
                PARAMETER_OPEN_PARENTHESIS,
                byteArraySizeName,
                EQUAL_SIGN,
                typeSpec.fixedSize,
                PARAMETER_CLOSE_PARENTHESIS
            )
        }

        addStatement(sizeValidationStatement)
    }

    val serializeStatement = buildString {
        append(
            OUTPUT_STREAM_NAME,
            INSTANCE_ACCESS_KEY,
            TLOutputStream::writeByteArray.name,
            PARAMETER_OPEN_PARENTHESIS,
            name,
            PARAMETER_CLOSE_PARENTHESIS
        )
    }

    addStatement(serializeStatement)
}


fun FunSpec.Builder.addFlaggingStatement(
    mtPropertySpec: TLPropertySpec,
    flag: Int
): FunSpec.Builder = this.apply {
    val flagCheckStatement = if (mtPropertySpec.typeSpec == TLTypeSpec.Flag) {
        mtPropertySpec.name
    } else {
        java.lang.StringBuilder().append(
            mtPropertySpec.name,
            NOT_EQUAL_SIGN,
            NULL_KEYWORD
        ).toString()
    }

    val flagPosition = DEFAULT_FLAG_BASE.pow(flag).toInt()
    val flaggingStatement = java.lang.StringBuilder().append(
        FLAGS_PROPERTY_NAME,
        INITIALIZATION_SIGN,
        IF_KEYWORD,
        KEYWORD_SEPARATOR,
        PARAMETER_OPEN_PARENTHESIS,
        flagCheckStatement,
        PARAMETER_CLOSE_PARENTHESIS,
        KEYWORD_SEPARATOR,
        PARAMETER_OPEN_PARENTHESIS,
        FLAGS_PROPERTY_NAME,
        OR_KEYWORD,
        flagPosition,
        PARAMETER_CLOSE_PARENTHESIS,
        KEYWORD_SEPARATOR,
        ELSE_KEYWORD,
        KEYWORD_SEPARATOR,
        PARAMETER_OPEN_PARENTHESIS,
        FLAGS_PROPERTY_NAME,
        AND_KEYWORD,
        flagPosition,
        INSTANCE_ACCESS_KEY,
        INVERT_METHOD,
        PARAMETER_CLOSE_PARENTHESIS
    ).toString()

    addStatement(flaggingStatement)
}

fun createTypeVariableClassName(
    name: String
): String = buildString {
    append(
        titleToCamelCase(name),
        camelToTitleCase(CLASS_KEYWORD)
    )
}

fun FunSpec.Builder.addClassInitializationStatement(
    typeVariable: TypeVariableName
): FunSpec.Builder = this.apply {
    val propertyName = createTypeVariableClassName(typeVariable.name)

    val initializationStatement = buildString {
        append(
            IMMUTABLE_KEYWORD,
            KEYWORD_SEPARATOR,
            propertyName,
            TYPE_SIGN,
            KEYWORD_SEPARATOR,
            TYPE_CONCAT_INDICATOR,
            INITIALIZATION_SIGN,
            typeVariable.name,
            CLASS_ACCESS_KEY,
            CLASS_KEYWORD
        )
    }

    val classTypeName = KClass::class.asClassName()
        .parameterizedBy(typeVariable)

    addStatement(initializationStatement, classTypeName)
}

fun FunSpec.Builder.addFlaggingSerializationLogic(
    propertiesSpecs: List<TLPropertySpec>?
): FunSpec.Builder = this.apply {
    val flagsInitializationStatement = StringBuilder().append(
        VARIABLE_KEYWORD,
        KEYWORD_SEPARATOR,
        FLAGS_PROPERTY_NAME,
        INITIALIZATION_SIGN,
        FLAGS_DEFAULT_VALUE
    ).toString()

    addStatement(flagsInitializationStatement)
    propertiesSpecs?.forEach { mtPropertySpec ->
        if (mtPropertySpec.flag != null) addFlaggingStatement(
            mtPropertySpec,
            mtPropertySpec.flag
        )
    }

    addLocalPropertySerializeStatement(FLAGS_PROPERTY_NAME, Int::class)
}

fun FunSpec.Builder.addPropertiesSerializationLogic(
    propertiesSpecs: List<TLPropertySpec>?
): FunSpec.Builder = this.apply {
    propertiesSpecs?.forEach { tlPropertySpecs ->
        addPropertySerializeStatement(
            tlPropertySpecs.name,
            tlPropertySpecs.typeSpec,
            tlPropertySpecs.flag
        )
    }
}
