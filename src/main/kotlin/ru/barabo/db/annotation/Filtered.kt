package ru.barabo.db.annotation

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Filtered (val posFrom :Int, val posTo :Int)