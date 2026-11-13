package et.sera.nativeapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Keep picker callbacks and camera lifecycle outside the chat screen's data-flow graph. */
@Composable
internal fun SeraNativeAttachmentPicker(
    token:String,userId:String,conversationId:String?,open:Boolean,onDismiss:()->Unit,onChecking:(Boolean)->Unit,
    onSelected:(NativeChatSelection)->Unit,onAlbum:(List<NativeChatSelection>)->Unit,onShare:(String)->Unit,onError:(String)->Unit
){
    var shareMode by remember(conversationId){mutableStateOf<SeraAttachmentKind?>(null)}
    shareMode?.let{kind->SeraShareDialog(kind,userId,onDismiss={shareMode=null},onAdd={onShare(it);shareMode=null})}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val currentConversation by rememberUpdatedState(conversationId)
    val selectedCallback by rememberUpdatedState(onSelected)
    val albumCallback by rememberUpdatedState(onAlbum)
    val errorCallback by rememberUpdatedState(onError)
    val checkingCallback by rememberUpdatedState(onChecking)
    var cameraPath by rememberSaveable{mutableStateOf<String?>(null)}
    var cameraConversation by rememberSaveable{mutableStateOf<String?>(null)}
    var pickerConversation by rememberSaveable{mutableStateOf<String?>(null)}

    fun readSelection(uri:Uri?,owner:String?){
        if(uri==null||owner==null||owner!=currentConversation)return
        checkingCallback(true)
        scope.launch{
            try{
                val result=withContext(Dispatchers.IO){selectChatFile(context,uri)}
                if(currentConversation==owner)selectedCallback(result)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(currentConversation==owner)errorCallback(e.message?:"Could not read attachment.")}
            finally{checkingCallback(false)}
        }
    }
    val documents=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val owner=pickerConversation;pickerConversation=null;readSelection(uri,owner)
    }
    val photos=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)){uris->
        val owner=pickerConversation;pickerConversation=null
        if(uris.isNotEmpty()&&owner!=null&&owner==currentConversation){
            checkingCallback(true)
            scope.launch{
                try{
                    require(uris.size<=4){"Select up to four photos."}
                    val selected=withContext(Dispatchers.IO){uris.map{selectChatFile(context,it).also{f->require(f.mime.startsWith("image/")){"Choose supported photos."}}}}
                    if(currentConversation==owner){if(selected.size==1)selectedCallback(selected.first())else albumCallback(selected)}
                }catch(e:CancellationException){throw e}catch(e:Exception){if(currentConversation==owner)errorCallback(e.message?:"Could not prepare photos.")}
                finally{checkingCallback(false)}
            }
        }
    }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
        val path=cameraPath;val owner=cameraConversation
        cameraPath=null;cameraConversation=null
        if(path!=null){
            val file=File(path)
            if(!ok||owner!=currentConversation){file.delete()}
            else{
                checkingCallback(true)
                scope.launch{
                    try{
                        val result=withContext(Dispatchers.IO){try{prepareCapturedChatPhoto(file)}finally{file.delete()}}
                        if(currentConversation==owner)selectedCallback(result)
                    }catch(e:CancellationException){throw e}
                    catch(e:Exception){if(currentConversation==owner)errorCallback(e.message?:"Could not prepare photo.")}
                    finally{checkingCallback(false)}
                }
            }
        }
    }
    if(open&&conversationId!=null)SeraAttachmentStudio(onDismiss=onDismiss,onChoose={kind->
        onDismiss()
        try{
            when(kind){
                SeraAttachmentKind.PHOTO->{pickerConversation=conversationId;photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
                SeraAttachmentKind.PROFESSIONAL,SeraAttachmentKind.LOCATION,SeraAttachmentKind.CONTACT->shareMode=kind
                SeraAttachmentKind.CV->{val owner=conversationId;checkingCallback(true);scope.launch{try{val file=withContext(Dispatchers.IO){readProfileCvForChat(token,userId)};if(currentConversation==owner)selectedCallback(file)}catch(e:CancellationException){throw e}catch(e:Exception){if(currentConversation==owner)errorCallback(e.message?:"Could not load profile CV.")}finally{checkingCallback(false)}}}
                SeraAttachmentKind.VIDEO->{pickerConversation=conversationId;documents.launch(arrayOf("video/mp4"))}
                SeraAttachmentKind.DOCUMENT->{pickerConversation=conversationId;documents.launch(arrayOf("application/pdf"))}
                SeraAttachmentKind.AUDIO->{pickerConversation=conversationId;documents.launch(arrayOf("audio/mp4","audio/webm","audio/ogg","audio/mpeg"))}
                SeraAttachmentKind.CAMERA->{
                    val directory=File(context.cacheDir,"chat_capture").apply{mkdirs()}
                    val file=File.createTempFile("sera-photo-",".jpg",directory)
                    try{
                        val uri=FileProvider.getUriForFile(context,context.packageName+".chatfiles",file)
                        cameraPath=file.absolutePath;cameraConversation=conversationId;camera.launch(uri)
                    }catch(e:Exception){file.delete();cameraPath=null;cameraConversation=null;throw e}
                }
            }
        }catch(_:Exception){pickerConversation=null;errorCallback("No compatible app is available.")}
    })
}
