package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.*
import com.example.ui.viewmodel.PanelEditorViewModel
import com.example.ui.viewmodel.PanelListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PanelRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PanelRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("panelcraft_cache", Context.MODE_PRIVATE).edit().clear().commit()
        repository = PanelRepository(firestore = null, context = context, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial panels loaded and observable`() = runTest {
        val initialPanels = repository.panels.value
        assertTrue("Repository should have default sample panels", initialPanels.isNotEmpty())
        val first = initialPanels.first()
        val retrieved = repository.getPanel(first.id)
        assertNotNull(retrieved)
        assertEquals(first.name, retrieved?.name)
    }

    @Test
    fun `test panel creation and retrieval CRUD operations`() = runTest {
        val newPanel = PanelDesign(
            id = UUID.randomUUID().toString(),
            name = "Substation Distribution Board",
            tag = "SS-DB-01",
            location = "Basement Substation",
            systemVoltage = SystemVoltage.THREE_PHASE_400_230,
            mainBreakerAmps = 250,
            circuits = listOf(
                CircuitBreaker(circuitNumber = 1, name = "Transformer Feeder", powerWatts = 15000.0, ratingAmps = 63)
            )
        )

        val createResult = repository.createPanel(newPanel)
        assertTrue(createResult.isSuccess)
        val created = createResult.getOrNull()
        assertNotNull(created)

        val inMemory = repository.getPanel(newPanel.id)
        assertNotNull(inMemory)
        assertEquals("Substation Distribution Board", inMemory?.name)

        val byIdResult = repository.getPanelById(newPanel.id)
        assertTrue(byIdResult.isSuccess)
        assertEquals("SS-DB-01", byIdResult.getOrNull()?.tag)
    }

    @Test
    fun `test panel update and delete CRUD operations`() = runTest {
        val testId = UUID.randomUUID().toString()
        val original = PanelDesign(
            id = testId,
            name = "Original Panel",
            tag = "DB-ORIG",
            mainBreakerAmps = 100
        )
        repository.createPanel(original)

        // Update
        val modified = original.copy(
            name = "Updated Distribution Board",
            mainBreakerAmps = 160
        )
        val updateResult = repository.updatePanel(modified)
        assertTrue(updateResult.isSuccess)
        assertEquals("Updated Distribution Board", repository.getPanel(testId)?.name)
        assertEquals(160, repository.getPanel(testId)?.mainBreakerAmps)

        // Delete
        val deleteResult = repository.deletePanel(testId)
        assertTrue(deleteResult.isSuccess)
        assertNull("Panel should be removed from repository", repository.getPanel(testId))
    }

    @Test
    fun `test circuit level CRUD operations inside repository`() = runTest {
        val testId = UUID.randomUUID().toString()
        val panel = PanelDesign(
            id = testId,
            name = "Circuit Test Panel",
            circuits = emptyList()
        )
        repository.createPanel(panel)

        // 1. Add circuit
        val cct1 = CircuitBreaker(id = "cct-1", circuitNumber = 1, name = "HVAC Chiller", powerWatts = 7500.0, ratingAmps = 32)
        val addResult = repository.addCircuit(testId, cct1)
        assertTrue(addResult.isSuccess)
        assertEquals(1, repository.getPanel(testId)?.circuits?.size)

        // 2. Update circuit
        val cct1Updated = cct1.copy(ratingAmps = 40, powerWatts = 9000.0)
        val updateResult = repository.updateCircuit(testId, cct1Updated)
        assertTrue(updateResult.isSuccess)
        val fetchedCct = repository.getPanel(testId)?.circuits?.find { it.id == "cct-1" }
        assertEquals(40, fetchedCct?.ratingAmps)
        assertEquals(9000.0, fetchedCct?.powerWatts ?: 0.0, 0.1)

        // 3. Add second circuit & reorder
        val cct2 = CircuitBreaker(id = "cct-2", circuitNumber = 2, name = "Lighting", powerWatts = 1200.0)
        repository.addCircuit(testId, cct2)
        assertEquals(2, repository.getPanel(testId)?.circuits?.size)

        val reordered = listOf(cct2, cct1Updated)
        repository.reorderCircuits(testId, reordered)
        assertEquals("cct-2", repository.getPanel(testId)?.circuits?.first()?.id)

        // 4. Delete circuit
        val deleteResult = repository.deleteCircuit(testId, "cct-1")
        assertTrue(deleteResult.isSuccess)
        assertEquals(1, repository.getPanel(testId)?.circuits?.size)
        assertEquals("cct-2", repository.getPanel(testId)?.circuits?.first()?.id)
    }

    @Test
    fun `test panel duplication`() = runTest {
        val testId = UUID.randomUUID().toString()
        val original = PanelDesign(
            id = testId,
            name = "Source Master Panel",
            tag = "MDB-SRC",
            circuits = listOf(CircuitBreaker(circuitNumber = 1, name = "Feed 1"))
        )
        repository.createPanel(original)

        val dupResult = repository.duplicatePanel(testId)
        assertTrue(dupResult.isSuccess)
        val duplicated = dupResult.getOrNull()
        assertNotNull(duplicated)
        assertNotEquals(testId, duplicated?.id)
        assertTrue(duplicated?.name?.contains("Copy") == true)
        assertEquals(1, duplicated?.circuits?.size)
    }

    @Test
    fun `test PanelListViewModel integration with repository`() = runTest {
        val viewModel = PanelListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Panels should not be empty once loaded", state.panels.isNotEmpty())

        // Search filtering
        viewModel.setSearchQuery("Villa")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filteredPanels.all { it.name.contains("Villa", ignoreCase = true) })

        // Trigger create via viewModel
        viewModel.createPanel(
            name = "Hospital Emergency DB",
            tag = "DB-HOSP-EMG",
            location = "ICU Wing",
            mainBreakerAmps = 125
        )
        testScheduler.advanceUntilIdle()

        val found = repository.panels.value.find { it.name == "Hospital Emergency DB" }
        assertNotNull(found)
        assertEquals("DB-HOSP-EMG", found?.tag)

        // Delete via viewModel
        viewModel.deletePanel(found!!.id)
        testScheduler.advanceUntilIdle()
        assertNull(repository.getPanel(found.id))
    }

    @Test
    fun `test PanelEditorViewModel integration with repository`() = runTest {
        val testId = UUID.randomUUID().toString()
        val testPanel = PanelDesign(
            id = testId,
            name = "Editor Test Panel",
            circuits = listOf(
                CircuitBreaker(id = "c1", circuitNumber = 1, name = "Load 1", powerWatts = 3000.0, assignedPhase = Phase.L1, isOn = true),
                CircuitBreaker(id = "c2", circuitNumber = 2, name = "Load 2", powerWatts = 3000.0, assignedPhase = Phase.L1, isOn = true)
            )
        )
        repository.createPanel(testPanel)

        val viewModel = PanelEditorViewModel(panelId = testId, repository = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        testScheduler.advanceUntilIdle()

        val editorState = viewModel.uiState.value
        assertEquals("Editor Test Panel", editorState.panel?.name)
        assertNotNull(editorState.calculationSummary)

        // Toggle circuit
        viewModel.toggleCircuit("c1")
        testScheduler.advanceUntilIdle()
        val toggled = repository.getPanel(testId)?.circuits?.find { it.id == "c1" }
        assertFalse(toggled?.isOn ?: true)

        // Balance phases
        viewModel.balancePhases()
        testScheduler.advanceUntilIdle()
        val balanced = repository.getPanel(testId)
        assertNotNull(balanced)
        val l1Count = balanced?.circuits?.count { it.assignedPhase == Phase.L1 } ?: 0
        val l2Count = balanced?.circuits?.count { it.assignedPhase == Phase.L2 } ?: 0
        assertTrue("Phases should be balanced across different phases", l1Count == 1 && l2Count == 1)
    }
}
