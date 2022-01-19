package ru.barabo.db

import java.sql.Connection
import java.sql.SQLException

data class Session (var session : Connection,
                    var isFree :Boolean = true,
                    var idSession :Long? = null) {

    companion object {

        private const val ERROR_CHECK_SESSION = "session is death"
    }

    fun checkConnect(selectCheck :String) :Boolean {
        return try {
            val statement = session.prepareStatement(selectCheck)

            statement.executeQuery()?.close() ?: throw SQLException(ERROR_CHECK_SESSION)
            statement.close()

            true
        } catch (e : SQLException) {
            false
        }
    }

    fun execute(query :String) {
        val statement = session.prepareStatement(query)

        statement.executeUpdate()

        statement.close()

        session.commit()
    }
}