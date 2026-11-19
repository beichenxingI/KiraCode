package com.kirakira.kiracode

import com.kirakira.kiracode.ui.common.resolveCoverCoilModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoverModelTest {

    @Test
    fun `absolute path becomes File model`() {
        val model = resolveCoverCoilModel("/data/user/0/kira/files/warehouse/covers/a.png")
        assertTrue(model is File)
    }

    @Test
    fun `data uri becomes byte array model`() {
        val uri = "data:image/png;base64,AAAA"
        assertTrue(resolveCoverCoilModel(uri) is ByteArray)
    }

    @Test
    fun `invalid data uri returns null`() {
        assertNull(resolveCoverCoilModel("data:image/png;base64,"))
    }

    @Test
    fun `blank avatar returns null`() {
        assertNull(resolveCoverCoilModel(null))
        assertNull(resolveCoverCoilModel(""))
    }
}
