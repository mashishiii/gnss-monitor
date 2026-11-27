package io.github.mashishiii.gnssmonitor.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CSV / GPX の読み書き。どちらも時刻は UTC の ISO 8601。 */
object TrackFormats {

    /** 先頭の 8 列は v0.3 から変わらない。v0.5 で後ろに 8 列足した（古い 8 列だけのファイルも読める）。 */
    const val CSV_HEADER =
        "time_utc,latitude,longitude,altitude_m,accuracy_m,speed_mps,satellites_used,satellites_visible," +
            "vertical_accuracy_m,speed_accuracy_mps,avg_cn0_dbhz,dual_freq,dual_freq_sats,sats_l1,sats_l5,sats_other_bands"

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
        p.verticalAccuracyMeters.fmt(1),
        p.speedAccuracyMps.fmt(2),
        p.avgCn0DbHz.fmt(1),
        p.dualFreq?.let { if (it) "1" else "0" } ?: "",
        p.dualFreqSats?.toString() ?: "",
        p.satsL1?.toString() ?: "",
        p.satsL5?.toString() ?: "",
        otherBandsText(p.satsOtherBands),
    ).joinToString(",")

    /** 例: "L2:3;E5b:2"（カンマを含めない）。 */
    fun otherBandsText(map: Map<BandFamily, Int>): String =
        map.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.label}:${it.value}" }

    fun parseOtherBands(text: String): Map<BandFamily, Int> =
        text.split(";").mapNotNull { part ->
            val (name, n) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val family = BandFamily.entries.firstOrNull { it.label == name && it != BandFamily.UNKNOWN } ?: return@mapNotNull null
            val count = n.toIntOrNull() ?: return@mapNotNull null
            family to count
        }.toMap()

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
                    // v0.5 以降の列（古いファイルには無いので null / 既定値）
                    verticalAccuracyMeters = c.getOrNull(8)?.toFloatOrNull(),
                    speedAccuracyMps = c.getOrNull(9)?.toFloatOrNull(),
                    avgCn0DbHz = c.getOrNull(10)?.toFloatOrNull(),
                    dualFreq = when (c.getOrNull(11)) { "1" -> true; "0" -> false; else -> null },
                    dualFreqSats = c.getOrNull(12)?.toIntOrNull(),
                    satsL1 = c.getOrNull(13)?.toIntOrNull(),
                    satsL5 = c.getOrNull(14)?.toIntOrNull(),
                    satsOtherBands = c.getOrNull(15)?.let { parseOtherBands(it) } ?: emptyMap(),
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

    /** GPX 1.1。速度・精度・捕捉衛星数・v0.5 の追加項目は拡張要素（gm:）に入れる。 */
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
            p.verticalAccuracyMeters.fmt(1).takeIf { it.isNotEmpty() }?.let { out.append("<gm:verticalAccuracy>").append(it).append("</gm:verticalAccuracy>") }
            p.speedAccuracyMps.fmt(2).takeIf { it.isNotEmpty() }?.let { out.append("<gm:speedAccuracy>").append(it).append("</gm:speedAccuracy>") }
            p.avgCn0DbHz.fmt(1).takeIf { it.isNotEmpty() }?.let { out.append("<gm:avgCn0>").append(it).append("</gm:avgCn0>") }
            p.dualFreq?.let { out.append("<gm:dualFreq>").append(if (it) "1" else "0").append("</gm:dualFreq>") }
            p.dualFreqSats?.let { out.append("<gm:dualFreqSats>").append(it.toString()).append("</gm:dualFreqSats>") }
            p.satsL1?.let { out.append("<gm:satsL1>").append(it.toString()).append("</gm:satsL1>") }
            p.satsL5?.let { out.append("<gm:satsL5>").append(it.toString()).append("</gm:satsL5>") }
            if (p.satsOtherBands.isNotEmpty()) {
                out.append("<gm:satsOtherBands>").append(xml(otherBandsText(p.satsOtherBands))).append("</gm:satsOtherBands>")
            }
            out.append("<gm:satsVisible>").append(p.satellitesVisible.toString()).append("</gm:satsVisible>")
            out.append("</extensions></trkpt>\n")
        }
        out.append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    /** 書き出しファイル名の既定値: gnss-track-20261127-1059 */
    fun defaultFileBase(startMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        "gnss-track-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply { this.timeZone = timeZone }.format(Date(startMillis))
}
