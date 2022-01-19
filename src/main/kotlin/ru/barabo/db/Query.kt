package ru.barabo.db

import oracle.jdbc.OracleCallableStatement
import oracle.jdbc.OracleTypes
import org.slf4j.LoggerFactory
import java.io.*
import java.sql.*
import java.util.concurrent.atomic.AtomicLong

open class Query (protected val dbConnection :DbConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(Query::class.simpleName)!!

        private const val ERROR_STATEMENT_NULL = "statement is null"

        private const val ERROR_RESULTSET_NULL = "ResultSet is null"
    }

    private var uniqueSession : AtomicLong = AtomicLong(1L)

    fun uniqueRollBackOnlySession(): SessionSetting =
        SessionSetting(false,  TransactType.ROLLBACK, uniqueSession.incrementAndGet())

    fun uniqueSession() :SessionSetting =
            SessionSetting(false,  TransactType.NO_ACTION, uniqueSession.incrementAndGet())

    inline fun <reified T> selectValueType(query :String, params :Array<Any?>? = null): T? = selectValue(query, params) as? T

    fun selectValue(query :String, params :Array<Any?>? = null):Any? = selectValue(query, params, SessionSetting(false))

    fun selectValue(query :String, params :Array<Any?>? = null, sessionSetting: SessionSetting) :Any? {

       val list = select(query, params, sessionSetting)

       return if(list.isEmpty() || list[0].isEmpty()) null else list[0][0]
    }

    fun select(query :String, params :Array<Any?>? ) :List<Array<Any?>> = select(query, params, SessionSetting(false))

    @Throws(SessionException::class)
    fun select(query :String, params :Array<Any?>? = null, sessionSetting : SessionSetting = SessionSetting(false) ) :List<Array<Any?>> {

        if(query.indexOf("od.PTKB_VERSION_JAR j") < 0) {
            logger.info("select=$query")
        }

        params?.forEach { logger.info(it?.toString()) }

        val (session, statement, resultSet) = prepareSelect(query, params, sessionSetting)

        val tableData = try {
            fetchData(resultSet)
        }catch (e : SQLException) {
            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("fetch", e)
            closeQueryData(session, TransactType.ROLLBACK, statement, resultSet)
            throw SessionException(e.message as String)
        }

        closeQueryData(session, sessionSetting.transactType, statement, resultSet)

        return tableData
    }

    fun selectWithMetaData(query :String, params :Array<Any?>? = null,
                           sessionSetting : SessionSetting = SessionSetting(true) ): WithMetaData {

       // logger.info("select=$query")
       // params?.forEach { logger.info("param=$it") }

        val (session, statement, resultSet) = prepareSelect(query, params, sessionSetting)

        val metaData = try {
            fetchWithMetaData(resultSet)
        } catch (e : Exception) {
            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("fetch", e)
            closeQueryData(session, TransactType.ROLLBACK, statement, resultSet)
            throw SessionException(e.message as String)
        }

        closeQueryData(session, sessionSetting.transactType, statement, resultSet)

        return metaData
    }

    @Throws(SessionException::class)
    fun selectBlobToFile(query :String, params :Array<Any?>?, file: File): File {

        val sessionSetting = SessionSetting(false)

        val (session, statement, resultSet) = prepareSelect(query, params, sessionSetting)

        val outFile = try {
            fetchDataToFile(resultSet, file)
        } catch (e : SQLException) {
            closeQueryData(session, TransactType.ROLLBACK, statement, resultSet)
            throw SessionException(e.message as String)
        }

        closeQueryData(session, sessionSetting.transactType, statement, resultSet)

        return outFile
    }

    @Throws(SessionException::class)
    fun selectCursor(query :String, params :Array<Any?>?) :List<Array<Any?>> = selectCursor(query, params, SessionSetting(false))

    @Throws(SessionException::class)
    fun selectCursor(query :String, params :Array<Any?>? = null,
                     sessionSetting : SessionSetting = SessionSetting(false)):List<Array<Any?>> {


        val session = dbConnection.getSession(sessionSetting)

        val request = prepareSelectCursor(session, query, params, sessionSetting)

        val tableData = try {
            fetchData(request.resultSetCursor!!)
        }catch (e : SQLException) {

            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("fetch", e)
            closeQueryData(session, TransactType.ROLLBACK, request.statement, request.resultSetCursor)

            throw SessionException(e.message?:"")
        }

        closeQueryData(session, sessionSetting.transactType, request.statement, request.resultSetCursor)

        return tableData
    }

    fun selectCursorWithMetaData(query :String, params :Array<Any?>? = null,
                                 sessionSetting : SessionSetting = SessionSetting(true)): WithMetaData {
        val session = dbConnection.getSession(sessionSetting)

        val request = prepareSelectCursor(session, query, params, sessionSetting)

        val metaData = try {

            fetchWithMetaData(request.resultSetCursor!!)
        } catch (e : Exception) {

            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("fetch", e)

            if(dbConnection.isRestartSessionException(session, sessionSetting.isReadTransact, e.message?:"")) {
                return selectCursorWithMetaData(query, params, sessionSetting)
            }

            closeQueryData(session, TransactType.ROLLBACK, request.statement, request.resultSetCursor)

            throw SessionException(e.message?:"")
        }

        closeQueryData(session, sessionSetting.transactType, request.statement, request.resultSetCursor)

        return metaData
    }

    private fun withMetaData(data: List<Array<Any?>>, resultSet: ResultSet): WithMetaData {

        val columns = ArrayList<String>()

        val types = ArrayList<Int>()

        if(resultSet.metaData == null) return WithMetaData(data, columns, types)

        for (index in 1 .. resultSet.metaData.columnCount) {
            columns += resultSet.metaData.getColumnName(index)?.uppercase()!!

            types += resultSet.metaData.getColumnType(index)
        }

        return WithMetaData(data, columns, types)
    }

    @Throws(SessionException::class)
    fun selectCursor(query :String, params :Array<Any?>? = null,
                     sessionSetting : SessionSetting = SessionSetting(false),
                     callBack :(isNewRow :Boolean, value :Any?, column :String?)->Unit) {

        logger.info("select=$query")
        params?.forEach { logger.info(it?.toString()) }

        val session = dbConnection.getSession(sessionSetting)

        val request = prepareSelectCursor(session, query, params, sessionSetting)

        try {
            fetchData(request.resultSetCursor!!, callBack)
        }catch (e : SQLException) {
            logger.error("fetch", e)
            closeQueryData(session, TransactType.ROLLBACK, request.statement, request.resultSetCursor)
            throw SessionException(e.message as String)
        }

        closeQueryData(session, sessionSetting.transactType, request.statement, request.resultSetCursor)
    }

    @Throws(SessionException::class)
    fun select(query :String, params :Array<Any?>? = null,
               sessionSetting : SessionSetting = SessionSetting(false),
               callBack :(isNewRow :Boolean, value :Any?, column :String?)->Unit) {

       logger.info("select=$query")

        params?.forEach { logger.info(it?.toString()) }

        val (session, statement, resultSet) = prepareSelect(query, params, sessionSetting)

        try {
            fetchData(resultSet, callBack)
        }catch (e : SQLException) {
            logger.error("fetch", e)
            closeQueryData(session, TransactType.ROLLBACK, statement, resultSet)
            throw SessionException(e.message as String)
        }

        closeQueryData(session, sessionSetting.transactType, statement, resultSet)
    }

    fun commitFree(sessionSetting : SessionSetting) {

        val session = dbConnection.getSession(sessionSetting)

        closeQueryData(session, TransactType.COMMIT)
    }

    fun rollbackFree(sessionSetting : SessionSetting) {

        val session = dbConnection.getSession(sessionSetting)

        closeQueryData(session, TransactType.ROLLBACK)
    }

    private fun prepareExecute(session :Session, query :String, params :Array<Any?>?,
                               outParamTypes :IntArray?): QueryRequest {

        return try {
            if((outParamTypes?.size ?: 0) == 0) {
                val prepareStatement = session.session.prepareStatement(query)

                val fileStream = prepareStatement?.setParams(params)

                QueryRequest(query, params, prepareStatement, fileInputStream = fileStream)
            } else {
                QueryRequest(query, params, session.session.prepareCall(query)?.setParams(outParamTypes as IntArray, params))
            }
        } catch (e : SQLException) {
            logger.error("QUERY=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("outParamTypes.size=${outParamTypes?.size}")
            outParamTypes?.forEach { logger.error(it.toString()) }

            logger.error("prepareCall", e)

            if(dbConnection.isRestartSessionException(session, false, e.message?:"")) {
                return prepareExecute(session, query, params, outParamTypes)
            }

            closeQueryData(session, TransactType.ROLLBACK)
            throw SessionException(e.message as String)
        }
    }

    private fun executePrepared(session :Session, queryRequest: QueryRequest, outParamTypes :IntArray?) :List<Any?>? {

        val result = if((outParamTypes?.size ?: 0) == 0) null else ArrayList<Any?>()

        val statement = queryRequest.statement

        try {
            statement?.execute()

            if(statement is CallableStatement) {
                for(index in outParamTypes!!.indices) {
                    result?.add(statement.getObject(index + 1))
                }
            }
        } catch (e : SQLException) {
            logger.error("query=${queryRequest.query}")
            logger.error("outParamTypes.size=${outParamTypes?.size}")
            outParamTypes?.forEach { logger.error(it.toString()) }

            logger.error("execute Call", e)

            if(dbConnection.isRestartSessionException(session, false, e.message?:"")) {

                try { statement?.close() } catch (e :SQLException) {}

                val newQueryRequest =
                        prepareExecute(session, queryRequest.query, queryRequest.params, outParamTypes)
                queryRequest.statement = newQueryRequest.statement

                return executePrepared(session, queryRequest, outParamTypes)
            }

            closeQueryData(session, TransactType.ROLLBACK, statement, fileInputStream = queryRequest.fileInputStream)
            throw SessionException(e.message as String)
        }

        return result
    }

    @Throws(SessionException::class)
    fun execute(query :String, params :Array<Any?>?, outParamTypes :IntArray): List<Any?>? =
        execute(query, params, SessionSetting(false), outParamTypes)

    @Throws(SessionException::class)
    fun execute(query :String, params :Array<Any?>?): List<Any?>? =
        execute(query, params, SessionSetting(false), null)

    @Throws(SessionException::class)
    fun executeOut(query: String, params: Array<Any?>?, outParamTypes: IntArray): List<Any?>? =
        execute(query, params, SessionSetting(false), outParamTypes)

    @Throws(SessionException::class)
    fun execute(query :String, params :Array<Any?>? = null,
                       sessionSetting : SessionSetting,
                       outParamTypes :IntArray? = null): List<Any?>? {

        logger.error("!!!!!!!!!!!!!!!!!$query")

        params?.forEach { logger.error(it.toString()) }

        val session = dbConnection.getSession(sessionSetting)

        val queryRequest = prepareExecute(session, query, params, outParamTypes)

        val resultList = executePrepared(session, queryRequest, outParamTypes)

        closeQueryData(session, sessionSetting.transactType, queryRequest.statement, fileInputStream = queryRequest.fileInputStream)

        return resultList
    }

    @Throws(SessionException::class)
    private fun prepareSelectCursor(session :Session, query :String, params :Array<Any?>?, sessionSetting : SessionSetting): QueryRequest {

        logger.info("query=$query")

        params?.forEach { logger.debug(it?.toString()) }

        val statement = try {
            session.session.prepareCall(query)
                    ?.setParams(intArrayOf(OracleTypes.CURSOR), params) as OracleCallableStatement

        } catch (e: SQLException) {

            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }

            logger.error("prepareSelectCursor", e)

            closeQueryData(session, TransactType.ROLLBACK)
            throw SessionException(e.message?:"")
        }

        val resultSet = try {

            statement.execute()

            statement.getCursor(1)

        } catch (e: SQLException) {
            logger.error("executeCursor", e)

            if(dbConnection.isRestartSessionException(session, sessionSetting.isReadTransact, e.message?:"")) {
                return prepareSelectCursor(session, query, params, sessionSetting)
            }
            closeQueryData(session, TransactType.ROLLBACK, statement)
            throw SessionException(e.message as String)
        }

        return QueryRequest(query, params, statement, resultSet)
    }

    @Throws(SessionException::class)
    private fun prepareSelect(query :String, params :Array<Any?>?, sessionSetting : SessionSetting)
            : Triple<Session, Statement, ResultSet>{
        val session = dbConnection.getSession(sessionSetting)

        val statement = try {
            session.session.prepareStatement(query).apply {
                setParams(params)
            } ?: throw SessionException(ERROR_STATEMENT_NULL)
        } catch (e : SQLException) {
            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("prepareStatement", e)

            if(dbConnection.isRestartSessionException(session, sessionSetting.isReadTransact, e.message?:"")) {
                return prepareSelect(query, params, sessionSetting)
            }

            closeQueryData(session, TransactType.ROLLBACK)
            throw SessionException(e.message?:"")
        }

        val resultSet = try {
            statement.executeQuery() ?: throw SessionException(ERROR_RESULTSET_NULL)
        } catch (e : SQLException) {
            logger.error("query=$query")
            params?.forEach { logger.error(it?.toString()) }
            logger.error("executeQuery", e)


            if(dbConnection.isRestartSessionException(session, sessionSetting.isReadTransact, e.message?:"")) {
                return prepareSelect(query, params, sessionSetting)
            }

            closeQueryData(session, TransactType.ROLLBACK, statement)
            throw SessionException(e.message as String)
        }

        return Triple(session, statement, resultSet)
    }

    @Throws(SessionException::class)
    private fun fetchData(resultSet : ResultSet, callBack :(isNewRow :Boolean, value :Any?, column :String?)->Unit) {

        val columns = Array(resultSet.metaData.columnCount) {""}

        for (index in 1 .. resultSet.metaData.columnCount) {
            columns[index - 1] = resultSet.metaData.getColumnName(index)?.uppercase()!!
        }

        while(resultSet.next()) {
            callBack(true, null, null)

            for (index in 1 .. columns.size) {
                callBack(false, resultSet.getObject(index), columns[index - 1])
            }
        }
    }

    @Throws(SessionException::class)
    private fun fetchData(resultSet : ResultSet) :List<Array<Any?>> {

        val data = ArrayList<Array<Any?>>()

        while(resultSet.next()) {

            val row = Array<Any?>(resultSet.metaData.columnCount) {null}

            for (index in 1 .. resultSet.metaData.columnCount) {
                row[index - 1] = resultSet.getObject(index)
            }
            data.add(row)
        }

        return data
    }

    @Throws(SessionException::class)
    private fun fetchWithMetaData(resultSet : ResultSet): WithMetaData {

        val data = ArrayList<Array<Any?>>()

        while(resultSet.next()) {

            val row = Array<Any?>(resultSet.metaData.columnCount) {null}

            for (index in 1 .. resultSet.metaData.columnCount) {
                row[index - 1] = resultSet.getObject(index)
            }
            data.add(row)
        }

        return withMetaData(data, resultSet)
    }

    @Throws(SessionException::class)
    private fun fetchDataToFile(resultSet: ResultSet, file: File): File {

        BufferedOutputStream(FileOutputStream(file)).use { out ->
            while(resultSet.next()) {
                BufferedInputStream(resultSet.getBlob(1).binaryStream).use { input ->
                    input.copyTo(out)
                }
             }
        }
        return file
    }


    private fun closeQueryData(session: Session,
                               transactType: TransactType = TransactType.ROLLBACK,
                               statement: Statement? = null,
                               resultSet: ResultSet? = null,
                               fileInputStream: InputStream? = null) {

        try {
            try { fileInputStream?.close()  } catch (e: Exception) {}

            resultSet?.close()

            statement?.close()

            processCommit(session, transactType)

            session.isFree = true

            if(transactType == TransactType.ROLLBACK || transactType == TransactType.COMMIT) {
                session.idSession = null
            }
         } catch (e :SQLException) {
            logger.error("closeQueryData", e)
        }
    }

    @Throws(SQLException::class)
    private fun processCommit(session :Session, transactType :TransactType) {
        when (transactType) {
            TransactType.ROLLBACK -> {
                session.session.rollback()
            }
            TransactType.COMMIT -> {
                session.session.commit()
            }
            else -> {
            }
        }
    }
}

@Throws(SQLException::class)
fun PreparedStatement.setParams(inParams :Array<Any?>? = null, shiftOutParams: Int = 0): InputStream? {

    if(inParams == null) {
        return null
    }

    var fileInputStream: InputStream? = null

    for (index in inParams.indices) {

        when(inParams[index]) {
            is Class<*> -> setNull(index + 1 + shiftOutParams, Type.getSqlTypeByClass(inParams[index] as Class<*>))
            is File -> {
                fileInputStream = FileInputStream(inParams[index] as File)
                setBinaryStream(index + 1 + shiftOutParams, fileInputStream)
            }
            else -> setObject(index + 1 + shiftOutParams, inParams[index])
        }
    }

    return fileInputStream
}

@Throws(SQLException::class)
fun CallableStatement.setParams(outParamTypes :IntArray, inParams :Array<Any?>? = null): CallableStatement {

    for(index in outParamTypes.indices) {
        this.registerOutParameter(index + 1, outParamTypes[index])
    }

    setParams(inParams, outParamTypes.size)

    return this// setParams(inParams, outParamTypes.size) as CallableStatement
}

private class QueryRequest(val query :String,
                        val params :Array<Any?>?,
                        var statement :PreparedStatement?,
                        var resultSetCursor :ResultSet? = null,
                        val fileInputStream :InputStream? = null)

data class WithMetaData(val data: List<Array<Any?>>, val columns: List<String>, val types: List<Int>)


