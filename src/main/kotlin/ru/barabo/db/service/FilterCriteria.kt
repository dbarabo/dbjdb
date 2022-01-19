package ru.barabo.db.service

import kotlin.reflect.KCallable

data class FilterCriteria(val getter: KCallable<*>, val andValues: List<Any>) {

    //private val logger = LoggerFactory.getLogger(FilterCriteria::class.java)

    fun <E> isAccess(row: E): Boolean {

        val valueRow = getter.call(row) ?: return false

        val isNotAccess = andValues.map {
            when (it) {
                is String -> valueRow.toString().uppercase().indexOf(it) >= 0
                is Number -> (valueRow as Number).toInt() == it.toInt()
                else -> false
            }
        }.firstOrNull { !it }

        return isNotAccess ?: true
    }
}