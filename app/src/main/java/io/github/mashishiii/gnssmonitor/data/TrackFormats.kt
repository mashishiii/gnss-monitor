package io.github.mashishiii.gnssmonitor.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CSV / GPX の読み書き。どちらも時刻は UTC の ISO 8601。 */
object TrackFormats {

    const val CSV_HEADER =
        "time_utc,latitude,longitude,altitude_m,accuracy_m,speed_mps,satellites_used,satellites_visible"

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun isoTime(millis: Long): String = isoFormat().format(Date(millis))

    fun parseIsoTime(text: String): Long? = runCatching { isoFormat().parse(text)?.time }.getOrNull()

    private fun Double?.fmt(digits: Int) = this?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.${digits}f", it) } ?: ""
    private fun Float?.fmt(digits: Int) = this?.toDouble().fmt(digits)

    fun csvLine(p: LogPoint): String = listOf(
        isoTime(p.timeMillis),
        p.latitude.fmt(7),
        p.longitude.fmt(7),
        p.altitudeMeters.fmt(1),
        p.accuracyMeters.fmt(1),
        p.speedMps.fmt(2),
        p.satellitesUsed.toString(),
        p.satellitesVisible.toString(),
    ).joinToString(",")

    fun writeCsv(points: List<LogPoint>, out: Appendable) {
        out.append(CSV_HEADER).append("\r\n")
        for (p in points) out.append(csvLine(p)).append("\r\n")
    }

    /** [writeCsv] の出力を読み戻す。壊れた行は飛ばす。 */
    fun parseCsv(text: String): List<LogPoint> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("time_utc") }
            .mapNotNull { line ->
                val c = line.split(",")
                if (c.size < 8) return@mapNotNull null
                val time = parseIsoTime(c[0]) ?: return@mapNotNull null
                val lat = c[1].toDoubleOrNull() ?: return@mapNotNull null
                val lon = c[2].toDoubleOrNull() ?: return@mapNotNull null
                LogPoint(
                    timeMillis = time,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = c[3].toDoubleOrNull(),
                    accuracyMeters = c[4].toFloatOrNull(),
                    speedMps = c[5].toFloatOrNull(),
                    satellitesUsed = c[6].toIntOrNull() ?: 0,
                    satellitesVisible = c[7].toIntOrNull() ?: 0,
                )
            }
            .toList()

    private fun xml(text: String): String = buildString {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    const val GPX_EXT_NS = "https://github.com/mashishiii/gnss-monitor/gpx/1"

    /** GPX 1.1。速度・精度・捕捉衛星数は拡張要素（gm:）に入れる。 */
    fun writeGpx(points: List<LogPoint>, out: Appendable, name: String) {
        out.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        out.append(
            """<gpx version="1.1" creator="GNSS Monitor" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:gm="$GPX_EXT_NS" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
        ).append('\n')
        out.append("  <metadata><name>").append(xml(name)).append("</name>")
        points.firstOrNull()?.let { out.append("<time>").append(isoTime(it.timeMillis)).append("</time>") }
        out.append("</metadata>\n")
        out.append("  <trk>\n    <name>").append(xml(name)).append("</name>\n    <trkseg>\n")
        for (p in points) {
            out.append("      <trkpt lat=\"").append(p.latitude.fmt(7)).append("\" lon=\"").append(p.longitude.fmt(7)).append("\">")
            p.altitudeMeters.fmt(1).takeIf { it.isNotEmpty() }?.let { out.append("<ele>").append(it).append("</ele>") }
            out.append("<time>").append(isoTime(p.timeMillis)).append("</time>")
            val fix = when {
                p.satellitesUsed >= 4 -> "3d"
                p.satellitesUsed == 3 -> "2d"
                else -> null
            }
            fix?.let { out.append("<fix>").append(it).append("</fix>") }
            out.append("<sat>").append(p.satellitesUsed.toString()).append("</sat>")
            out.append("<extensions>")
            p.speedMps.fmt(2).takeIf { it.isNotEmpty() }?.let { out.append("<gm:speed>").append(it).append("</gm:speed>") }
            p.accuracyMeters.fmt(1).takeIf { it.isNotEmpty() }?.let { out.append("<gm:accuracy>").append(it).append("</gm:accuracy>") }
            out.append("<gm:satsVisible>").append(p.satellitesVisible.toString()).append("</gm:satsVisible>")
            out.append("</extensions></trkpt>\n")
        }
        out.append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    /** 書き出しファイル名の既定値: gnss-track-20261127-1059 */
    fun defaultFileBase(startMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        "gnss-track-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply { this.timeZone = timeZone }.format(Date(startMillis))
}
