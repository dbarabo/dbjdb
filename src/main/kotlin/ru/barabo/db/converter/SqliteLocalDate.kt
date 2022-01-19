package ru.barabo.db.converter

import ru.barabo.db.ConverterValue
import ru.barabo.db.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

object SqliteLocalDate : ConverterValue {

    override fun convertFromStringToJava(value: String, javaType: Class<*>): Any? {
        if(value.isEmpty()) return null

        return convertFromBase(value, javaType)
    }

    override fun convertFromBase(value: Any, javaType: Class<*>): Any? {

        val milliseconds = when (value) {
            is Number -> value.toLong()
            is Date -> value.time
            is String -> value.toLong()
            else -> throw Exception("unknown class of value $value")
        }

        return when(javaType) {
            java.time.LocalDateTime::class.javaObjectType -> milliseconds.toLocalDateTime()

            java.time.LocalDate::class.javaObjectType -> milliseconds.toLocalDate()

            else -> throw Exception("unknown class of javaType $javaType")
        }
    }

    override fun convertToBase(value :Any) :Any = if(value is LocalDate)
        Type.localDateToSqlDate(value) else
        Type.localDateToSqlDate(value as LocalDateTime)
}

fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun Long.toLocalDateTime() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()