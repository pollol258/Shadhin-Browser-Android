package com.shadhin.browser.storage

import android.content.ContextWrapper
import com.shadhin.browser.engine.QuickDialShortcut
import com.shadhin.browser.engine.UrlNormalizer
import com.shadhin.browser.network.DohResolver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sun.reflect.ReflectionFactory
import java.io.File

/**
 * Fast local JVM unit test for QuickDialRepository testing persistence, editing, deletion, reordering, reset, and URL normalization.
 */
class QuickDialRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: QuickDialRepository

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "shadhin_test_${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        val rf = ReflectionFactory.getReflectionFactory()
        val objConstructor = Any::class.java.getDeclaredConstructor()
        val baseConstructor = rf.newConstructorForSerialization(ContextWrapper::class.java, objConstructor)
        val dummyBase = baseConstructor.newInstance() as ContextWrapper

        val testContext = object : ContextWrapper(dummyBase) {
            override fun getFilesDir(): File = tempDir
            override fun getDataDir(): File = tempDir
        }

        repository = QuickDialRepository(testContext, DohResolver(), customStorageDir = tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `test default shortcuts are populated when storage is empty`() {
        val shortcuts = repository.loadShortcuts()
        assertEquals(6, shortcuts.size)
        val titles = shortcuts.map { it.title }
        assertTrue(titles.contains("GitHub"))
        assertTrue(titles.contains("Hacker News"))
        assertTrue(titles.contains("Wikipedia"))
        assertTrue(titles.contains("EFF"))
        assertTrue(titles.contains("DuckDuckGo"))
        assertTrue(titles.contains("ArXiv"))
    }

    @Test
    fun `test adding user shortcut persists and normalizes URL`() = runBlocking {
        val added = repository.addShortcut("DuckDuckGo Test", "duckduckgo.com")
        assertNotNull(added.id)
        assertEquals("https://duckduckgo.com", added.url)

        val reloaded = repository.loadShortcuts()
        assertTrue(reloaded.any { it.id == added.id && it.title == "DuckDuckGo Test" })
    }

    @Test
    fun `test editing shortcut updates title and url`() = runBlocking {
        val initial = repository.loadShortcuts().first()
        val updatedList = repository.editShortcut(initial.id, "GitHub Enterprise", "enterprise.github.com")

        val modified = updatedList.first { it.id == initial.id }
        assertEquals("GitHub Enterprise", modified.title)
        assertEquals("https://enterprise.github.com", modified.url)

        val reloaded = repository.loadShortcuts()
        val reloadedModified = reloaded.first { it.id == initial.id }
        assertEquals("GitHub Enterprise", reloadedModified.title)
        assertEquals("https://enterprise.github.com", reloadedModified.url)
    }

    @Test
    fun `test deleting shortcut and restoring via undo`() = runBlocking {
        val added = repository.addShortcut("Temp Site", "https://temp.org")
        val (afterDelete, deletedItem) = repository.deleteShortcut(added.id)

        assertNotNull(deletedItem)
        assertEquals(added.id, deletedItem?.id)
        assertFalse(afterDelete.any { it.id == added.id })

        val reloaded = repository.loadShortcuts()
        assertFalse(reloaded.any { it.id == added.id })

        // Restore via undo
        val afterRestore = repository.restoreShortcut(deletedItem!!, 0)
        assertTrue(afterRestore.any { it.id == added.id })
        assertEquals(added.id, afterRestore.first().id)
    }

    @Test
    fun `test moving shortcut left and right reorders items`() {
        val shortcuts = repository.loadShortcuts()
        val firstId = shortcuts[0].id
        val secondId = shortcuts[1].id

        // Move first item right
        val afterMoveRight = repository.moveShortcut(firstId, moveLeft = false)
        assertEquals(secondId, afterMoveRight[0].id)
        assertEquals(firstId, afterMoveRight[1].id)

        // Move it back left
        val afterMoveLeft = repository.moveShortcut(firstId, moveLeft = true)
        assertEquals(firstId, afterMoveLeft[0].id)
        assertEquals(secondId, afterMoveLeft[1].id)
    }

    @Test
    fun `test reset to defaults restores initial 6 tiles`() = runBlocking {
        repository.addShortcut("Custom 1", "https://example.com")
        repository.addShortcut("Custom 2", "https://sample.com")
        assertTrue(repository.loadShortcuts().size > 6)

        val resetList = repository.resetToDefaults()
        assertEquals(6, resetList.size)
        val titles = resetList.map { it.title }
        assertTrue(titles.contains("GitHub"))
        assertTrue(titles.contains("ArXiv"))

        val reloaded = repository.loadShortcuts()
        assertEquals(6, reloaded.size)
    }

    @Test
    fun `test URL normalizer validation and normalization`() {
        assertTrue(UrlNormalizer.isValidUrlOrHost("github.com"))
        assertTrue(UrlNormalizer.isValidUrlOrHost("https://news.ycombinator.com/item?id=1"))
        assertTrue(UrlNormalizer.isValidUrlOrHost("192.168.1.1:8080"))
        assertTrue(UrlNormalizer.isValidUrlOrHost("localhost"))
        assertFalse(UrlNormalizer.isValidUrlOrHost("just some search text"))
        assertFalse(UrlNormalizer.isValidUrlOrHost(""))

        assertEquals("https://github.com", UrlNormalizer.normalizeWebAddress("github.com"))
        assertEquals("https://test.com", UrlNormalizer.normalizeWebAddress("https://test.com"))
        assertEquals("http://test.com", UrlNormalizer.normalizeWebAddress("http://test.com"))
    }

    @Test
    fun `test all default and user quick dials resolve to valid normalized https URLs`() = runBlocking {
        val defaults = repository.loadShortcuts()
        for (dial in defaults) {
            val normalized = UrlNormalizer.normalizeInput(dial.url)
            assertTrue("Default ${dial.title} must be HTTPS: $normalized", normalized.startsWith("https://"))
            assertTrue("Default ${dial.title} must be valid web address", UrlNormalizer.isValidUrlOrHost(dial.url))
        }

        val custom = repository.addShortcut("Custom Test", "arstechnica.com")
        val normalizedCustom = UrlNormalizer.normalizeInput(custom.url)
        assertEquals("https://arstechnica.com", normalizedCustom)
    }

    @Test
    fun `test address bar submission vs outside tap criteria`() {
        val initialUrl = "https://github.com"

        // Unchanged URL: should not be considered modified
        val unchanged = "https://github.com"
        assertFalse(unchanged != initialUrl && UrlNormalizer.isValidUrlOrHost(unchanged))

        // Modified valid URL: outside tap will navigate
        val modifiedValid = "duckduckgo.com"
        assertTrue(modifiedValid != initialUrl && UrlNormalizer.isValidUrlOrHost(modifiedValid))
        assertEquals("https://duckduckgo.com", UrlNormalizer.normalizeInput(modifiedValid))

        // Invalid query: outside tap will NOT navigate
        val invalidSearch = "latest tech news today"
        assertFalse(invalidSearch != initialUrl && UrlNormalizer.isValidUrlOrHost(invalidSearch))

        // Empty input: outside tap will NOT navigate
        val empty = ""
        assertFalse(empty != initialUrl && UrlNormalizer.isValidUrlOrHost(empty))
    }
}
