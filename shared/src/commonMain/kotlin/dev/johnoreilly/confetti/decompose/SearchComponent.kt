package dev.johnoreilly.confetti.decompose

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import dev.johnoreilly.confetti.ConfettiRepository
import dev.johnoreilly.confetti.auth.User
import dev.johnoreilly.confetti.fragment.SessionDetails
import dev.johnoreilly.confetti.fragment.SpeakerDetails
import doist.x.normalize.Form
import doist.x.normalize.normalize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface SearchComponent {

    val isLoggedIn: Boolean
    val search: Flow<String>
    val loading: Flow<Boolean>
    val sessions: Flow<List<SessionDetails>>
    val bookmarks: Flow<Set<String>>
    val speakers: Flow<List<SpeakerDetails>>

    fun onSearchChange(query: String)
    fun addBookmark(sessionId: String)
    fun removeBookmark(sessionId: String)
    fun onSessionClicked(id: String)
    fun onSpeakerClicked(id: String)
    fun onSignInClicked()
}

class DefaultSearchComponent(
    componentContext: ComponentContext,
    private val conference: String,
    private val user: User?,
    private val onSessionSelected: (id: String) -> Unit,
    private val onSpeakerSelected: (id: String) -> Unit,
    private val onSignIn: () -> Unit,
) : SearchComponent, KoinComponent, ComponentContext by componentContext {

    private val repository: ConfettiRepository by inject()
    private val coroutineScope = coroutineScope()

    private val _search = MutableStateFlow("")
    override val search: Flow<String> = _search.asStateFlow()

    private val sessionsComponent =
        SessionsSimpleComponent(
            componentContext = childContext("Sessions"),
            conference = conference,
            user = user,
        )

    private val speakersComponent =
        SpeakersSimpleComponent(
            componentContext = childContext("Speakers"),
            conference = conference,
            repository = repository,
        )

    override val isLoggedIn: Boolean = user != null

    override val loading: Flow<Boolean> = sessionsComponent
        .uiState
        .combine(speakersComponent.speakers) { sessions, speakers ->
            sessions is SessionsUiState.Loading || speakers is SpeakersUiState.Loading
        }

    private val successSessions = sessionsComponent
        .uiState
        .filterIsInstance<SessionsUiState.Success>()

    private val successSpeakers = speakersComponent
        .speakers
        .filterIsInstance<SpeakersUiState.Success>()

    override val sessions: Flow<List<SessionDetails>> = successSessions
        .map { state ->
            state
                .sessionsByStartTimeList
                .flatMap { sessions -> sessions.values }
                .flatten()
        }
        .combine(search) { sessions, search ->
            sessions.filter { filterSessions(it, search) }
        }
        .flowOn(Dispatchers.Default)

    override val bookmarks: Flow<Set<String>> = successSessions
        .map { state -> state.bookmarks }

    override val speakers: Flow<List<SpeakerDetails>> = successSpeakers
        .map { state -> state.speakers }
        .combine(search) { sessions, search ->
            sessions.filter { filterSpeakers(it, search) }
        }
        .flowOn(Dispatchers.Default)

    private fun filterSpeakers(details: SpeakerDetails, filter: String): Boolean {
        if (filter.isBlank()) return false

        val ignoreCase = true
        val ignoreDiacritics = !filter.containsDiacritics()

        return sequenceOf(
            details.name,
            details.bio.orEmpty(),
            details.city.orEmpty(),
            details.company.orEmpty(),
        ).any { it.adjustWith(ignoreDiacritics).contains(filter, ignoreCase) }
    }

    private val diacriticsRegex = "\\p{Mn}+".toRegex()

    fun String.containsDiacritics(): Boolean = normalizeNfd().contains(diacriticsRegex)

    fun String.adjustWith(ignoreDiacritics: Boolean): String {
        return if (ignoreDiacritics) {
            normalizeNfd().replace(diacriticsRegex, "")
        } else {
            this
        }
    }

    private fun String.normalizeNfd(): String = normalize(Form.NFD)

    private fun filterSessions(details: SessionDetails, filter: String): Boolean {
        if (filter.isBlank()) return false

        val ignoreCase = true
        val ignoreDiacritics = !filter.containsDiacritics()

        return sequence {
            yield(details.title)
            yield(details.sessionDescription.orEmpty())
            yield(details.room?.name.orEmpty())

            details.speakers.forEach {
                yield(it.speakerDetails.name)
            }
        }.any {
            it.adjustWith(ignoreDiacritics).contains(filter, ignoreCase)
        }
    }

    override fun onSearchChange(query: String) {
        _search.update { query }
    }

    override fun addBookmark(sessionId: String) {
        coroutineScope.launch {
            repository.addBookmark(conference, user?.uid, user, sessionId)
        }
    }

    override fun removeBookmark(sessionId: String) {
        coroutineScope.launch {
            repository.removeBookmark(conference, user?.uid, user, sessionId)
        }
    }

    override fun onSessionClicked(id: String) {
        onSessionSelected(id)
    }

    override fun onSpeakerClicked(id: String) {
        onSpeakerSelected(id)
    }

    override fun onSignInClicked() {
        onSignIn()
    }
}
