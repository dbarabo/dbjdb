package ru.barabo.db.service

import ru.barabo.db.EditType
import ru.barabo.db.EditType.*
import ru.barabo.db.SessionException
import ru.barabo.db.SessionSetting
import ru.barabo.db.TemplateQuery
import ru.barabo.db.annotation.ParamsSelect
import ru.barabo.db.annotation.QuerySelect
import java.awt.EventQueue

abstract class StoreService<T: Any, out G>(protected val orm: TemplateQuery, val clazz: Class<T>) {

    // private val logger = Logger.getLogger(StoreService::class.simpleName)!!

    private val listenerList = ArrayList<StoreListener<G>>()

    protected val dataList = ArrayList<T>()

    open fun dataListCount() = dataList.size

    open fun getEntity(rowIndex: Int): T? = if(rowIndex < dataList.size) dataList[rowIndex] else null

    @Volatile
    private var startedLongTransaction: LongTransactState = LongTransactState.NONE_LONG_TRANSACT

    init {
        initData()
    }

    abstract fun elemRoot(): G

    protected open fun processDelete(item: T) {}

    protected open fun processInsert(item: T) {}

    protected open fun processUpdate(item: T) {}

    protected open fun afterSelectInit() {}

    protected fun callBackSelectData(item: T) {

        synchronized(dataList) { dataList.add(item) }

        processInsert(item)
    }

    fun addListener(listener : StoreListener<G>) {
        listenerList.add(listener)

        //listener.refreshAll(elemRoot(), INIT)
    }

    protected fun sentRefreshAllListener(refreshType: EditType) {

        EventQueue.invokeLater {
            listenerList.forEach { it.refreshAll(elemRoot(), refreshType) }
        }
    }

    open fun initData() {
        dataList.removeAll(dataList)

        // orm.select(clazz, ::callBackSelectData)
        selectDefault()

        afterSelectInit()

        sentRefreshAllListener(INIT)
    }

    private fun selectDefault() {

        val query = if(QuerySelect::class.java.isAssignableFrom(this::class.java))
            (this as QuerySelect).selectQuery() else orm.getSelect(clazz)

        val params = if(ParamsSelect::class.java.isAssignableFrom(this::class.java))
            (this as ParamsSelect).selectParams() else orm.selectParams(clazz)

        orm.select(query, params, clazz, ::callBackSelectData)
    }

    @Throws(SessionException::class)
    open fun delete(item: T, sessionSetting: SessionSetting = SessionSetting(false)) {

        dataList.remove(item)

        orm.deleteById(item, sessionSetting)

        processDelete(item)

        processStartLongTransactState(DELETE)
    }

    fun reCalcItemById(idParam: Any, item: T, sessionSetting: SessionSetting = SessionSetting(false)) {

        orm.reCalcValue(idParam, item, sessionSetting)
    }

    @Throws(SessionException::class)
    open fun save(item: T, sessionSetting: SessionSetting = SessionSetting(false)): T {

        val type = orm.save(item, sessionSetting)

        when (type) {
            INSERT -> {
                dataList.add(item)

                processInsert(item)
            }
            EDIT -> {
                processUpdate(item)
            }
            else -> throw SessionException("EditType is not valid $type")
        }

        processStartLongTransactState(type)

        return item
    }

    private fun processStartLongTransactState(type: EditType) {
        if(startedLongTransaction != LongTransactState.NONE_LONG_TRANSACT) {
            startedLongTransaction = LongTransactState.LONG_TRANSACT_MUST_REFRESH
        } else {
            sentRefreshAllListener(type)
        }
    }

    private fun processEndLongTransactState() {

        if(startedLongTransaction == LongTransactState.LONG_TRANSACT_MUST_REFRESH) {
            startedLongTransaction = LongTransactState.NONE_LONG_TRANSACT
            sentRefreshAllListener(ALL)
        }
        startedLongTransaction = LongTransactState.NONE_LONG_TRANSACT
    }
}

private enum class LongTransactState {
    NONE_LONG_TRANSACT,
    LONG_TRANSACT_STARTED,
    LONG_TRANSACT_MUST_REFRESH
}

