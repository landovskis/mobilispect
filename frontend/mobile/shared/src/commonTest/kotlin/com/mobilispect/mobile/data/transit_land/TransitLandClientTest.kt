package com.mobilispect.mobile.data.transit_land

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.test.assertNotNull


@OptIn(ExperimentalCoroutinesApi::class)
class TransitLandClientTest {
    private lateinit var configRepository: FakeTransitLandConfigRepository
    private lateinit var retrofitBuilder: Retrofit.Builder

    private lateinit var subject: TransitLandClient

    @Before
    fun setup() {
        configRepository = FakeTransitLandConfigRepository()
        retrofitBuilder = Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(Gson()))
    }

    @Test
    fun configIsMissing() = runTest {
        val server = MockWebServer()

        val transitLandAPI = retrofitBuilder
            .baseUrl(server.url("/"))
            .build()
            .create(TransitLandAPI::class.java)
        subject = TransitLandClient(transitLandAPI, configRepository)

        val actualResponse = subject.fromRef("r-f25ej-19")

        assertThat(actualResponse.isFailure).isTrue()
    }

    @Test
    fun routeWasNotFound() = runTest {
        configRepository.insert(TransitLandConfig(apiKey = "KEY"))

        val server = MockWebServer()
        val response = MockResponse()
            .setResponseCode(200)
            .setBody(
                """
{
    "routes": []
}
            """
            )
        server.enqueue(response)
        server.start()

        subject = TransitLandClient(transitLandAPI(server), configRepository)

        val actualResponse = subject.fromRef("r-f25ej-19")
        server.shutdown()

        assertThat(actualResponse.isSuccess).isTrue()
        assertThat(actualResponse.getOrNull()?.routes).isEmpty()
    }

    @Test
    fun routeWasFound() = runTest {
        configRepository.insert(TransitLandConfig(apiKey = "KEY"))

        val server = MockWebServer()
        val response = MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
    "meta": {
        "after": 12247961,
        "next": "https://api.transit.land/api/v2/rest/routes?after=12247961&route_key=r-f25ej-18"
    },
    "routes": [
        {
            "agency": {
                "agency_id": "STM",
                "agency_name": "Société de transport de Montréal",
                "id": 195839,
                "onestop_id": "o-f25d-socitdetransportdemontral"
            },
            "continuous_drop_off": null,
            "continuous_pickup": null,
            "feed_version": {
                "feed": {
                    "id": 68,
                    "onestop_id": "f-f25d-socitdetransportdemontral"
                },
                "fetched_at": "2022-10-21T16:01:00.795863Z",
                "id": 289961,
                "sha1": "5817a9f002832e405651ccdd7e929d3c10590d25"
            },
            "id": 12247961,
            "onestop_id": "r-f25ej-18",
            "route_color": "009EE0",
            "route_desc": "",
            "route_id": "18",
            "route_long_name": "Beaubien",
            "route_short_name": "18",
            "route_sort_order": 0,
            "route_stops": [
                {
                    "stop": {
                        "id": 269618621,
                        "stop_id": "50101",
                        "stop_name": "Louis-Hippolyte-La Fontaine / Curatteau"
                    }
                },
                {
                    "stop": {
                        "id": 269618633,
                        "stop_id": "61826",
                        "stop_name": "Beaubien / des Écores"
                    }
                },
                {
                    "stop": {
                        "id": 269618817,
                        "stop_id": "50109",
                        "stop_name": "Louis-Hippolyte-La Fontaine / Fonteneau"
                    }
                },
                {
                    "stop": {
                        "id": 269620028,
                        "stop_id": "51377",
                        "stop_name": "Saint-Dominique / Saint-Zotique"
                    }
                },
                {
                    "stop": {
                        "id": 269620089,
                        "stop_id": "51441",
                        "stop_name": "Beaubien / Saint-Laurent"
                    }
                },
                {
                    "stop": {
                        "id": 269620093,
                        "stop_id": "51445",
                        "stop_name": "Saint-Dominique / Beaubien"
                    }
                },
                {
                    "stop": {
                        "id": 269620128,
                        "stop_id": "51481",
                        "stop_name": "Beaubien / Alma"
                    }
                },
                {
                    "stop": {
                        "id": 269620129,
                        "stop_id": "51482",
                        "stop_name": "Beaubien / Alma"
                    }
                },
                {
                    "stop": {
                        "id": 269620155,
                        "stop_id": "51509",
                        "stop_name": "Beaubien / Saint-Denis"
                    }
                },
                {
                    "stop": {
                        "id": 269620156,
                        "stop_id": "51511",
                        "stop_name": "Beaubien / Saint-Denis"
                    }
                },
                {
                    "stop": {
                        "id": 269620165,
                        "stop_id": "51522",
                        "stop_name": "Station Beaubien"
                    }
                },
                {
                    "stop": {
                        "id": 269620192,
                        "stop_id": "51550",
                        "stop_name": "Beaubien / Saint-Hubert"
                    }
                },
                {
                    "stop": {
                        "id": 269620193,
                        "stop_id": "51551",
                        "stop_name": "Beaubien / Saint-Hubert"
                    }
                },
                {
                    "stop": {
                        "id": 269620221,
                        "stop_id": "51584",
                        "stop_name": "Beaubien / Christophe-Colomb"
                    }
                },
                {
                    "stop": {
                        "id": 269620223,
                        "stop_id": "51586",
                        "stop_name": "Beaubien / Christophe-Colomb"
                    }
                },
                {
                    "stop": {
                        "id": 269620233,
                        "stop_id": "51598",
                        "stop_name": "Beaubien / De Normanville"
                    }
                },
                {
                    "stop": {
                        "id": 269620261,
                        "stop_id": "51628",
                        "stop_name": "Beaubien / De Lanaudière"
                    }
                },
                {
                    "stop": {
                        "id": 269620262,
                        "stop_id": "51629",
                        "stop_name": "Beaubien / De Lanaudière"
                    }
                },
                {
                    "stop": {
                        "id": 269620286,
                        "stop_id": "51654",
                        "stop_name": "Beaubien / Fabre"
                    }
                },
                {
                    "stop": {
                        "id": 269620287,
                        "stop_id": "51655",
                        "stop_name": "Beaubien / Fabre"
                    }
                },
                {
                    "stop": {
                        "id": 269620308,
                        "stop_id": "51677",
                        "stop_name": "Beaubien / Papineau"
                    }
                },
                {
                    "stop": {
                        "id": 269620310,
                        "stop_id": "51679",
                        "stop_name": "Beaubien / Papineau"
                    }
                },
                {
                    "stop": {
                        "id": 269620325,
                        "stop_id": "51694",
                        "stop_name": "Beaubien / Chabot"
                    }
                },
                {
                    "stop": {
                        "id": 269620326,
                        "stop_id": "51695",
                        "stop_name": "Beaubien / Chabot"
                    }
                },
                {
                    "stop": {
                        "id": 269620367,
                        "stop_id": "51739",
                        "stop_name": "Beaubien / De Lorimier"
                    }
                },
                {
                    "stop": {
                        "id": 269620369,
                        "stop_id": "51741",
                        "stop_name": "Beaubien / De Lorimier"
                    }
                },
                {
                    "stop": {
                        "id": 269620406,
                        "stop_id": "51781",
                        "stop_name": "Beaubien / des Écores"
                    }
                },
                {
                    "stop": {
                        "id": 269620421,
                        "stop_id": "51803",
                        "stop_name": "Beaubien / D'Iberville"
                    }
                },
                {
                    "stop": {
                        "id": 269620423,
                        "stop_id": "51805",
                        "stop_name": "Beaubien / D'Iberville"
                    }
                },
                {
                    "stop": {
                        "id": 269620446,
                        "stop_id": "51835",
                        "stop_name": "Beaubien / 1re Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620473,
                        "stop_id": "51868",
                        "stop_name": "Beaubien / 6e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620474,
                        "stop_id": "51869",
                        "stop_name": "Beaubien / 6e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620513,
                        "stop_id": "51914",
                        "stop_name": "Beaubien / 9e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620514,
                        "stop_id": "51915",
                        "stop_name": "Beaubien / 9e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620540,
                        "stop_id": "51944",
                        "stop_name": "Beaubien / Saint-Michel"
                    }
                },
                {
                    "stop": {
                        "id": 269620542,
                        "stop_id": "51946",
                        "stop_name": "Beaubien / Saint-Michel"
                    }
                },
                {
                    "stop": {
                        "id": 269620575,
                        "stop_id": "51982",
                        "stop_name": "Beaubien / 13e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620576,
                        "stop_id": "51983",
                        "stop_name": "Beaubien / 13e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620609,
                        "stop_id": "52020",
                        "stop_name": "Collège de Rosemont (Beaubien / 16e Avenue)"
                    }
                },
                {
                    "stop": {
                        "id": 269620610,
                        "stop_id": "52021",
                        "stop_name": "Collège de Rosemont (Beaubien / 16e Avenue)"
                    }
                },
                {
                    "stop": {
                        "id": 269620660,
                        "stop_id": "52074",
                        "stop_name": "Beaubien / 19e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620661,
                        "stop_id": "52075",
                        "stop_name": "Beaubien / 19e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620711,
                        "stop_id": "52127",
                        "stop_name": "Beaubien / Pie-IX"
                    }
                },
                {
                    "stop": {
                        "id": 269620713,
                        "stop_id": "52129",
                        "stop_name": "Beaubien / Pie-IX"
                    }
                },
                {
                    "stop": {
                        "id": 269620746,
                        "stop_id": "52168",
                        "stop_name": "Beaubien / 25e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620762,
                        "stop_id": "52186",
                        "stop_name": "Beaubien / 26e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620787,
                        "stop_id": "52215",
                        "stop_name": "Beaubien / 29e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620802,
                        "stop_id": "52231",
                        "stop_name": "Beaubien / 29e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620831,
                        "stop_id": "52262",
                        "stop_name": "Beaubien / 31e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620832,
                        "stop_id": "52263",
                        "stop_name": "Beaubien / 31e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620865,
                        "stop_id": "52300",
                        "stop_name": "Beaubien / 33e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620866,
                        "stop_id": "52301",
                        "stop_name": "Beaubien / 33e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620896,
                        "stop_id": "52332",
                        "stop_name": "Beaubien / 35e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620897,
                        "stop_id": "52333",
                        "stop_name": "Beaubien / 35e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620920,
                        "stop_id": "52358",
                        "stop_name": "Beaubien / Viau"
                    }
                },
                {
                    "stop": {
                        "id": 269620922,
                        "stop_id": "52360",
                        "stop_name": "Beaubien / Viau"
                    }
                },
                {
                    "stop": {
                        "id": 269620956,
                        "stop_id": "52396",
                        "stop_name": "Beaubien / 39e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269620972,
                        "stop_id": "52414",
                        "stop_name": "Beaubien / 40e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269621003,
                        "stop_id": "52445",
                        "stop_name": "Beaubien / 42e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269621067,
                        "stop_id": "52513",
                        "stop_name": "Beaubien / de l'Assomption"
                    }
                },
                {
                    "stop": {
                        "id": 269621069,
                        "stop_id": "52515",
                        "stop_name": "Beaubien / de l'Assomption"
                    }
                },
                {
                    "stop": {
                        "id": 269621088,
                        "stop_id": "52538",
                        "stop_name": "Beaubien / Châtelain"
                    }
                },
                {
                    "stop": {
                        "id": 269621089,
                        "stop_id": "52539",
                        "stop_name": "Beaubien / Châtelain"
                    }
                },
                {
                    "stop": {
                        "id": 269621120,
                        "stop_id": "52575",
                        "stop_name": "Beaubien / de Pontoise"
                    }
                },
                {
                    "stop": {
                        "id": 269621121,
                        "stop_id": "52576",
                        "stop_name": "Beaubien / de Pontoise"
                    }
                },
                {
                    "stop": {
                        "id": 269621142,
                        "stop_id": "52601",
                        "stop_name": "Beaubien / Lacordaire"
                    }
                },
                {
                    "stop": {
                        "id": 269621144,
                        "stop_id": "52603",
                        "stop_name": "Beaubien / Lacordaire"
                    }
                },
                {
                    "stop": {
                        "id": 269621201,
                        "stop_id": "52672",
                        "stop_name": "Beaubien / Albani"
                    }
                },
                {
                    "stop": {
                        "id": 269621202,
                        "stop_id": "52673",
                        "stop_name": "Beaubien / Albani"
                    }
                },
                {
                    "stop": {
                        "id": 269621231,
                        "stop_id": "52704",
                        "stop_name": "Beaubien / de Carignan"
                    }
                },
                {
                    "stop": {
                        "id": 269621245,
                        "stop_id": "52721",
                        "stop_name": "Beaubien / De Repentigny"
                    }
                },
                {
                    "stop": {
                        "id": 269621271,
                        "stop_id": "52749",
                        "stop_name": "Beaubien / Langelier"
                    }
                },
                {
                    "stop": {
                        "id": 269621273,
                        "stop_id": "52751",
                        "stop_name": "Beaubien / Langelier"
                    }
                },
                {
                    "stop": {
                        "id": 269621309,
                        "stop_id": "52794",
                        "stop_name": "Beaubien / François-Boivin"
                    }
                },
                {
                    "stop": {
                        "id": 269621310,
                        "stop_id": "52795",
                        "stop_name": "Beaubien / François-Boivin"
                    }
                },
                {
                    "stop": {
                        "id": 269621348,
                        "stop_id": "52839",
                        "stop_name": "Beaubien / Pierre-Gadois"
                    }
                },
                {
                    "stop": {
                        "id": 269621349,
                        "stop_id": "52840",
                        "stop_name": "Beaubien / Pierre-Gadois"
                    }
                },
                {
                    "stop": {
                        "id": 269621720,
                        "stop_id": "53242",
                        "stop_name": "Sherbrooke / Lepailleur"
                    }
                },
                {
                    "stop": {
                        "id": 269621730,
                        "stop_id": "53253",
                        "stop_name": "Station Honoré-Beaugrand"
                    }
                },
                {
                    "stop": {
                        "id": 269621749,
                        "stop_id": "53275",
                        "stop_name": "Station Honoré-Beaugrand / Terminus Sud"
                    }
                },
                {
                    "stop": {
                        "id": 269621774,
                        "stop_id": "53303",
                        "stop_name": "Louis-Hippolyte-La Fontaine / Curatteau"
                    }
                },
                {
                    "stop": {
                        "id": 269622194,
                        "stop_id": "53751",
                        "stop_name": "Beaubien / Place Beaubien"
                    }
                },
                {
                    "stop": {
                        "id": 269622195,
                        "stop_id": "53752",
                        "stop_name": "Beaubien / Place Beaubien"
                    }
                },
                {
                    "stop": {
                        "id": 269622285,
                        "stop_id": "53849",
                        "stop_name": "Louis-H.-La Fontaine / Chénier"
                    }
                },
                {
                    "stop": {
                        "id": 269622286,
                        "stop_id": "53850",
                        "stop_name": "Beaubien / De Normanville"
                    }
                },
                {
                    "stop": {
                        "id": 269622287,
                        "stop_id": "53851",
                        "stop_name": "Station Beaubien"
                    }
                },
                {
                    "stop": {
                        "id": 269622517,
                        "stop_id": "54129",
                        "stop_name": "Beaubien / 42e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269622524,
                        "stop_id": "54136",
                        "stop_name": "Beaubien / 1re Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269622575,
                        "stop_id": "54207",
                        "stop_name": "Louis-Hippolyte-La Fontaine / Pierre-Corneille"
                    }
                },
                {
                    "stop": {
                        "id": 269622715,
                        "stop_id": "54398",
                        "stop_name": "Saint-Laurent / Saint-Zotique"
                    }
                },
                {
                    "stop": {
                        "id": 269622814,
                        "stop_id": "60634",
                        "stop_name": "Beaubien / 20e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269622815,
                        "stop_id": "60635",
                        "stop_name": "Beaubien / 20e Avenue"
                    }
                },
                {
                    "stop": {
                        "id": 269623036,
                        "stop_id": "54511",
                        "stop_name": "des Roseraies / Louis-H.-La Fontaine"
                    }
                },
                {
                    "stop": {
                        "id": 269623139,
                        "stop_id": "54616",
                        "stop_name": "Beaubien / des Galeries-d'Anjou"
                    }
                },
                {
                    "stop": {
                        "id": 269623141,
                        "stop_id": "54618",
                        "stop_name": "Beaubien / des Galeries-d'Anjou"
                    }
                },
                {
                    "stop": {
                        "id": 269623147,
                        "stop_id": "54625",
                        "stop_name": "Beaubien / des Roseraies"
                    }
                },
                {
                    "stop": {
                        "id": 269623148,
                        "stop_id": "54626",
                        "stop_name": "Beaubien / des Roseraies"
                    }
                },
                {
                    "stop": {
                        "id": 269623155,
                        "stop_id": "54633",
                        "stop_name": "du Val d'Anjou / Beaubien"
                    }
                },
                {
                    "stop": {
                        "id": 269623160,
                        "stop_id": "54638",
                        "stop_name": "du Val d'Anjou / de la Nantaise"
                    }
                },
                {
                    "stop": {
                        "id": 269623176,
                        "stop_id": "54656",
                        "stop_name": "Louis-Hippolyte-La Fontaine / de la Malicorne"
                    }
                },
                {
                    "stop": {
                        "id": 269623212,
                        "stop_id": "54693",
                        "stop_name": "Val d'Anjou / Place Val d'Anjou"
                    }
                },
                {
                    "stop": {
                        "id": 269623213,
                        "stop_id": "54694",
                        "stop_name": "Val d'Anjou / Place Val d'Anjou"
                    }
                }
            ],
            "route_text_color": "",
            "route_type": 3,
            "route_url": "http://www.stm.info/fr/infos/reseaux/bus"
        }
    ]
}
                """
            )
        server.enqueue(response)
        server.start()

        subject = TransitLandClient(transitLandAPI(server), configRepository)

        val actualResponse = subject.fromRef("r-f25ej-18")
        server.shutdown()

        assertThat(actualResponse.isSuccess).isTrue()
        val actualRoute = actualResponse.getOrNull()?.routes?.firstOrNull()
        assertNotNull(actualRoute)
        assertThat(actualRoute.shortName).isEqualTo("18")
        assertThat(actualRoute.longName).isEqualTo("Beaubien")
    }

    private fun transitLandAPI(server: MockWebServer): TransitLandAPI {
        return retrofitBuilder
            .baseUrl(server.url("/"))
            .build()
            .create(TransitLandAPI::class.java)
    }
}
