package com.slopus.happy.journeys

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.slopus.happy.model.Pin
import com.slopus.happy.model.ProjectHomeRow
import com.slopus.happy.model.ProjectHomeSection
import com.slopus.happy.model.SessionListGrouping
import com.slopus.happy.ui.HappyApp
import com.slopus.happy.ui.PHONE
import com.slopus.happy.ui.node
import com.slopus.happy.ui.home.HomeRow
import com.slopus.happy.ui.home.HomeServices
import com.slopus.happy.ui.home.PinTags
import com.slopus.happy.ui.home.ProjectListTags
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #1707 on the home list grouped by project: swiping a card uncovers Pin,
 * which moves it to the Pinned section at the top, in the order pinned, in
 * the account's settings; a long press on a pinned card drags it to
 * another place in that order; held still, it opens the project's menu.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PinJourneyTest {
    @get:Rule val rule = createComposeRule()

    private val journey = Journey()

    @After fun tearDown() = journey.close()

    @Test
    fun swipeToPinThenDragToReorder() {
        journey.machine()
        journey.session("s1", updatedAt = 300, path = "/h/app", title = "App chat")
        journey.session("s2", updatedAt = 200, path = "/h/docs", title = "Docs chat")
        journey.session("s3", updatedAt = 100, path = "/h/site", title = "Site chat")
        val graph = journey.graph()
        journey.signIn(graph)
        graph.settings.change { it.copy(sessionListGrouping = SessionListGrouping.Project) }
        val home = HomeServices.of(graph)
        rule.setContent { HappyApp(graph) }
        rule.waitUntil(10_000) { cards(home).size == 3 }

        fun card(name: String) = rule.onNode(hasTestTag(ProjectListTags.PROJECT) and hasAnyDescendant(hasText(name)), useUnmergedTree = true)
        fun pinAction(name: String) = rule.node(PinTags.action(cards(home).first { it.project.name == name }.project.id))
        fun stored() = graph.settings.settings.value.pinnedFolders.entries

        // Swipe docs: its Pin pill, then the card leads the list under Pinned.
        card("docs").performTouchInput { swipeLeft() }
        pinAction("docs").assertContentDescriptionEquals("Pin").performClick()
        rule.waitUntil(5_000) { stored() == listOf(Pin(Journey.MACHINE, "/h/docs")) }
        rule.waitUntil(5_000) { pinnedNames(home) == listOf("docs") }

        // site second: pins keep the order they were made in.
        card("site").performTouchInput { swipeLeft() }
        pinAction("site").performClick()
        rule.waitUntil(5_000) { pinnedNames(home) == listOf("docs", "site") }
        card("site").performTouchInput { swipeLeft() }
        pinAction("site").assertContentDescriptionEquals("Unpin")
        card("site").performTouchInput { swipeRight() }

        // A long press lifts site; dragged up past docs, it takes docs' place, written on the drop.
        card("site").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy(Offset(0f, -height / 5f)) }
            up()
        }
        rule.waitUntil(5_000) { stored() == listOf(Pin(Journey.MACHINE, "/h/site"), Pin(Journey.MACHINE, "/h/docs")) }
        rule.waitUntil(5_000) { pinnedNames(home) == listOf("site", "docs") }
        assertEquals("app stays under its computer", listOf("app"), cards(home).filterNot { it.project.pinned }.map { it.project.name })

        // Held still, a pinned card's long press is the project's menu, and nothing moves.
        card("docs").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        rule.waitForText("Create workspace", substring = true)
        assertEquals(listOf(Pin(Journey.MACHINE, "/h/site"), Pin(Journey.MACHINE, "/h/docs")), stored())
    }

    private fun projectRows(home: HomeServices): List<ProjectHomeRow> =
        home.list.state.value?.rows.orEmpty().mapNotNull { (it as? HomeRow.Project)?.row }

    private fun cards(home: HomeServices) = projectRows(home).filterIsInstance<ProjectHomeRow.ProjectCard>()

    /** The cards under the Pinned heading, top to bottom. */
    private fun pinnedNames(home: HomeServices): List<String> {
        val rows = projectRows(home)
        if ((rows.firstOrNull() as? ProjectHomeRow.Section)?.section != ProjectHomeSection.Pinned) return emptyList()
        return rows.drop(1).takeWhile { it is ProjectHomeRow.ProjectCard && it.project.pinned }.map { (it as ProjectHomeRow.ProjectCard).project.name }
    }
}
