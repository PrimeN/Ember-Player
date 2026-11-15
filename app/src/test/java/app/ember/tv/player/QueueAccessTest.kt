package app.ember.tv.player

import org.junit.Assert.*
import org.junit.Test

class QueueAccessTest {
    private val files=listOf(QueueMedia("content://files/one","One"),QueueMedia("content://files/private","Private"),QueueMedia("content://files/three","Three"))
    private val public:(QueueMedia)->Boolean={it.title!="Private"}
    @Test fun navigationSkipsDisallowedFilesInBothDirections() {
        val queue=MediaQueue(files)
        assertEquals("Three",queue.next(accept=public)?.title);assertEquals(2,queue.index)
        assertEquals("One",queue.previous(public)?.title);assertEquals(0,queue.index)
    }
    @Test fun deniedNavigationDoesNotChangeTheCurrentSelection() {
        val queue=MediaQueue(files.take(2))
        assertFalse(queue.hasNext(public));assertNull(queue.next(accept=public));assertEquals(0,queue.index)
        queue.repeat="all"
        assertEquals("One",queue.next(accept=public)?.title)
        assertNull(queue.next(accept={false}));assertEquals(0,queue.index)
    }
    @Test fun repeatOneStillChecksAccessAndCanContinueToAnAllowedFile() {
        val queue=MediaQueue(files);queue.repeat="one";queue.select(1)
        assertEquals("Three",queue.next(ended=true,accept=public)?.title)
        assertEquals("Three",queue.next(ended=true,accept=public)?.title)
    }
}
