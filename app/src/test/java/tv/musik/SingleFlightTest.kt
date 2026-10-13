package tv.musik

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SingleFlightTest {
    @Test fun concurrentWaitersShareOneRequest()=runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val resolver=SingleFlight<String,Int>(owner)
            val gate=CompletableDeferred<Unit>();var calls=0
            val a=async(start=CoroutineStart.UNDISPATCHED) { resolver.await("song") { calls++;gate.await();42 } }
            val b=async(start=CoroutineStart.UNDISPATCHED) { resolver.await("song") { calls++;0 } }
            gate.complete(Unit)
            assertEquals(42,a.await());assertEquals(42,b.await());assertEquals(1,calls)
        } finally { owner.cancel() }
    }
    @Test fun failedBackgroundWorkDoesNotFailTheNextPlaybackAttempt()=runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val resolver=SingleFlight<String,Int>(owner)
            val gate=CompletableDeferred<Unit>()
            val abandoned=launch(start=CoroutineStart.UNDISPATCHED) { resolver.await("song") { gate.await();error("temporary network failure") } }
            abandoned.cancelAndJoin()
            gate.complete(Unit)
            assertEquals(42,resolver.await("song") { 42 })
        } finally { owner.cancel() }
    }
    @Test fun clearingCancelsPendingWorkAndAllowsFreshRequests()=runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val resolver=SingleFlight<String,Int>(owner)
            val gate=CompletableDeferred<Unit>()
            val abandoned=async(start=CoroutineStart.UNDISPATCHED) { resolver.await("song") { gate.await();0 } }
            resolver.clear()
            try { abandoned.await();fail("Expected cancellation") } catch(_:CancellationException) {}
            assertEquals(42,resolver.await("song") { 42 })
        } finally { owner.cancel() }
    }
}
