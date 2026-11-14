package et.sera.nativeapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Reconstructable cache only. Encrypted payloads; hashed account/item identifiers.
 * Never stores tokens, signed URLs, drafts or pending uploads. All disk operations use IO. */
internal class SeraLocalCache private constructor(context:Context):SQLiteOpenHelper(context,"sera_local_cache.db",null,1){
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("sera_cache_budget",Context.MODE_PRIVATE)
    private val alias="sera_local_cache_aes_v1"
    private val guard=Any()
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE snapshots (owner TEXT NOT NULL, bucket TEXT NOT NULL, item TEXT NOT NULL, written INTEGER NOT NULL, expires INTEGER NOT NULL, touched INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(owner,bucket,item))")
        db.execSQL("CREATE INDEX snapshots_lru ON snapshots(owner,touched)")
        db.execSQL("CREATE INDEX snapshots_expiry ON snapshots(expires)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("Unsupported cache schema migration")}
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun key():SecretKey{
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey(alias,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    private fun aad(o:String,b:String,i:String,w:Long,e:Long)="$o|$b|$i|$w|$e".toByteArray(Charsets.UTF_8)
    private fun seal(value:String,aad:ByteArray):ByteArray{
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(aad)
        return cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }
    private fun open(value:ByteArray,aad:ByteArray):String{
        require(value.size>=28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,value.copyOfRange(0,12)));cipher.updateAAD(aad)
        return String(cipher.doFinal(value.copyOfRange(12,value.size)),Charsets.UTF_8)
    }
    private fun args(owner:String,bucket:String,item:String)=arrayOf(hash(owner),bucket,hash(item))
    private val where="owner=? AND bucket=? AND item=?"
    fun read(owner:String,bucket:String,item:String,now:Long=System.currentTimeMillis()):String?=synchronized(guard){
        if(!SeraMessageAlerts.validId(owner))return@synchronized null
        val a=args(owner,bucket,item);val db=readableDatabase
        val result=db.query("snapshots",arrayOf("written","expires","payload"),where,a,null,null,null).use{c->
            if(!c.moveToFirst())return@use null
            val w=c.getLong(0);val e=c.getLong(1)
            if(now<w||now>e)null else runCatching{open(c.getBlob(2),aad(a[0],bucket,a[2],w,e))}.getOrNull()
        }
        if(result==null)db.delete("snapshots",where,a)
        else db.update("snapshots",ContentValues().apply{put("touched",now)},where,a)
        result
    }
    fun write(owner:String,bucket:String,item:String,value:String,now:Long,ttl:Long,maxEntries:Int):Boolean=synchronized(guard){
        if(!SeraMessageAlerts.validId(owner)||ttl<=0||maxEntries<1||value.toByteArray(Charsets.UTF_8).size>600000)return@synchronized false
        val a=args(owner,bucket,item);val expires=now+ttl
        val encrypted=seal(value,aad(a[0],bucket,a[2],now,expires))
        val db=writableDatabase;db.beginTransaction()
        try{
            val inserted=db.insertWithOnConflict("snapshots",null,ContentValues().apply{
                put("owner",a[0]);put("bucket",bucket);put("item",a[2]);put("written",now);put("expires",expires);put("touched",now);put("payload",encrypted)
            },SQLiteDatabase.CONFLICT_REPLACE)
            check(inserted!=-1L){"Cache insert failed"}
            // Keep newest records per bucket, and enforce a logical payload budget across all buckets.
            db.execSQL("DELETE FROM snapshots WHERE owner=? AND bucket=? AND item NOT IN (SELECT item FROM snapshots WHERE owner=? AND bucket=? ORDER BY touched DESC,written DESC,item LIMIT ?)",arrayOf(a[0],bucket,a[0],bucket,maxEntries))
            trim(db,now)
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
        true
    }
    private fun trim(db:SQLiteDatabase,now:Long){
        db.delete("snapshots","expires<?",arrayOf(now.toString()))
        val budget=budgetMb()*1024L*1024L
        // Globally bounded, including old account namespaces.
        val rows=mutableListOf<Pair<Long,Long>>()
        db.rawQuery("SELECT rowid,length(payload) FROM snapshots ORDER BY touched DESC,written DESC,rowid DESC",null).use{c->while(c.moveToNext())rows.add(c.getLong(0) to c.getLong(1))}
        var total=0L
        rows.forEach{(id,size)->total+=size;if(total>budget)db.delete("snapshots","rowid=?",arrayOf(id.toString()))}
    }
    fun remove(owner:String,bucket:String,item:String)=synchronized(guard){writableDatabase.delete("snapshots",where,args(owner,bucket,item));Unit}
    fun clear(owner:String,bucket:String?=null)=synchronized(guard){
        if(bucket==null)writableDatabase.delete("snapshots","owner=?",arrayOf(hash(owner)))
        else writableDatabase.delete("snapshots","owner=? AND bucket=?",arrayOf(hash(owner),bucket))
        Unit
    }
    fun budgetMb():Int=prefs.getInt("text_mb",30).takeIf{it in listOf(15,30,50)}?:30
    fun setBudgetMb(mb:Int)=synchronized(guard){
        require(mb in listOf(15,30,50));prefs.edit().putInt("text_mb",mb).apply()
        trim(writableDatabase,System.currentTimeMillis())
    }
    fun bytes(owner:String):Long=synchronized(guard){
        readableDatabase.rawQuery("SELECT coalesce(sum(length(payload)),0) FROM snapshots WHERE owner=?",arrayOf(hash(owner))).use{c->if(c.moveToFirst())c.getLong(0) else 0L}
    }
    companion object{
        @Volatile private var instance:SeraLocalCache?=null
        fun get(context:Context):SeraLocalCache=instance?:synchronized(this){instance?:SeraLocalCache(context.applicationContext).also{instance=it}}
    }
}
