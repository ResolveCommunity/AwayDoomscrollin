package com.resolvecommunity.awaydoomscrollin

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class LatestObservationDispatcherTest {
    private class Queue : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun run() { tasks.removeFirst().run() }
    }

    @Test fun slowDetectionDoesNotOccupyTheOwnerAndResultsReturnToIt() {
        val worker = Queue(); val owner = Queue(); val received = mutableListOf<Int>()
        var reads = 0; var tapHandled = false
        val dispatcher = LatestObservationDispatcher(worker, owner, { ++reads }, { received.add(it.getOrThrow()) })
        dispatcher.request()
        owner.execute { tapHandled = true }
        owner.run()
        assertTrue(tapHandled)
        assertEquals(0, reads)
        worker.run()
        assertTrue(received.isEmpty())
        owner.run()
        assertEquals(listOf(1), received)
    }

    @Test fun contentStormQueuesOnlyOneFollowUpWithoutStarvingValidResults() {
        val worker = Queue(); val owner = Queue(); val received = mutableListOf<Int>()
        var reads = 0
        val dispatcher = LatestObservationDispatcher(worker, owner, { ++reads }, { received.add(it.getOrThrow()) })
        dispatcher.request()
        repeat(1000) { dispatcher.request() }
        assertEquals(1, worker.tasks.size)
        worker.run(); owner.run()
        assertEquals(listOf(1), received)
        assertEquals(1, worker.tasks.size)
        worker.run(); owner.run()
        assertEquals(listOf(1, 2), received)
        assertTrue(worker.tasks.isEmpty())
    }

    @Test fun navigationDiscardsAnEarlierObservationAndReadsTheLatestState() {
        val worker = Queue(); val owner = Queue(); val received = mutableListOf<String>()
        var screen = "Explore"
        val dispatcher = LatestObservationDispatcher(worker, owner, { screen }, { received.add(it.getOrThrow()) })
        dispatcher.request(); worker.run()
        screen = "Search"
        dispatcher.request(invalidate = true)
        owner.run()
        assertTrue(received.isEmpty())
        worker.run(); owner.run()
        assertEquals(listOf("Search"), received)
    }

    @Test fun leavingAndReenteringCannotApplyThePreviousVisit() {
        val worker = Queue(); val owner = Queue(); val received = mutableListOf<Int>()
        var reads = 0
        val dispatcher = LatestObservationDispatcher(worker, owner, { ++reads }, { received.add(it.getOrThrow()) })
        dispatcher.request(); worker.run()
        dispatcher.cancel(); dispatcher.request()
        owner.run(); worker.run(); owner.run()
        assertEquals(listOf(2), received)
    }

    @Test fun closeDropsInFlightResultsAndDoesNotRestartPendingWork() {
        val worker = Queue(); val owner = Queue(); var calls = 0
        val dispatcher = LatestObservationDispatcher(worker, owner, { 1 }, { calls++ })
        dispatcher.request(); dispatcher.request()
        dispatcher.close(); dispatcher.request()
        worker.run(); owner.run()
        assertEquals(0, calls)
        assertTrue(worker.tasks.isEmpty())
    }

    @Test fun failuresReturnToTheOwnerAndDoNotStrandTheDispatcher() {
        val worker = Queue(); val owner = Queue(); val received = mutableListOf<Result<Int>>()
        var fail = true
        val dispatcher = LatestObservationDispatcher(worker, owner,
            { if (fail) error("Synthetic read failure") else 7 }, { received.add(it) })
        dispatcher.request(); worker.run(); owner.run()
        assertTrue(received.single().isFailure)
        fail = false
        dispatcher.request(); worker.run(); owner.run()
        assertEquals(7, received.last().getOrThrow())
    }

    @Test fun requestingFromTheResultCallbackCannotCreateParallelReads() {
        val worker = Queue(); val owner = Queue(); var reads = 0
        lateinit var dispatcher: LatestObservationDispatcher<Int>
        dispatcher = LatestObservationDispatcher(worker, owner, { ++reads }, {
            if (it.getOrThrow() == 1) dispatcher.request()
        })
        dispatcher.request(); dispatcher.request()
        worker.run(); owner.run()
        assertEquals(1, worker.tasks.size)
        worker.run(); owner.run()
        assertEquals(2, reads)
        assertTrue(worker.tasks.isEmpty())
    }

    @Test fun anAlreadySupersededTaskDoesNotEvenStartItsRead() {
        val worker = Queue(); val owner = Queue(); var reads = 0
        val dispatcher = LatestObservationDispatcher(worker, owner, { ++reads }, { })
        dispatcher.request()
        val old = dispatcher.generation
        dispatcher.request(invalidate = true)
        assertFalse(dispatcher.isCurrent(old))
        worker.run(); owner.run()
        assertEquals(0, reads)
        worker.run(); owner.run()
        assertEquals(1, reads)
    }
}
