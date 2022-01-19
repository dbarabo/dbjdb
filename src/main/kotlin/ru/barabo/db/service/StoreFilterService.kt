package ru.barabo.db.service

import ru.barabo.db.EditType
import ru.barabo.db.TemplateQuery

open class StoreFilterService<T: Any>(orm: TemplateQuery, clazz: Class<T>) : StoreService<T, List<T>>(orm, clazz) {

    private val filterdList = ArrayList<T>()

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

        orm.selectById(clazz, idRow, ::callBackSelectData)

        sentRefreshAllListener(EditType.CHANGE_CURSOR)
    }

    private fun callBackSelectRow(item: T) {

        synchronized(dataList) {

            val selectedItem = selectedEntity() ?: return

            val index = dataList.indexOf(selectedItem)

            if(index < 0) return


            dataList[index] = item
        }
    }

    override fun initData() {
        isFiltered = false
        filterdList.clear()
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