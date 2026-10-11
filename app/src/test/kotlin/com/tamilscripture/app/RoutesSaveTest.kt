package com.tamilscripture.app

import com.tamilscripture.core.model.Passage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M2-10: after process death the back stack is restored from its saved routes, so every
 * route must survive a round trip (a route that does not would crash the restore).
 */
class RoutesSaveTest {
    private val p = Passage("IRVTAM", "2CO", 5, 17)

    private fun <T> roundTrip(value: T, serializer: KSerializer<T>) =
        assertEquals(value, Json.decodeFromString(serializer, Json.encodeToString(serializer, value)))

    @Test fun everyRouteRoundTrips() {
        roundTrip(HomeRoute, HomeRoute.serializer())
        roundTrip(PlansRoute, PlansRoute.serializer())
        roundTrip(StudyRoute, StudyRoute.serializer())
        roundTrip(SearchRoute(focus = true, query = "அன்பு"), SearchRoute.serializer())
        roundTrip(ReaderRoute(p, compare = true, select = false), ReaderRoute.serializer())
        roundTrip(PickerRoute(p), PickerRoute.serializer())
        roundTrip(CommentaryRoute(p), CommentaryRoute.serializer())
        roundTrip(SettingsRoute, SettingsRoute.serializer())
        roundTrip(DownloadsRoute, DownloadsRoute.serializer())
        roundTrip(AccountRoute, AccountRoute.serializer())
        roundTrip(MineRoute(3), MineRoute.serializer())
        roundTrip(StrongsRoute("H430"), StrongsRoute.serializer())
        roundTrip(PersonRoute("paul"), PersonRoute.serializer())
        roundTrip(PlaceRoute("corinth"), PlaceRoute.serializer())
        roundTrip(ArticleRoute("grace"), ArticleRoute.serializer())
        roundTrip(DictionaryRoute, DictionaryRoute.serializer())
        roundTrip(RootWordsRoute, RootWordsRoute.serializer())
        roundTrip(AtlasRoute("paul-2"), AtlasRoute.serializer())
    }
}
