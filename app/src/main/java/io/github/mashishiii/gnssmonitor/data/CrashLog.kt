package io.github.mashishiii.gnssmonitor.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 未処理の例外をアプリ内のファイルに残し、次回起動時に画面へ表示する。
 * adb が使えない実機テストでも原因を共有できるようにするためのもの。
 * 保存先はアプリ専用領域（filesDir）で、端末の外には送らない・リポジトリにも入らない。
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 6000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file(appContext).writeText("thread=${thread.name}\n$sw".take(MAX_CHARS))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}
