package et.sera.nativeapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object SeraNotificationAvatars{
    private val client=OkHttpClient.Builder().callTimeout(1500,TimeUnit.MILLISECONDS).followRedirects(false).followSslRedirects(false).build()
    private val cache=android.util.LruCache<String,Bitmap>(32)
    private var generation=0L
    @Synchronized fun clear(){generation++;cache.evictAll()}
    @Synchronized fun cached(owner:String,sender:String)=cache.get("$owner:$sender")
    fun initials(name:String):Bitmap{
        val image=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);canvas.drawColor(Color.rgb(8,126,131))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=46f;textAlign=Paint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD}
        val label=name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()}.ifBlank{"S"}
        canvas.drawText(label,64f,64f-(paint.ascent()+paint.descent())/2f,paint)
        return image
    }
    fun load(context:Context,owner:String,sender:String,url:String):Bitmap?{
        if(SeraPush.owner(context)!=owner)return null
        val stamp=synchronized(this){generation}
        val uri=runCatching{java.net.URI(url)}.getOrNull()?:return null
        val allowed=runCatching{java.net.URI(BuildConfig.SUPABASE_URL).host}.getOrNull()
        if(uri.scheme!="https"||uri.host!=allowed||!uri.path.startsWith("/storage/v1/object/sign/sera-account-private/"))return null
        cached(owner,sender)?.let{return it}
        val image=runCatching{
            client.newCall(Request.Builder().url(url).build()).execute().use{response->
                if(!response.isSuccessful)return null
                val body=response.body?:return null
                val bytes=body.byteStream().use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;if(out.size()+count>524288)return null;out.write(buffer,0,count)};out.toByteArray()}
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>8192||bounds.outHeight>8192)return null
                var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256)sample*=2
                val decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:return null
                Bitmap.createScaledBitmap(decoded,128,128,true)
            }
        }.getOrNull()?:return null
        synchronized(this){if(generation!=stamp||SeraPush.owner(context)!=owner)return null;cache.put("$owner:$sender",image)}
        return image
    }
}
