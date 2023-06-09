package ru.barabo.db.service

import ru.barabo.db.EditType
import ru.barabo.db.TemplateQuery

open class StoreFilterService<T: Any>(orm: TemplateQuery, clazz: Class<T>) : StoreService<T, List<T>>(orm, clazz) {

    private lateinit var filterdList: MutableList<T>

    @Volatile private var isFiltered = false

    @Volatile var selectedRowIndex: Int = 0
    set(value) {
        if(value < 0 || value >= dataListCount()) return

        field = value

        sentRefreshAllListener(EditType.CHANGE_CURSOR)
    }

    fun selectedEntity(): T? = if(selectedRowIndex < 0 || selectedRowIndex >= dataListCount()) null else getEntity(selectedRowIndex)

    fun setSelectedEntity(value: T) {
        val index = (if(isFiltered) filterdList.withIndex().firstOrNull { it.value == value }
                          else dataList.withIndex().firstOrNull { it.value == value }) ?: return

        selectedRowIndex = index.index
    }

    fun reselectRow() {

        val idRow = selectedEntity() ?: return

        orm.selectById(clazz, idRow, ::callBackSelectRow)

        sentRefreshAllListener(EditType.CHANGE_CURSOR)
    }

    protected open fun callBackSelectRow(item: T) {

        var selectedItem: T?

        synchronized(dataList) {

            selectedItem = selectedEntity() ?: return

            val index = dataList.indexOf(selectedItem)

            if(index < 0) return

            dataList[index] = item
        }

        if(isFiltered) {
            synchronized(filterdList) {
                val index = filterdList.indexOf(selectedItem)

                if(index < 0) return

                filterdList[index] = item
            }
        }
    }

    override fun initData() {
        isFiltered = false

        if(::filterdList.isInitialized) {
            filterdList.clear()
        } else {
            filterdList = ArrayList()
        }

        super.initData()
    }

    override fun elemRoot(): List<T> = if(isFiltered) filterdList else dataList

    override fun dataListCount() = if(isFiltered) filterdList.size else super.dataListCount()

    override fun getEntity(rowIndex: Int): T? = if(isFiltered) getItemByIndex(rowIndex) else super.getEntity(rowIndex)

    private fun getItemByIndex(index: Int) = if(index < filterdList.size) filterdList[index] else null

    @Synchronized
    fun setFilter(accessCriteria: (T) -> Boolean) {
        filterdList.clear()

        for(item in dataList) {
            if(accessCriteria(item)) filterdList += item
        }

        isFiltered = true

        sentRefreshAllListener(EditType.FILTER)
    }

    fun resetFilter() {
        isFiltered = false
        sentRefreshAllListener(EditType.FILTER)
    }
}