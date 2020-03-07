package com.hazelcast.jet.swisstrain.data

import com.hazelcast.client.config.ClientConfig
import com.hazelcast.jet.Jet
import com.hazelcast.jet.JetInstance
import com.hazelcast.jet.config.JobConfig
import com.hazelcast.jet.pipeline.Pipeline
import com.hazelcast.jet.pipeline.ServiceFactories
import com.hazelcast.jet.pipeline.Sinks
import com.hazelcast.jet.swisstrain.common.withCloseable

internal const val URL = "https://api.opentransportdata.swiss/gtfs-rt?format=JSON"

fun main() {
    Jet.newJetClient().withCloseable().use {
        it.newJob(pipeline(), jobConfig)
    }
}

private fun pipeline() = Pipeline.create().apply {
    val service = if (System.getProperty("mock") != null) mockService()
    else remoteService(URL, System.getProperty("token"))
    readFrom(service)
        .withTimestamps(TimestampExtractor, 200)
        .flatMap(SplitPayload)
        .mapUsingIMap("trips", TripIdExtractor, MergeWithTrip)
        .mapUsingService(
            ServiceFactories.iMapService("stop_times"),
            MergeWithStopTimes
        )
        .map(HourToTimestamp)
        .mapUsingService(
            ServiceFactories.iMapService("stops"),
            MergeWithLocation
        ).peek()
        .map(ToEntry)
        .writeTo(Sinks.remoteMap("update", clientConfig))
}

private val clientConfig = ClientConfig().apply {
    clusterName = "jet"
}

private val jobConfig = JobConfig()
    .addClass(
        SplitPayload::class.java,
        TripTraverser::class.java,
        FillBuffer::class.java,
        MockBuffer::class.java,
        CreateContext::class.java,
        CreateMockContext::class.java,
        TimeHolder::class.java,
        CountHolder::class.java,
        ToEntry::class.java,
        HourToTimestamp::class.java,
        TripIdExtractor::class.java,
        MergeWithTrip::class.java,
        MergeWithStopTimes::class.java,
        MergeWithLocation::class.java,
        TimestampExtractor::class.java
    )