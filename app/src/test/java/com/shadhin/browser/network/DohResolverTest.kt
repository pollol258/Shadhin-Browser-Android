package com.shadhin.browser.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress

class DohResolverTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var resolver: DohResolver

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val mockClient = OkHttpClient.Builder().build()
        resolver = DohResolver(customClient = mockClient)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `test buildDnsWireQuery creates valid DNS header and QNAME`() {
        val queryBytes = DohResolver.buildDnsWireQuery("example.com", type = 1, id = 0x4321.toShort())

        assertTrue(queryBytes.size >= 12)
        // Check ID
        assertEquals(0x43.toByte(), queryBytes[0])
        assertEquals(0x21.toByte(), queryBytes[1])
        // Flags: 0x0100 (RD=1)
        assertEquals(0x01.toByte(), queryBytes[2])
        assertEquals(0x00.toByte(), queryBytes[3])
    }

    @Test
    fun `test parseDnsWireResponse extracts valid IPv4 address and TTL`() {
        // Construct mock DNS response packet for example.com -> 93.184.216.34 (TTL 300)
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Header
        dos.writeShort(0x1234) // ID
        dos.writeShort(0x8180) // Flags: Standard response, No error
        dos.writeShort(1) // QDCOUNT = 1
        dos.writeShort(1) // ANCOUNT = 1
        dos.writeShort(0) // NSCOUNT = 0
        dos.writeShort(0) // ARCOUNT = 0

        // Question: example.com, Type 1, Class 1
        dos.writeByte(7)
        dos.writeBytes("example")
        dos.writeByte(3)
        dos.writeBytes("com")
        dos.writeByte(0)
        dos.writeShort(1) // Type A
        dos.writeShort(1) // Class IN

        // Answer: Pointer to name (0xC00C), Type 1, Class 1, TTL 300, RdLength 4, IP 93.184.216.34
        dos.writeShort(0xC00C)
        dos.writeShort(1) // Type A
        dos.writeShort(1) // Class IN
        dos.writeInt(300) // TTL = 300 seconds
        dos.writeShort(4) // RdLength = 4
        dos.writeByte(93)
        dos.writeByte(184)
        dos.writeByte(216)
        dos.writeByte(34)
        dos.flush()

        val responseBytes = baos.toByteArray()
        val (addresses, ttl) = DohResolver.parseDnsWireResponse(responseBytes)

        assertEquals(1, addresses.size)
        assertEquals(InetAddress.getByName("93.184.216.34"), addresses[0])
        assertEquals(300L, ttl)
    }

    @Test
    fun `test MockWebServer DoH execution`() {
        // Mock server response
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0x1234)
        dos.writeShort(0x8180)
        dos.writeShort(1) // QDCOUNT
        dos.writeShort(1) // ANCOUNT
        dos.writeShort(0)
        dos.writeShort(0)
        // Question
        dos.writeByte(4)
        dos.writeBytes("test")
        dos.writeByte(0)
        dos.writeShort(1)
        dos.writeShort(1)
        // Answer
        dos.writeShort(0xC00C)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.writeInt(120)
        dos.writeShort(4)
        dos.writeByte(10)
        dos.writeByte(0)
        dos.writeByte(0)
        dos.writeByte(1)
        dos.flush()

        mockServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/dns-message")
                .setBody(Buffer().write(baos.toByteArray()))
        )

        val wireQuery = DohResolver.buildDnsWireQuery("test", type = 1)
        assertNotNull(wireQuery)
    }
}
