package ru.barabo.db

enum class EditType {

    INSERT,
    EDIT,
    DELETE,
    FILTER,
    INIT,
    ALL,
    CHANGE_CURSOR;

    fun isEditable() = this in listOf(ALL, DELETE, EDIT, INSERT)
}