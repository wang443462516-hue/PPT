package com.hy.wristnav.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmapParserTest {
    @Test fun parsesTraditionalNotification() {
        val result = AmapParser.parse(NotificationInput(
            packageName = "com.autonavi.minimap",
            id = 1,
            postTime = 1,
            title = "导航中",
            text = "90米后左转，进入知春路",
            bigText = null,
            focusParam = null,
            extrasKeys = emptyList()
        ))
        assertNotNull(result.snapshotFields)
        assertEquals("left", result.snapshotFields?.maneuver)
        assertEquals(90, result.snapshotFields?.metersToTurn)
        assertEquals("知春路", result.snapshotFields?.road)
    }

    @Test fun parsesFocusFallback() {
        val result = AmapParser.parse(NotificationInput(
            packageName = "com.autonavi.minimap",
            id = 2,
            postTime = 1,
            title = "",
            text = "",
            bigText = "",
            focusParam = "{\"text\":\"180米后右转\",\"road\":\"示例路\"}",
            extrasKeys = listOf("miui.focus.param")
        ))
        assertEquals("right", result.snapshotFields?.maneuver)
        assertEquals(180, result.snapshotFields?.metersToTurn)
        assertEquals("示例路", result.snapshotFields?.road)
    }

    @Test fun rejectsOtherPackages() {
        val result = AmapParser.parse(NotificationInput("com.baidu.BaiduMap", 1, 1, "", "90米后左转", null, null, emptyList()))
        assertNull(result.snapshotFields)
        assertEquals("NOT_AMAP", result.failure)
    }

    @Test fun reportsUnreadableFocusParam() {
        val result = AmapParser.parse(NotificationInput("com.autonavi.minimap", 1, 1, "导航中", "", null, "not-json", listOf("miui.focus.param")))
        assertNull(result.snapshotFields)
        assertEquals("FOCUS_UNREADABLE", result.failure)
    }
}
