package ca.mcgill.science.tepid.server.queue

import ca.mcgill.science.tepid.models.data.FullDestination
import ca.mcgill.science.tepid.models.data.PrintJob

interface QueueLogic {
    /**
     * Unique identifier
     */
    val name: String

    /**
     * Returns the queue decision for the given job and the given options
     */
    fun getResult(job: PrintJob, destinations: List<FullDestination>): QueueResult?

    companion object {
        const val FIFTY_FIFTY = "FiftyFifty"

        fun create(name: String?): QueueLogic = when (name) {
            FIFTY_FIFTY -> FiftyFifty()
            else -> FiftyFifty()
        }
    }
}

/**
 * Base class for [QueueLogic] implementations
 * [name] should typically reference a constant string held by [QueueLogic]
 * to keep all the keys in one place
 */
private abstract class QueueLogicBase(final override val name: String) : QueueLogic {

    final override fun getResult(job: PrintJob, destinations: List<FullDestination>): QueueResult? {
        val destination = getDestination(job, destinations) ?: return null
        val eta = getEta(job, destination)
        return QueueResult(destination.name, eta)
    }

    abstract fun getDestination(job: PrintJob, destinations: List<FullDestination>): FullDestination?

    abstract fun getEta(job: PrintJob, destination: FullDestination): Long
}

private class FiftyFifty : QueueLogicBase(QueueLogic.FIFTY_FIFTY) {

    private var lastVisited = -1

    override fun getDestination(job: PrintJob, destinations: List<FullDestination>): FullDestination? {
        /*
         * We will start 1 after out last visited position,
         * and continue going up until the entire list count
         * We will stop once the destination and the given index is up
         */
        val index = (1..destinations.size)
                .map { (it + lastVisited) % destinations.size }
                .first { destinations[it].up }

        lastVisited = index

        return destinations[index]
    }

    override fun getEta(job: PrintJob, destination: FullDestination): Long {
        return System.currentTimeMillis() + 10000 + (job.pages * 2500) // todo update
    }

}