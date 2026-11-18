package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class ShuffleQueueTest {
    private fun media(name:String)=QueueMedia("content://media/$name",name)
    private fun remaining(queue:MediaQueue):List<String> = buildList {
        while(true)add(queue.next()?.title ?: break)
    }
    private fun shuffled():Pair<MediaQueue,List<String>> {
        val queue=MediaQueue((0..5).map {media("item$it")});queue.shuffle=true
        val order=listOf(queue.current!!.title)+remaining(queue)
        queue.select(0)
        return queue to order
    }
    @Test fun queueOccurrencesSurviveEditsWithoutConfusingRepeatedFiles() {
        val same=media("same");val queue=MediaQueue(listOf(same,same,media("other")))
        val second=queue.occurrence(1)!!
        queue.move(1,2)
        assertEquals(2,queue.indexOfOccurrence(second))
        queue.select(queue.indexOfOccurrence(second));assertEquals(1,queue.originalIndex())
        queue.select(0);assertTrue(queue.remove(queue.indexOfOccurrence(second)))
        assertEquals(-1,queue.indexOfOccurrence(second));assertEquals(same,queue.items.first())
        queue.add(listOf(same));queue.select(2);assertNull(queue.originalIndex())
    }
    @Test fun addingKeepsPlayedHistoryAndUpcomingOrder() {
        val (queue,order)=shuffled()
        queue.next();queue.next()
        queue.add(listOf(media("new")))
        assertEquals(order[1],queue.previous()!!.title)
        assertEquals(order[0],queue.previous()!!.title)
        assertNull(queue.previous())
        assertEquals(order.drop(1)+"new",remaining(queue))
    }
    @Test fun movingVisibleRowsKeepsShuffledTraversalAndCurrentItem() {
        val (queue,order)=shuffled()
        queue.next();queue.next();val current=queue.current
        queue.move(queue.index,5);queue.move(0,4)
        assertSame(current,queue.current)
        assertEquals(order[1],queue.previous()!!.title)
        assertEquals(order.drop(2),remaining(queue))
    }
    @Test fun removingAnUpcomingEntryDoesNotReintroducePlayedItems() {
        val (queue,order)=shuffled();queue.next();queue.next()
        queue.remove(queue.items.indexOfFirst {it.title==order[4]})
        assertEquals(order[1],queue.previous()!!.title)
        assertEquals(order.drop(2).filter {it!=order[4]},remaining(queue))
    }
    @Test fun previousWrapsWithRepeatAllAndSkipsIneligibleEntries() {
        val queue=MediaQueue(listOf(media("a"),media("b"),media("c")))
        assertNull(queue.previous());queue.repeat="all"
        assertEquals("b",queue.previous {it.title!="c"}!!.title)
        assertEquals("c",queue.next()!!.title)
        assertEquals("a",queue.next()!!.title)
        assertNull(queue.previous {false})
    }
    @Test fun repeatedMediaKeepTheirSeparatePlacesAfterAnEdit() {
        val item=media("same");val queue=MediaQueue(listOf(item,item,media("other")))
        queue.shuffle=true;queue.move(0,2)
        assertEquals(2,remaining(queue).size)
    }
}
