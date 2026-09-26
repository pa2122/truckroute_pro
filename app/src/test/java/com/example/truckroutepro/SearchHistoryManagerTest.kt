package com.example.truckroutepro

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class SearchHistoryManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk()

        every { mockContext.getSharedPreferences("truck_search_history_prefs", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
    }

    @Test
    fun `getRecentSearches returns empty list initially when no data is saved`() {
        every { mockPrefs.getString("recent_searches_json", null) } returns null

        val searches = SearchHistoryManager.getRecentSearches(mockContext)
        assertTrue(searches.isEmpty())
    }

    @Test
    fun `addSearchItem adds multiple searches and they are retrieved correctly`() {
        // Initially empty
        every { mockPrefs.getString("recent_searches_json", null) } returns null

        val latLng1 = LatLng(30.0, -90.0)
        val latLng2 = LatLng(31.0, -91.0)

        // Capture what gets saved to prefs
        var savedJson = ""
        every { mockEditor.putString("recent_searches_json", capture(mutableListOf(savedJson))) } answers {
            savedJson = arg(1)
            mockEditor
        }

        // Add first search
        SearchHistoryManager.addSearchItem(mockContext, "Stop 1", "Address 1", latLng1)
        
        // Mock the prefs to return the updated json
        every { mockPrefs.getString("recent_searches_json", null) } answers { savedJson }

        // Add second search
        SearchHistoryManager.addSearchItem(mockContext, "Stop 2", "Address 2", latLng2)
        every { mockPrefs.getString("recent_searches_json", null) } answers { savedJson }

        val searches = SearchHistoryManager.getRecentSearches(mockContext)
        assertEquals(2, searches.size)
        // Most recent should be first
        assertEquals("Stop 2", searches[0].title)
        assertEquals("Address 2", searches[0].formattedAddress)
        assertEquals("Stop 1", searches[1].title)
        assertEquals("Address 1", searches[1].formattedAddress)
    }

    @Test
    fun `addSearchItem caps history at exactly 10 items`() {
        every { mockPrefs.getString("recent_searches_json", null) } returns null

        var savedJson = ""
        every { mockEditor.putString("recent_searches_json", capture(mutableListOf(savedJson))) } answers {
            savedJson = arg(1)
            mockEditor
        }

        // Add 12 items
        for (i in 1..12) {
            SearchHistoryManager.addSearchItem(mockContext, "Title $i", "Address $i", LatLng(30.0, -90.0))
            every { mockPrefs.getString("recent_searches_json", null) } answers { savedJson }
        }

        val searches = SearchHistoryManager.getRecentSearches(mockContext)
        assertEquals(10, searches.size)
        // The most recent 10 should be present: 12 down to 3
        assertEquals("Title 12", searches.first().title)
        assertEquals("Title 3", searches.last().title)
    }

    @Test
    fun `addSearchItem handles duplicate searches by moving them to the top`() {
        every { mockPrefs.getString("recent_searches_json", null) } returns null

        var savedJson = ""
        every { mockEditor.putString("recent_searches_json", capture(mutableListOf(savedJson))) } answers {
            savedJson = arg(1)
            mockEditor
        }

        SearchHistoryManager.addSearchItem(mockContext, "Dup", "Address 1", LatLng(30.0, -90.0))
        every { mockPrefs.getString("recent_searches_json", null) } answers { savedJson }
        
        SearchHistoryManager.addSearchItem(mockContext, "Other", "Address 2", LatLng(31.0, -91.0))
        every { mockPrefs.getString("recent_searches_json", null) } answers { savedJson }
        
        // Add the duplicate again (same address)
        SearchHistoryManager.addSearchItem(mockContext, "Dup", "Address 1", LatLng(30.0, -90.0))
        every { mockPrefs.getString("recent_searches_json", null) } answers { savedJson }

        val searches = SearchHistoryManager.getRecentSearches(mockContext)
        assertEquals(2, searches.size) // No duplicates
        
        // The duplicate should be moved to the top
        assertEquals("Dup", searches[0].title)
        assertEquals("Address 1", searches[0].formattedAddress)
        
        assertEquals("Other", searches[1].title)
        assertEquals("Address 2", searches[1].formattedAddress)
    }
}
