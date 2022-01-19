package ru.barabo.db

data class DbSetting(val driver :String,
                     var url :String,
                     var user :String,
                     var password :String,
                     val selectCheck :String = "select 1 from dual")