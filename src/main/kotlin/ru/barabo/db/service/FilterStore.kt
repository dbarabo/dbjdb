package ru.barabo.db.service

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParseException
import java.util.*
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

interface FilterStore<E: Any> {

    val allData: MutableList<E>?

    var filterCriteria: MutableList<FilterCriteria>

    val indexFieldToFilter: Map<KCallable<*>, KClass<*>>

    fun getDataListStore(): MutableList<E>

    fun setCriteria(criteria: String) {

        fillCriteriaData(criteria)
    }

    private fun fillCriteriaData(criteria: String) {
        filterCriteria.clear()

        val words = criteria.split("[\\p{Punct}\\s]+")
                .filter { it.trim().length > 2 || it.toNumber() != null }
                .map { it.trim().uppercase() }

        val numbers = words.filter { it.toNumber() != null }.map{it.toNumber()!!}

        indexFieldToFilter.entries.forEach {

            when(it.value) {
                String::class -> if(words.isNotEmpty()) filterCriteria.add(FilterCriteria(it.key, words))

                Number::class -> if(numbers.isNotEmpty())  filterCriteria.add(FilterCriteria(it.key, numbers))
            }

        }
    }

    fun MutableList<FilterCriteria>.isAccess(row: E): Boolean =
            this.isEmpty() || this.firstOrNull { it.isAccess(row) } != null
}

private fun String.toNumber(): Number? = this.trim().parseToMoney()

fun String.parseToMoney() :Number? {

    val format = DecimalFormat(CURRENCY_MASK)
    format.negativePrefix = "-"
    format.isParseBigDecimal = true
    format.isDecimalSeparatorAlwaysShown = true
    format.decimalFormatSymbols = DecimalFormatSymbols(Locale("RU"))

    if (this.isEmpty() ) return null

    return try { format.parse(this) } catch (e: ParseException) { null }
}

private const val CURRENCY_MASK = "#,###.00"
