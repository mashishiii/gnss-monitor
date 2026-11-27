package io.github.mashishiii.gnssmonitor.data

import java.io.ByteArrayInputStream
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class TrackFormatsTest {
    // 2026-11-27T01:59:08.250Z
    private val t0 = 1795744748250L

    private val points = listOf(
        LogPoint(t0, 35.6812360, 139.7671250, 42.3, 4.2f, 1.25f, 9, 14),
        LogPoint(t0 + 1000, 35.6813, 139.7672, null, null, null, 3, 6),
        LogPoint(t0 + 2000, -33.85, -151.2, -2.0, 12.5f, 0f, 0, 2),
    )

    @Test
    fun csvHasHeaderAndRows() {
        val sb = StringBuilder()
        TrackFormats.writeCsv(points, sb)
        val lines = sb.toString().split("\r\n").filter { it.isNotEmpty() }
        assertEquals(TrackFormats.CSV_HEADER, lines[0])
        assertEquals(4, lines.size)
        // 先頭 8 列は v0.3 から同じ。v0.5 の 8 列が後ろに付く（値が無い項目は空欄）
        assertEquals("2026-11-27T01:59:08.250Z,35.6812360,139.7671250,42.3,4.2,1.25,9,14,,,,,,,,", lines[1])
        assertEquals("2026-11-27T01:59:09.250Z,35.6813000,139.7672000,,,,3,6,,,,,,,,", lines[2])
        assertEquals("2026-11-27T01:59:10.250Z,-33.8500000,-151.2000000,-2.0,12.5,0.00,0,2,,,,,,,,", lines[3])
    }

    @Test
    fun csvRoundTrips() {
        val sb = StringBuilder()
        TrackFormats.writeCsv(points, sb)
        val back = TrackFormats.parseCsv(sb.toString())
        assertEquals(points.size, back.size)
        assertEquals(points[0].timeMillis, back[0].timeMillis)
        assertEquals(points[0].latitude, back[0].latitude, 1e-7)
        assertEquals(points[0].altitudeMeters!!, back[0].altitudeMeters!!, 0.05)
        assertEquals(null, back[1].altitudeMeters)
        assertEquals(null, back[1].speedMps)
        assertEquals(14, back[0].satellitesVisible)
    }

    @Test
    fun csvParserSkipsBrokenLines() {
        val text = TrackFormats.CSV_HEADER + "\nbroken\n2026-11-27T01:59:08.250Z,x,1,,,,1,1\n" +
            TrackFormats.csvLine(points[0]) + "\n"
        assertEquals(1, TrackFormats.parseCsv(text).size)
    }

    private fun parseGpx(xml: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun gpxIsWellFormedGpx11WithPoints() {
        val sb = StringBuilder()
        TrackFormats.writeGpx(points, sb, "テスト <記録> & \"走行\"")
        val doc = parseGpx(sb.toString())
        val root = doc.documentElement
        assertEquals("gpx", root.localName)
        assertEquals("http://www.topografix.com/GPX/1/1", root.namespaceURI)
        assertEquals("1.1", root.getAttribute("version"))
        val ns = "http://www.topografix.com/GPX/1/1"
        val trkpts = doc.getElementsByTagNameNS(ns, "trkpt")
        assertEquals(3, trkpts.length)
        val first = trkpts.item(0) as Element
        assertEquals("35.6812360", first.getAttribute("lat"))
        assertEquals("139.7671250", first.getAttribute("lon"))
        assertEquals("42.3", first.getElementsByTagNameNS(ns, "ele").item(0).textContent)
        assertEquals("2026-11-27T01:59:08.250Z", first.getElementsByTagNameNS(ns, "time").item(0).textContent)
        assertEquals("3d", first.getElementsByTagNameNS(ns, "fix").item(0).textContent)
        assertEquals("9", first.getElementsByTagNameNS(ns, "sat").item(0).textContent)
        assertEquals("1.25", first.getElementsByTagNameNS(TrackFormats.GPX_EXT_NS, "speed").item(0).textContent)
        assertEquals("14", first.getElementsByTagNameNS(TrackFormats.GPX_EXT_NS, "satsVisible").item(0).textContent)
        // 名前のエスケープ
        assertEquals("テスト <記録> & \"走行\"", doc.getElementsByTagNameNS(ns, "name").item(0).textContent)
        // 高度が無い点には ele を出さない、2D の点は fix=2d
        val second = trkpts.item(1) as Element
        assertEquals(0, second.getElementsByTagNameNS(ns, "ele").length)
        assertEquals("2d", second.getElementsByTagNameNS(ns, "fix").item(0).textContent)
        val third = trkpts.item(2) as Element
        assertEquals(0, third.getElementsByTagNameNS(ns, "fix").length)
    }

    @Test
    fun gpxElementOrderFollowsSchema() {
        val sb = StringBuilder()
        TrackFormats.writeGpx(points.take(1), sb, "x")
        val s = sb.toString()
        val order = listOf("<ele>", "<time>", "<fix>", "<sat>", "<extensions>").map { s.indexOf(it, s.indexOf("<trkpt")) }
        assertTrue(order.all { it > 0 })
        assertEquals(order.sorted(), order)
    }

    @Test
    fun emptyTrackStillProducesValidFiles() {
        val gpx = StringBuilder().also { TrackFormats.writeGpx(emptyList(), it, "empty") }.toString()
        assertEquals(0, parseGpx(gpx).getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").length)
        val csv = StringBuilder().also { TrackFormats.writeCsv(emptyList(), it) }.toString()
        assertEquals(TrackFormats.CSV_HEADER + "\r\n", csv)
        assertFalse(TrackFormats.parseCsv(csv).any())
    }

    @Test
    fun defaultFileNameUsesLocalTime() {
        assertEquals("gnss-track-20261127-1059", TrackFormats.defaultFileBase(t0, TimeZone.getTimeZone("Asia/Tokyo")))
    }

    private val v05 = LogPoint(
        t0, 35.6812360, 139.7671250, 42.3, 4.2f, 1.25f, 9, 14,
        verticalAccuracyMeters = 6.5f,
        speedAccuracyMps = 0.35f,
        avgCn0DbHz = 31.4f,
        dualFreq = true,
        dualFreqSats = 3,
        satsL1 = 8,
        satsL5 = 5,
        satsOtherBands = mapOf(BandFamily.L2 to 2, BandFamily.E5B to 1),
    )

    @Test
    fun csvHeaderHasEightNewColumnsAfterTheOriginalEight() {
        val cols = TrackFormats.CSV_HEADER.split(",")
        assertEquals(16, cols.size)
        assertEquals(
            listOf("time_utc", "latitude", "longitude", "altitude_m", "accuracy_m", "speed_mps", "satellites_used", "satellites_visible"),
            cols.take(8),
        )
        assertEquals(
            listOf("vertical_accuracy_m", "speed_accuracy_mps", "avg_cn0_dbhz", "dual_freq", "dual_freq_sats", "sats_l1", "sats_l5", "sats_other_bands"),
            cols.drop(8),
        )
    }

    @Test
    fun csvWritesAndReadsBackNewColumns() {
        val sb = StringBuilder()
        TrackFormats.writeCsv(listOf(v05), sb)
        val line = sb.toString().split("\r\n")[1]
        assertEquals("2026-11-27T01:59:08.250Z,35.6812360,139.7671250,42.3,4.2,1.25,9,14,6.5,0.35,31.4,1,3,8,5,E5b:1;L2:2", line)
        val back = TrackFormats.parseCsv(sb.toString()).single()
        assertEquals(6.5f, back.verticalAccuracyMeters!!, 0.01f)
        assertEquals(0.35f, back.speedAccuracyMps!!, 0.001f)
        assertEquals(31.4f, back.avgCn0DbHz!!, 0.01f)
        assertEquals(true, back.dualFreq)
        assertEquals(3, back.dualFreqSats)
        assertEquals(8, back.satsL1)
        assertEquals(5, back.satsL5)
        assertEquals(mapOf(BandFamily.L2 to 2, BandFamily.E5B to 1), back.satsOtherBands)
    }

    @Test
    fun csvWithoutFrequencyInfoLeavesDualFreqBlank() {
        val p = v05.copy(dualFreq = null, dualFreqSats = null, satsL1 = null, satsL5 = null, satsOtherBands = emptyMap())
        val back = TrackFormats.parseCsv(StringBuilder().also { TrackFormats.writeCsv(listOf(p), it) }.toString()).single()
        assertEquals(null, back.dualFreq)
        assertEquals(null, back.satsL5)
        assertEquals(6.5f, back.verticalAccuracyMeters!!, 0.01f)
    }

    @Test
    fun oldEightColumnCsvStillReads() {
        val old = "time_utc,latitude,longitude,altitude_m,accuracy_m,speed_mps,satellites_used,satellites_visible\r\n" +
            "2026-11-27T01:59:08.250Z,35.6812360,139.7671250,42.3,4.2,1.25,9,14\r\n"
        val p = TrackFormats.parseCsv(old).single()
        assertEquals(35.6812360, p.latitude, 1e-7)
        assertEquals(9, p.satellitesUsed)
        assertEquals(null, p.verticalAccuracyMeters)
        assertEquals(null, p.dualFreq)
        assertTrue(p.satsOtherBands.isEmpty())
    }

    @Test
    fun gpxCarriesNewExtensions() {
        val sb = StringBuilder()
        TrackFormats.writeGpx(listOf(v05), sb, "x")
        val doc = parseGpx(sb.toString())
        fun ext(name: String) = doc.getElementsByTagNameNS(TrackFormats.GPX_EXT_NS, name).item(0)?.textContent
        assertEquals("6.5", ext("verticalAccuracy"))
        assertEquals("0.35", ext("speedAccuracy"))
        assertEquals("31.4", ext("avgCn0"))
        assertEquals("1", ext("dualFreq"))
        assertEquals("3", ext("dualFreqSats"))
        assertEquals("8", ext("satsL1"))
        assertEquals("5", ext("satsL5"))
        assertEquals("E5b:1;L2:2", ext("satsOtherBands"))
        // 値が無い点には出さない
        val none = StringBuilder().also { TrackFormats.writeGpx(listOf(points[0]), it, "x") }.toString()
        assertFalse(none.contains("verticalAccuracy"))
        assertFalse(none.contains("dualFreq"))
    }

    @Test
    fun logPointFromStateFillsV05Fields() {
        fun s(c: Int, svid: Int, hz: Float, used: Boolean, cn0: Float) = SatelliteInfo(c, svid, cn0, 0f, 45f, used, hz)
        val state = GnssUiState(
            position = PositionInfo(35.0, 139.0, 3f, 10.0, t0, verticalAccuracyMeters = 5f, speedAccuracyMps = 0.2f),
            satellites = listOf(
                s(1, 3, 1575.42e6f, true, 40f), s(1, 3, 1176.45e6f, true, 30f),
                s(1, 4, 1575.42e6f, true, 36f), s(1, 4, 1176.45e6f, true, 32f),
                s(1, 9, 1575.42e6f, true, 28f), s(1, 9, 1227.6e6f, true, 25f),
            ),
        )
        val p = logPointFrom(state)!!
        assertEquals(5f, p.verticalAccuracyMeters!!, 0f)
        assertEquals(true, p.dualFreq)
        assertEquals(2, p.dualFreqSats)
        assertEquals(3, p.satsL1)
        assertEquals(2, p.satsL5)
        assertEquals(mapOf(BandFamily.L2 to 1), p.satsOtherBands)
        // 平均 C/N0 は衛星ごとに強い方（40, 36, 28）
        assertEquals((40f + 36f + 28f) / 3f, p.avgCn0DbHz!!, 0.01f)
    }
}
