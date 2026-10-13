package tv.musik

import kotlinx.coroutines.*

/** Share work between waiters; completed failures never poison future requests.
 * Accessed on the owning scope's serial dispatcher (Main in the player).
 */
class SingleFlight<K,V>(private val scope:CoroutineScope) {
    private val pending=mutableMapOf<K,Deferred<V>>()
    suspend fun await(key:K,block:suspend CoroutineScope.()->V):V {
        val job=pending[key]?.takeUnless { it.isCompleted } ?: scope.async(start=CoroutineStart.LAZY,block=block).also { deferred ->
            pending[key]=deferred
            deferred.invokeOnCompletion {
                scope.launch { if(pending[key]===deferred) pending.remove(key) }
            }
            deferred.start()
        }
        try { return job.await() }
        finally { if(job.isCompleted && pending[key]===job) pending.remove(key) }
    }
    fun clear() { val jobs=pending.values.toList();pending.clear();jobs.forEach { it.cancel() } }
}
