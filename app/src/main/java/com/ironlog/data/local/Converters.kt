package com.ironlog.data.local

import androidx.room.TypeConverter

/** Lists are stored as a single TEXT column using an ASCII unit separator. */
object Converters {
    private const val SEP = "\u001F"

    fun join(list: List<String>): String = list.joinToString(SEP)

    fun split(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEP)

    @TypeConverter
    @JvmStatic
    fun fromList(list: List<String>): String = join(list)

    @TypeConverter
    @JvmStatic
    fun toList(value: String): List<String> = split(value)
}
