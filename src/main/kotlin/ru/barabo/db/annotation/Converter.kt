package ru.barabo.db.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Converter(val converterClazz : KClass<*>)