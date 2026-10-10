package com.evan.pulse.data.monitor

import com.evan.pulse.R
import com.evan.pulse.data.model.MonitorTarget
import com.evan.pulse.data.model.MonitorType
import com.evan.pulse.data.model.addressText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorCheckerTest {

    private fun target(
        type: MonitorType,
        name: String = "api",
        host: String = "example.com",
        port: Int = 80,
        url: String = "https://example.com/ping",
        statusMin: Int = 200,
        statusMax: Int = 399
    ) = MonitorTarget(
        id = 1L,
        projectId = 1L,
        name = name,
        type = type,
        host = host,
        port = port,
        url = url,
        statusMin = statusMin,
        statusMax = statusMax
    )

    @Test
    fun isValidPortAcceptsStandardRange() {
        assertTrue(MonitorChecker.isValidPort(1))
        assertTrue(MonitorChecker.isValidPort(80))
        assertTrue(MonitorChecker.isValidPort(65535))
    }

    @Test
    fun isValidPortRejectsOutOfRangeValues() {
        assertFalse(MonitorChecker.isValidPort(0))
        assertFalse(MonitorChecker.isValidPort(-1))
        assertFalse(MonitorChecker.isValidPort(65536))
    }

    @Test
    fun statusPassesChecksClosedRange() {
        assertTrue(MonitorChecker.statusPasses(200, 200, 399))
        assertTrue(MonitorChecker.statusPasses(399, 200, 399))
        assertTrue(MonitorChecker.statusPasses(204, 200, 399))
        assertFalse(MonitorChecker.statusPasses(199, 200, 399))
        assertFalse(MonitorChecker.statusPasses(400, 200, 399))
        assertFalse(MonitorChecker.statusPasses(500, 200, 399))
    }

    @Test
    fun statusRangeValidAcceptsNormalRanges() {
        assertTrue(MonitorChecker.statusRangeValid(200, 399))
        assertTrue(MonitorChecker.statusRangeValid(100, 599))
        assertTrue(MonitorChecker.statusRangeValid(200, 200))
    }

    @Test
    fun statusRangeValidRejectsBrokenRanges() {
        assertFalse(MonitorChecker.statusRangeValid(399, 200))
        assertFalse(MonitorChecker.statusRangeValid(90, 399))
        assertFalse(MonitorChecker.statusRangeValid(200, 600))
        assertFalse(MonitorChecker.statusRangeValid(100, 1000))
        assertFalse(MonitorChecker.statusRangeValid(0, 100))
    }

    @Test
    fun validationRequiresName() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_name_required),
            MonitorChecker.validation(target(MonitorType.IP, name = ""))
        )
    }

    @Test
    fun validationRejectsIpWithoutHost() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_host_required),
            MonitorChecker.validation(target(MonitorType.IP, host = ""))
        )
    }

    @Test
    fun validationAcceptsIpWithHost() {
        assertEquals(TargetValidation.Valid, MonitorChecker.validation(target(MonitorType.IP)))
        assertEquals(
            TargetValidation.Valid,
            MonitorChecker.validation(target(MonitorType.IP, host = "10.0.0.5"))
        )
    }

    @Test
    fun validationRejectsPortWithoutHost() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_host_required),
            MonitorChecker.validation(target(MonitorType.PORT, host = ""))
        )
    }

    @Test
    fun validationRejectsPortOutsideRange() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_port_invalid),
            MonitorChecker.validation(target(MonitorType.PORT, port = 0))
        )
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_port_invalid),
            MonitorChecker.validation(target(MonitorType.PORT, port = 70000))
        )
    }

    @Test
    fun validationAcceptsPort() {
        assertEquals(TargetValidation.Valid, MonitorChecker.validation(target(MonitorType.PORT)))
    }

    @Test
    fun validationRejectsHttpWithoutUrl() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_url_required),
            MonitorChecker.validation(target(MonitorType.HTTP, url = ""))
        )
    }

    @Test
    fun validationRejectsHttpWithoutHttpScheme() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_url_scheme),
            MonitorChecker.validation(target(MonitorType.HTTP, url = "ftp://example.com"))
        )
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_url_scheme),
            MonitorChecker.validation(target(MonitorType.HTTP, url = "example.com/ping"))
        )
    }

    @Test
    fun validationAcceptsHttpsScheme() {
        assertEquals(TargetValidation.Valid, MonitorChecker.validation(target(MonitorType.HTTP)))
        assertEquals(
            TargetValidation.Valid,
            MonitorChecker.validation(target(MonitorType.HTTP, url = "http://10.0.0.5:8080"))
        )
    }

    @Test
    fun validationRejectsBrokenStatusRange() {
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_status_range),
            MonitorChecker.validation(target(MonitorType.HTTP, statusMin = 400, statusMax = 200))
        )
        assertEquals(
            TargetValidation.Invalid(R.string.monitor_status_range),
            MonitorChecker.validation(target(MonitorType.HTTP, statusMin = 200, statusMax = 600))
        )
    }

    @Test
    fun addressTextShowsHostForIpProbe() {
        assertEquals(
            "example.com",
            target(MonitorType.IP, host = "example.com").addressText()
        )
        assertEquals(
            "10.0.0.5",
            target(MonitorType.IP, host = "10.0.0.5").addressText()
        )
    }

    @Test
    fun addressTextShowsHostAndPortForPortProbe() {
        assertEquals(
            "example.com:8443",
            target(MonitorType.PORT, host = "example.com", port = 8443).addressText()
        )
        assertEquals(
            "127.0.0.1:1",
            target(MonitorType.PORT, host = "127.0.0.1", port = 1).addressText()
        )
    }

    @Test
    fun addressTextShowsUrlForHttpProbe() {
        assertEquals(
            "https://example.com/ping",
            target(MonitorType.HTTP, url = "https://example.com/ping").addressText()
        )
        assertEquals(
            "http://10.0.0.5:8080/health",
            target(MonitorType.HTTP, url = "http://10.0.0.5:8080/health").addressText()
        )
    }
}
