package ru.barabo.db

enum class EditType {

    INSERT,
    EDIT,
    DELETE,
    FILTER,
    INIT,
    ALL,
    CHANGE_CURSOR;

    fun isEditable() = this in listOf(EditType.ALL, EditType.DELETE, EditType.EDIT, EditType.INSERT)
}