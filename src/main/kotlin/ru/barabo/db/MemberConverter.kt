package ru.barabo.db

import org.slf4j.LoggerFactory
import ru.barabo.db.annotation.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaType


internal data class MemberConverter(private val member: KMutableProperty<*>,
                                    private val converter: ConverterValue?,
                                    private val manyToOnePrefix: String?) {

    fun setJavaValueToField(newObject: Any, sqlValue: Any, row: Array<Any?>, columnNames: Array<String>): Any? {

        val javaValue = sqlValueToJava(newObject, sqlValue, row, columnNames)

        member.setter.call(newObject, javaValue)

        return javaValue
    }

    fun setJavaValueToFieldShort(entity: Any, sqlValue: Any): Any? {
        val javaValue = sqlValueToJava(entity, sqlValue)

        member.setter.call(entity, javaValue)

        return javaValue
    }

    fun setJavaValueFieldFromString(entity: Any, value: String): Any? {

        val javaValue = stringValueToJava(entity, value)

        member.setter.call(entity, javaValue)

        return javaValue
    }

    fun getSqlValueFromJavaObject(entityItem: Any): Any? {

        val javaValue = member.getter.call(entityItem) ?: return null

        return getSqlValueFromJavaValue(javaValue)
    }

    private fun getSqlValueFromJavaValue(javaValue: Any): Any? {

        return when {
            manyToOnePrefix != null -> getIdSqlValueFromEntity(javaValue)
            converter != null -> converter.convertToBase(javaValue)
            else -> member.javaValueToSql(javaValue)
        }
    }

    private fun getIdSqlValueFromEntity(subEntityItem: Any): Any? {

        val subMember = getIdMember(subEntityItem::class.java) ?: return null

        val javaValue = subMember.getter.call(subEntityItem) ?: return null

        return subMember.javaValueToSql(javaValue)
    }

    private fun sqlValueToJava(newObject: Any, sqlValue: Any, row: Array<Any?>? = null, columnNames: Array<String>? = null): Any? {

        return when {
            manyToOnePrefix != null -> manyToOneJavaObject(newObject, sqlValue, row, columnNames)
            converter != null -> converter.convertFromBase(sqlValue, member.returnType.javaType as Class<*>)
            else -> member.valueToJava(sqlValue)
        }
    }

    private fun stringValueToJava(entity: Any, value: String): Any? {
        return when {
            manyToOnePrefix != null -> manyToOneJavaObjectStringValue(entity, value)
            converter != null -> converter.convertFromStringToJava(value, member.returnType.javaType as Class<*>)
            else -> member.valueStringToJava(value)
        }
    }

    private fun manyToOneJavaObjectStringValue(parentEntity: Any, value: String): Any? {

        val javaType: Class<*> = member.returnType.javaType as Class<*>

        val objectEntity = member.getter.call(parentEntity) ?: javaType.newInstance()

        setIdByString(javaType, objectEntity, value)

        return objectEntity
    }

    private fun manyToOneJavaObject(parentItem: Any, value: Any, row: Array<Any?>? = null, columnNames: Array<String>? = null): Any {

        val javaType: Class<*> = member.returnType.javaType as Class<*>

        val objectItem = member.getter.call(parentItem) ?: javaType.newInstance()

        setId(javaType, objectItem, value)

        return columnNames?.let { fillManyToOneColumns(objectItem, columnNames, row!!) } ?: objectItem
    }

    private fun fillManyToOneColumns(objectItem: Any, columnNames: Array<String>, row: Array<Any?>): Any {

        val prefixColumn = manyToOnePrefix ?: return objectItem

        val javaType: Class<*> = member.returnType.javaType as Class<*>

        columnNames.forEachIndexed { index, columnName ->

            if(columnName.indexOf(prefixColumn) != 0) return@forEachIndexed

            val subColumn = columnName.substring(prefixColumn.length)

            val valueColumn = row[index] ?: return@forEachIndexed

            setValueSubColumn(javaType, objectItem, valueColumn, subColumn)
        }

        return objectItem
    }

    private fun setIdByString(javaType: Class<*>, objectEntity: Any, value: String) {

        val memberId = getIdMember(javaType) ?: return

        val javaValue = memberId.valueStringToJava(value) ?: return

        memberId.setter.call(objectEntity, javaValue)
    }

    private fun setId(javaType: Class<*>, objectItem: Any, sqlValue: Any) {

        val memberId = getIdMember(javaType) ?: return

        val javaValue = memberId.valueToJava(sqlValue) ?: return

        memberId.setter.call(objectItem, javaValue)
    }

    private fun setValueSubColumn(javaType: Class<*>, objectItem: Any, sqlValue: Any, columnName: String) {

        val memberCol= getMemberByColumnName(javaType, columnName) ?: return

        val javaValue = memberCol.valueToJava(sqlValue) ?: return

        memberCol.setter.call(objectItem, javaValue)
    }
}

internal fun isNullIdItem(entityItem: Any): Boolean {

    val member = getIdMember(entityItem::class.java)

//    logger.error("member=$member")
//    logger.error("entityItem=$entityItem")

    val getValue = member?.getter?.call(entityItem)
//    logger.error("getValue=$getValue")

    return getValue == null
}

private fun mapMemberToSqlValue(entityItem: Any, memberColumn: Pair<String, MemberConverter>): Pair<String, Any?> {

    val value = memberColumn.second.getSqlValueFromJavaObject(entityItem)

    return Pair(memberColumn.first, value)
}

fun getInsertListPairs(entityItem: Any): List<Pair<String, Any?>>  =
        getColumnsInsertAnnotation(entityItem::class.java)
                .toPairValueList(entityItem)


fun getUpdateListPairs(entityItem: Any): List<Pair<String, Any?>> =
        getColumnsUpdateAnnotation(entityItem::class.java)
                .toPairValueList(entityItem)

fun getBackupListPairs(entityItem: Any): List<Pair<String, Any?>> =
        getColumnsBackupAnnotation(entityItem::class.java)
                .toPairValueList(entityItem)


private fun Map<String, MemberConverter>.toPairValueList(entityItem: Any): List<Pair<String, Any?>> =
        map { mapMemberToSqlValue(entityItem, Pair(it.key, it.value)) }


/**
 * copy properties with ColumnName annotation (it's not ReadOnly) from this to destination
 */
fun <T: Any> T.setFieldEditValues(destination: T) {

    javaClass.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>()
            .filter { it.findAnnotation<ColumnName>()?.name != null &&
                    it.findAnnotation<ReadOnly>() == null }
            .forEach { member ->

                member.setter.call(destination, member.getter.call(this))
            }
}

fun <T: Any> T.copyByReflection(): T {

    val copyMethod = this::class.memberFunctions.first { it.name == "copy"}

    val instanceParams = copyMethod.instanceParameter!!

    @Suppress("UNCHECKED_CAST")
    return copyMethod.callBy(mapOf(instanceParams to this)) as T
}

internal fun getColumnsAnnotation(row: Class<*>): Map<String, MemberConverter> = getColumnsAnnotationByFilter(row)

internal fun getColumnsInsertAnnotation(row: Class<*>): Map<String, MemberConverter> =
        getColumnsAnnotationByFilter(row) { it.findAnnotation<ColumnName>()?.name != null &&
                it.findAnnotation<ReadOnly>() == null
        }

private fun getColumnsUpdateAnnotation(row: Class<*>): Map<String, MemberConverter> =
        getColumnsAnnotationByFilter(row) { it.findAnnotation<ColumnName>()?.name != null &&
                it.findAnnotation<ReadOnly>() == null &&
                it.findAnnotation<SequenceName>() == null
        }

private fun getColumnsBackupAnnotation(row: Class<*>): Map<String, MemberConverter> =
        getColumnsAnnotationByFilter(row) { it.findAnnotation<ColumnName>()?.name != null &&
                it.findAnnotation<ReadOnly>() == null &&
                it.findAnnotation<Transient>() == null
        }

private fun getColumnsAnnotationByFilter(row: Class<*>,
                                         filtered: (KMutableProperty<*>)->Boolean =
                                                 { it.findAnnotation<ColumnName>()?.name != null}): Map<String, MemberConverter> {

    val columnsAnnotation = HashMap<String, MemberConverter>()

    row.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>()
            .filter { filtered(it) }
            .forEach { member ->

                val columnAnnotation = member.getColumnAnnotation() ?: return@forEach

                columnsAnnotation += columnAnnotation
            }

    return columnsAnnotation
}

internal fun setSyncValue(entityItem: Any, syncValue: Any?): Boolean {
    val member = entityItem::class.java.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>().firstOrNull {
        it.findAnnotation<Transient>() != null &&
        it.findAnnotation<ReadOnly>() == null &&
        it.findAnnotation<ColumnName>() != null &&
        it.findAnnotation<ColumnType>()?.type == java.sql.Types.INTEGER
    } ?: return false

    if(member.getter.call(entityItem) == null) {
        member.setter.call(entityItem, syncValue)
    }

    return true
}

fun getBackupColumnsTable(row: Class<*>) = getColumnsByFilter(row) {
    it.findAnnotation<ReadOnly>() == null && it.findAnnotation<Transient>() == null
}

fun getTransientColumns(row: Class<*>) = getColumnsByFilter(row) { it.findAnnotation<ReadOnly>() == null && it.findAnnotation<Transient>() != null }

private fun getColumnsByFilter(row: Class<*>, filtered: (KMutableProperty<*>)->Boolean): List<String> =
    row.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>()
            .filter { it.findAnnotation<ColumnName>()?.name != null && filtered(it) }
            .map { it.findAnnotation<ColumnName>()!!.name }


private fun KMutableProperty<*>.getColumnAnnotation(): Pair<String, MemberConverter>? {

    val columnName = findAnnotation<ColumnName>()?.name ?: return null

    val memberConverter = getMemberConvertor()

    return Pair(columnName, memberConverter)
}

private fun KMutableProperty<*>.getMemberConvertor(): MemberConverter {

    val converter = findAnnotation<Converter>()?.converterClazz?.instanceCreateOrGet()

    val manyToOnePrefix = findAnnotation<ManyToOne>()?.prefixColumns

    return MemberConverter(this, converter as? ConverterValue, manyToOnePrefix)
}

private fun KClass<*>.instanceCreateOrGet() = this.objectInstance ?: this.java.newInstance()

internal fun getIdColumnName(javaType: Class<*>): String? = getIdMember(javaType)?.findAnnotation<ColumnName>()?.name

internal fun getIdMember(javaType: Class<*>): KMutableProperty<*>? = javaType.kotlin.declaredMemberProperties
        .filterIsInstance<KMutableProperty<*>>().firstOrNull { it.findAnnotation<SequenceName>() != null }

internal fun getMemberEntityFields(entityClass: Class<*>): List<KMutableProperty<*>> = entityClass.kotlin.declaredMemberProperties
        .filterIsInstance<KMutableProperty<*>>().filter { it.findAnnotation<ManyToOne>() != null }

private fun getMemberByColumnName(javaType: Class<*>, columnName: String) = javaType.kotlin.declaredMemberProperties
        .filterIsInstance<KMutableProperty<*>>().firstOrNull {
            columnName.equals(it.findAnnotation<ColumnName>()?.name, true)
        }

fun KMutableProperty<*>.valueToJava(sqlValue: Any): Any? {

    val javaType :Class<*> = returnType.javaType as Class<*>

    return Type.convertValueToJavaTypeByClass(sqlValue, javaType) //SqliteType.sqlValueConvertToJavaValueByJavaType(sqlValue, javaType)
}

private fun KMutableProperty<*>.valueStringToJava(value: String): Any? {
    val javaType :Class<*> = returnType.javaType as Class<*>

    return Type.convertStringValueToJavaByClass(value, javaType)
}

private fun KMutableProperty<*>.javaValueToSql(javaValue: Any): Any =
        findAnnotation<ColumnType>()?.type?.let { getSqlValueBySqlType(it, javaValue) } ?: javaValue

private fun getSqlValueBySqlType(sqlType: Int, javaValue: Any): Any? =
        Type.convertToSqlBySqlType(sqlType, javaValue)


fun getPropertyByColumn(row :Class<*>) :Map<String, KMutableProperty<*>> {
    val propertyByColumn = HashMap<String, KMutableProperty<*>>()

    row.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>()
            .filter { it.findAnnotation<ColumnName>()?.name != null}.forEach { member ->

                val columnName = member.findAnnotation<ColumnName>()?.name?.uppercase()?:return@forEach

                propertyByColumn[columnName] = member

                val prefix = member.findAnnotation<ManyToOne>()?.prefixColumns?.uppercase()?:return@forEach

                val subMemberType :Class<*> = member.returnType.javaType as Class<*>

                subMemberType.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>()
                        .filter { it.findAnnotation<ColumnName>()?.name != null}.forEach intern@ {
                            val subColumnName =it.findAnnotation<ColumnName>()?.name?.uppercase()?:return@intern

                            propertyByColumn["$prefix$subColumnName"] = member
                        }
            }

    return propertyByColumn
}

private val logger = LoggerFactory.getLogger(MemberConverter::class.simpleName)!!

@Throws(SessionException::class)
internal fun valueToJava(entity: Any, value: Any, member: KMutableProperty<*>, columnName :String) :Any? {

    val javaType :Class<*> = member.returnType.javaType as Class<*>

    val converterClass = member.findAnnotation<Converter>()?.converterClazz

    if(converterClass != null) {
        val instance = converterClass.objectInstance ?: converterClass.java.newInstance()

        return (instance as ConverterValue).convertFromBase(value, javaType)
    }
    if(Type.isConverterExists(javaType)) {
        return Type.convertValueToJavaTypeByClass(value, javaType)
    }

    val manyToOneClass =member.findAnnotation<ManyToOne>()

    if(manyToOneClass != null) {

        return manyToOneValue(entity, member, columnName, value)
    }

    return value
}

internal fun <T: Any> getEntityFromSql(entity: T, columnsAnnotation: Map<String, MemberConverter>, row: Array<Any?>, columns: List<String>): T {

    for ((index, column) in columns.withIndex()) {

        if(row[index] == null) continue

        val memberConverter = columnsAnnotation[column] ?: continue

        memberConverter.setJavaValueToFieldShort(entity, row[index]!!)
    }

    return entity
}

internal fun <T: Any> getEntityFromString(entity: T, columnsAnnotation: Map<String, MemberConverter>, row: List<String>, columns: List<String>): T {
    for ((index, column) in columns.withIndex()) {

        if(row[index].isEmpty() || row[index] == NULL) continue

        val memberConverter = columnsAnnotation[column] ?: continue

        memberConverter.setJavaValueFieldFromString(entity, row[index])
    }

    return entity
}

private const val NULL = "null"


@Throws(SessionException::class)
internal fun getTableName(row: Class<*>): String = row.kotlin.findAnnotation<TableName>()?.name
        ?: throw SessionException(errorNotFoundAnnotationTableName(row.simpleName))

@Throws(SessionException::class)
internal fun getTableName(entity :Any) :String = entity::class.findAnnotation<TableName>()?.name
        ?: throw SessionException(errorNotFoundAnnotationTableName(entity::class.simpleName))

private fun errorNotFoundAnnotationTableName(className :String?) = "Annotation @TableName not found for class $className"

private fun manyToOneValue(parentItem :Any, member :KMutableProperty<*>, columnName :String, value :Any): Any {

    val javaType :Class<*> = member.returnType.javaType as Class<*>

    var objectValue = member.getter.call(parentItem)

    val prefix = member.findAnnotation<ManyToOne>()?.prefixColumns

    val column =
            if(objectValue == null) {
                objectValue = javaType.newInstance()

                ID_COLUMN
            } else {
                columnName.substring(prefix?.length?:0)
            }

    setMemberValue(javaType, objectValue!!, value, column)

    return objectValue
}

private fun setMemberValue(clazz :Class<*>, objectMember :Any, value :Any, columnName :String) {

    val member = clazz.kotlin.declaredMemberProperties.filterIsInstance<KMutableProperty<*>>().firstOrNull {
        it.findAnnotation<ColumnName>()?.name?.uppercase() == columnName.uppercase()
    } ?: return

    val javaValue = valueToJava(objectMember, value, member, columnName) ?: return

    member.setter.call(objectMember, javaValue)
}

internal const val ID_COLUMN = "ID"

internal fun getSqlParamsFromEntity(entity: Any, memberColumns: Collection<MemberConverter>): Array<Any?> {

    val params: Array<Any?> = arrayOfNulls(memberColumns.size)

    //logger.error("entity=$entity")
    //logger.error("memberColumns.size=${memberColumns.size}")

    for ((index, member) in memberColumns.withIndex()) {
        params[index] = member.getSqlValueFromJavaObject(entity)
        //logger.error("params[$index]=${params[index]}")
    }

    return params
}