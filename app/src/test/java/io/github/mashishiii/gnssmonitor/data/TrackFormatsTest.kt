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
        assertEquals("2026-11-27T01:59:08.250Z,35.6812360,139.7671250,42.3,4.2,1.25,9,14", lines[1])
        // 値が無い項目は空欄
        assertEquals("2026-11-27T01:59:09.250Z,35.6813000,139.7672000,,,,3,6", lines[2])
        assertEquals("2026-11-27T01:59:10.250Z,-33.8500000,-151.2000000,-2.0,12.5,0.00,0,2", lines[3])
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
}
