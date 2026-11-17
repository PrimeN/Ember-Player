package app.ember.tv.player

import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class)
class MediaLibraryTest {
    @Test fun skippedPrivateRowsDoNotHideTheRemainderOfTheIndex() {
        val db=database();val folder=DeviceFolder("content://device.files/tree/root","Root")
        val root=java.security.MessageDigest.getInstance("SHA-256").digest(folder.uri.toByteArray()).joinToString(""){"%02x".format(it)}
        MediaLibrary(app,{db},{listOf(folder)},{it.endsWith("/private")}).use {index->
            for(i in 0 until 220)db.execSQL("INSERT INTO media VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("id-$i",root,"content://device.files/tree/root/document/"+(if(i<30)"$i/private" else "$i"),"clip-%03d".format(i),"video/mp4",100L,i.toLong(),"generation"))
            val first=index.search("")
            assertEquals(100,first.entries.size);assertNotNull(first.nextOffset)
            val second=index.search("",first.nextOffset!!)
            assertEquals(90,second.entries.size);assertNull(second.nextOffset)
        }
    }
    private val app:Context get()=RuntimeEnvironment.getApplication()
    private fun database():SupportSQLiteDatabase=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(app)
        .callback(object:SupportSQLiteOpenHelper.Callback(1){override fun onCreate(db:SupportSQLiteDatabase){};override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int){}}).build()).writableDatabase
    @Test fun refreshSearchPrivacyAndRemovedFilesUseTheSameIndex() {
        val provider=DeviceTestProvider();provider.attachInfo(app,ProviderInfo().apply {authority="device.files";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        ShadowContentResolver.registerProviderInternal("device.files",provider)
        val roots=mutableListOf(DeviceFolder("content://device.files/tree/root","Root"))
        var protectVideo=false
        MediaLibrary(app,{database()},{roots},{protectVideo&&it.endsWith("/video")}).use {index->
            index.refresh(roots,{}, {})
            assertEquals(1,index.search("video").entries.size)
            assertTrue(index.search("notes").entries.isEmpty())
            protectVideo=true;assertTrue(index.search("video").entries.isEmpty())
            protectVideo=false
            provider.children=listOf("hidden")
            index.refresh(roots,{}, {})
            assertTrue(index.search("video").entries.isEmpty())
            assertEquals(1,index.search("hidden").entries.size)
            roots.clear();assertTrue(index.search("").entries.isEmpty())
        }
    }
    @Test fun interruptedRefreshInvalidatesAnyCatalogLoadedBeforePartialChanges() {
        MediaLibrary(app,{database()},{emptyList()},{false}).use {index->
            val revision=MediaLibrary.revision.get()
            index.refresh(emptyList(),{}, {})
            assertEquals(revision+1,MediaLibrary.revision.get())
            try {
                index.refresh(listOf(DeviceFolder("content://device.files/tree/root","Root")),{throw kotlinx.coroutines.CancellationException()}, {})
                fail("Expected cancellation")
            } catch(_:kotlinx.coroutines.CancellationException) {}
            assertEquals(revision+2,MediaLibrary.revision.get())
        }
    }
    @Test fun providerWithoutOffsetSupportStillPagesAllRows() {
        val provider=DeviceTestProvider();provider.attachInfo(app,ProviderInfo().apply {authority="device.files";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        ShadowContentResolver.registerProviderInternal("device.files",provider)
        provider.children=List(451){"video"}
        val tree=Uri.parse("content://device.files/tree/root");val root=DeviceFiles.root(tree)
        val first=DeviceFiles.list(app,tree,root);val second=DeviceFiles.list(app,tree,root,first.nextOffset);val last=DeviceFiles.list(app,tree,root,second.nextOffset)
        assertEquals(200,first.entries.size);assertEquals(200,second.entries.size);assertEquals(51,last.entries.size)
        assertFalse(last.limited)
    }
    @Test fun searchTermsCannotInjectFtsSyntax() {
        assertEquals("\"hello\"* \"OR\"* \"世界\"*",MediaLibrary.terms("hello OR (世界*)\""))
        assertEquals("",MediaLibrary.terms("* - \""))
    }
    @Test fun sortingUsesTheWholeIndexAndKeepsStablePages() {
        val db=database()
        val folder=DeviceFolder("content://device.files/tree/root","Root")
        val root=java.security.MessageDigest.getInstance("SHA-256").digest(folder.uri.toByteArray()).joinToString(""){"%02x".format(it)}
        MediaLibrary(app,{db},{listOf(folder)},{false}).use {index->
            for(i in 0..204)db.execSQL("INSERT INTO media VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("id-$i",root,"content://device.files/tree/root/document/$i","clip-%03d".format(i),"video/mp4",100L,i.toLong(),"generation"))
            val first=index.search("clip",sort="modified")
            assertEquals("clip-204",first.entries.first().title)
            assertEquals(204L,first.modified[first.entries.first().key])
            val second=index.search("clip",first.nextOffset!!,"modified")
            assertEquals("clip-104",second.entries.first().title)
            assertTrue(first.entries.map {it.key}.toSet().intersect(second.entries.map {it.key}.toSet()).isEmpty())
            assertEquals("clip-000",index.search("clip",sort="name").entries.first().title)
            val last=index.search("clip",second.nextOffset!!,"modified")
            assertEquals(5,last.entries.size);assertNull(last.nextOffset)
        }
    }
    @Test fun naturalOrderingIsAppliedBeforePaginationAndUpdatesWhenFilesChange() {
        val db=database();val folder=DeviceFolder("content://device.files/tree/root","Root")
        val root=java.security.MessageDigest.getInstance("SHA-256").digest(folder.uri.toByteArray()).joinToString(""){"%02x".format(it)}
        MediaLibrary(app,{db},{listOf(folder)},{false}).use {index->
            for(i in 0..204)db.execSQL("INSERT INTO media VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("id-$i",root,"content://device.files/tree/root/document/$i","Episode $i","video/mp4",100L,i.toLong(),"generation"))
            val first=index.search("Episode");val second=index.search("Episode",first.nextOffset!!)
            assertEquals((0..99).map {"Episode $it"},first.entries.map {it.title})
            assertEquals((100..199).map {"Episode $it"},second.entries.map {it.title})
            db.execSQL("DELETE FROM media WHERE id='id-0'")
            db.execSQL("INSERT INTO media VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("id-0",root,"content://device.files/tree/root/document/0","Episode 1000","video/mp4",100L,0L,"generation"))
            assertEquals("Episode 1",index.search("Episode").entries.first().title)
        }
    }

}
