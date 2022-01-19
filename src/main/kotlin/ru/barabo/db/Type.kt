package ru.barabo.db

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

enum class Type(val clazz: Class<*>, val sqlType: Int,
                private val converterToJava: (Any)->Any,
                private val converterToJavaFromString: (String)->Any?,
                private val increment: (Any)->Any,
                private val converterToDb: (Any)->Any = converterToJava) {

    BYTE(Byte::class.javaObjectType, java.sql.Types.TINYINT, { (it as Number).toByte() }, { it.toByteOrNull() }, { (it as Number).toByte() + 1 }  ),

    SMALLINT(Short::class.javaObjectType, java.sql.Types.SMALLINT, { (it as Number).toShort() }, { it.toShortOrNull() }, { (it as kotlin.Number).toShort() + 1 } ),

    INTP(Int::class.javaPrimitiveType!!, java.sql.Types.INTEGER, { (it as Number).toInt() },  { it.toIntOrNull() }, { (it as kotlin.Number).toInt() + 1 }  ),

    INT(Int::class.javaObjectType, java.sql.Types.INTEGER, { (it as Number).toInt() },  { it.toIntOrNull() }, { (it as kotlin.Number).toInt() + 1 }  ),

    LONGP(Long::class.javaPrimitiveType!!, java.sql.Types.BIGINT, { (it as Number).toLong() }, { it.toLongOrNull() }, { (it as Number).toLong() + 1L } ),

    LONG(Long::class.javaObjectType, java.sql.Types.BIGINT, { (it as Number).toLong()  }, { it.toLongOrNull() }, { (it as Number).toLong() + 1L } ),

    STRING(String::class.javaObjectType, java.sql.Types.VARCHAR, { it.toString() }, { it }, {} ),

    DECIMALP(Double::class.javaPrimitiveType!!, java.sql.Types.DECIMAL, { (it as Number).toDouble()}, {it.toDoubleOrNull() }, { (it as Number).toDouble() + 1.0} ),

    DECIMAL(Double::class.javaObjectType, java.sql.Types.DECIMAL, { (it as Number).toDouble()}, {it.toDoubleOrNull() }, { (it as Number).toDouble() + 1.0} ),

    DATETIME(java.time.LocalDateTime::class.javaObjectType, java.sql.Types.TIMESTAMP,
            { x -> (x as java.util.Date).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()},
            { x -> x.toLongOrNull()?.let{ java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() } },
            {},
            {java.sql.Date(Date.from((it as LocalDateTime).atZone(ZoneId.systemDefault()).toInstant()).time) }),

    DATE_SQL(java.sql.Date::class.javaObjectType, java.sql.Types.DATE,
        {x -> x},
        { x -> x.toLongOrNull()?.let{ java.sql.Date(it) } },
        {x -> x} ),

    DATE(java.time.LocalDate::class.javaObjectType, java.sql.Types.TIMESTAMP,
            { x -> (x as java.util.Date).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()},
            { x -> x.toLongOrNull()?.let{ java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } },
            {},
            {java.sql.Date(Date.from((it as LocalDate).atStartOfDay(ZoneId.systemDefault()).toInstant()).time) }),

    BIG_DECIMAL(BigDecimal::class.javaObjectType, java.sql.Types.NUMERIC,
            {x -> if(x is BigDecimal) x else java.math.BigDecimal(x.toString()) },
            { it.toBigDecimalOrNull() },
            { (it as BigDecimal).add(BigDecimal.ONE) },
            { (it as? BigDecimal)?.toString() ?: java.math.BigDecimal(it.toString()).toString() });

    companion object {
       // private val logger = LoggerFactory.getLogger(Type::class.java)

        private val HASH_CLASS_TYPES = Type.values().map { it -> Pair(it.clazz, it.sqlType) }.toMap()

        fun getSqlTypeByClass(clazz: Class<*>) :Int = HASH_CLASS_TYPES[clazz] ?:-1

        private val HASH_TYPES_CLASS = Type.values().map { it -> Pair(it.sqlType, it.clazz) }.toMap()

        private val CONVERTER_TO_SQL_BY_SQLTYPE = Type.values().map { it -> Pair(it.sqlType, it.converterToDb) }.toMap()

        fun convertToSqlBySqlType(sqlType: Int, javaValue: Any): Any? = CONVERTER_TO_SQL_BY_SQLTYPE[sqlType]?.invoke(javaValue)

        private fun errorNotFoundClassType(typeSql :Int) = "For type $typeSql Class not found"

        @Throws(SessionException::class)
        fun getClassBySqlType(typeSql:Int) :Class<*> {
           // logger.info("typeSql=$typeSql")

            return HASH_TYPES_CLASS[typeSql] ?: throw SessionException(errorNotFoundClassType(typeSql))
        }

        fun increment(clazz :Class<*>, value: Any) = HASH_CLASS_INCREMENT[clazz]!!.invoke(value)

        private val HASH_CLASS_INCREMENT = Type.values().map { it -> Pair(it.clazz, it.increment) }.toMap()

        private val HASH_CLASS_CONVERTER = Type.values().map { it -> Pair(it.clazz, it.converterToJava) }.toMap()

        fun convertValueToJavaTypeByClass(value :Any, clazz :Class<*>) :Any? = HASH_CLASS_CONVERTER[clazz]?.invoke(value)

        private val HASH_CLASS_STRING_CONVERTER = Type.values().map { it -> Pair(it.clazz, it.converterToJavaFromString) }.toMap()

        fun convertStringValueToJavaByClass(value: String, clazz: Class<*>) :Any? = HASH_CLASS_STRING_CONVERTER[clazz]?.invoke(value)

        fun isConverterExists(clazz :Class<*>) :Boolean {
            return HASH_CLASS_CONVERTER[clazz] != null
        }

        fun isNumberType(type :Int) :Boolean {
            return when (type) {
                java.sql.Types.INTEGER,
                java.sql.Types.SMALLINT,
                java.sql.Types.BIT,
                java.sql.Types.BIGINT,
                java.sql.Types.FLOAT,
                java.sql.Types.REAL,
                java.sql.Types.DOUBLE,
                java.sql.Types.NUMERIC,
                java.sql.Types.DECIMAL -> true
                else -> false
            }
        }

        fun isDateType(type :Int) :Boolean {
            return when (type) {
                java.sql.Types.DATE,
                java.sql.Types.TIME,
                java.sql.Types.TIMESTAMP -> true
                else -> false
            }
        }

        fun isStringType(type :Int) :Boolean {
            return when (type) {
                java.sql.Types.VARCHAR,
                java.sql.Types.CHAR -> true
                else -> false
            }
        }

        fun localDateToSqlDate(local : LocalDate) : java.sql.Date = local.toSqlDate()

        fun localDateToSqlDate(local : LocalDateTime) : java.sql.Date = local.toSqlDate()
    }
}

fun LocalDate.toSqlDate() = java.sql.Date(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()).time)

fun LocalDateTime.toSqlDate() = java.sql.Date(Date.from(this.atZone(ZoneId.systemDefault()).toInstant()).time)

