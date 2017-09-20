package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.*

class SetsSerializationTest : TestDependencyInjectionBase() {
    private companion object {
        val javaEmptySetClass = Collections.emptySet<Any>().javaClass
    }

    @Test
    fun `check set can be serialized as root of serialization graph`() {
        assertEqualAfterRoundTripSerialization(emptySet<Int>())
        assertEqualAfterRoundTripSerialization(setOf(1))
        assertEqualAfterRoundTripSerialization(setOf(1, 2))
    }

    @Test
    fun `check set can be serialized as part of SessionData`() {
        run {
            val sessionData = SessionData(123, setOf(1))
            assertEqualAfterRoundTripSerialization(sessionData)
        }
        run {
            val sessionData = SessionData(123, setOf(1, 2))
            assertEqualAfterRoundTripSerialization(sessionData)
        }
        run {
            val sessionData = SessionData(123, emptySet<Int>())
            assertEqualAfterRoundTripSerialization(sessionData)
        }
    }

    @Test
    fun `check empty set serialises as Java emptySet`() {
        val nameID = 0
        val serializedForm = emptySet<Int>().serialize()
        val output = ByteArrayOutputStream().apply {
            write(KryoHeaderV0_1.bytes)
            write(DefaultClassResolver.NAME + 2)
            write(nameID)
            write(javaEmptySetClass.name.toAscii())
            write(Kryo.NOT_NULL.toInt())
        }
        assertArrayEquals(output.toByteArray(), serializedForm.bytes)
    }
}
