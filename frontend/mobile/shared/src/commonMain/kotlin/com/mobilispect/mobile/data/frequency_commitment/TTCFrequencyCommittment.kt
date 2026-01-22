package com.mobilispect.mobile.data.frequency_commitment

import com.mobilispect.mobile.agency.TTC_ID
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.minutes

// Source: https://ttc-cdn.azureedge.net/-/media/Project/TTC/DevProto/Images/Home/Routes-and-Schedules/Landing-page-pdfs/TTC_SystemMap_2021-11.pdf?rev=58232b2f280d4314b6435c79199af773
val item = FrequencyCommitmentItem(
    daysOfWeek = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
    ),
    frequency = 10.0.minutes,
    directions = DirectionTime.both(
        start = LocalTime(6, 0),
        end = LocalTime(1, 0)
    ),
    routes = listOf(
        "r-dpz8-1",
        "r-dpz8-2",
        "r-dpz8u-3",
        "r-dpz8c-4",
        "r-dpz8-7",
        "r-dpz87-22",
        "r-dpz8-24",
        "r-dpz8-25",
        "r-dpz8-29",
        "r-dpz2-32",
        "r-dpz8-34",
        "r-dpz2-35",
        "r-dpz2-36",
        "r-dpz9-39",
        "r-dpz95-43",
        "r-dpx2n-44",
        "r-dpz2t-45",
        "r-dpz2x-47",
        "r-dpz2-52",
        "r-dpz9-53",
        "r-dpz3-60",
        "r-dpz82-63",
        "r-dpz86-72",
        "r-dpz2n-76",
        "r-dpz2-84",
        "r-dpz9-85",
        "r-dpz8-86",
        "r-dpz8d-87",
        "r-dpz2x-89",
        "r-dpz83-94",
        "r-dpz8-95",
        "r-dpz2-96",
        "r-dpz8d-100",
        "r-dpz9-102",
        "r-dpz9j-116",
        "r-dpz9-129",
        "r-dpz2-165",
        "r-dpz8-501",
        "r-dpz83-504",
        "r-dpz8-505",
        "r-dpz8-506",
        "r-dpz83-509",
        "r-dpz83-510",
        "r-dpz83-511",
        "r-dpz82-512",
        "r-dpz2m-900",
        "r-dpz2-927",
    )
)

val TTC_FREQUENCY_COMMITMENT = FrequencyCommitment(
    spans = listOf(item),
    agency = TTC_ID
)
