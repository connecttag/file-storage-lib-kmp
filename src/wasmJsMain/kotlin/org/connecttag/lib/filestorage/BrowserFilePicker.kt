package org.connecttag.lib.filestorage

import kotlin.js.JsFun
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.io.encoding.Base64
import kotlinx.coroutines.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@JsFun("""(accept,multiple,maxBytes)=>new Promise((resolve,reject)=>{
 const input=document.createElement('input');input.type='file';input.accept=accept;input.multiple=multiple;input.style.display='none';document.body.appendChild(input);
 const finish=value=>{input.remove();resolve(value)};input.oncancel=()=>finish('[]');
 input.onchange=async()=>{try{ const result=await Promise.all(Array.from(input.files||[]).map(file=>new Promise((done,fail)=>{
 if(file.size>maxBytes){fail(new Error('FILE_TOO_LARGE'));return;}const reader=new FileReader();reader.onerror=()=>fail(reader.error);
 reader.onload=()=>done({name:file.name,mime:file.type,data:String(reader.result).split(',')[1]});reader.readAsDataURL(file);
 })));finish(JSON.stringify(result));}catch(e){input.remove();reject(e);}};input.click();
})""") private external fun pickFiles(accept: String,multiple: Boolean,maxBytes: Int): Promise<JsString>
@JsFun("() => crypto.randomUUID()") private external fun randomFileId(): String
@JsFun("() => Date.now()") private external fun browserNow(): Double
@JsFun("""(base64,name,mime)=>{const text=atob(base64),bytes=new Uint8Array(text.length);for(let i=0;i<text.length;i++)bytes[i]=text.charCodeAt(i);const url=URL.createObjectURL(new Blob([bytes],{type:mime}));const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);} """)
private external fun downloadFile(base64: String,name: String,mime: String)
@Serializable private data class Selection(val name: String,val mime: String,val data: String)
class BrowserFilePicker(private val storage: FileStorage,private val maxBytes: Int=20*1024*1024) : PlatformFilePicker {
    override suspend fun pick(request: PlatformFilePickerRequest): PlatformFilePickerResult = try {
        val files=Json.decodeFromString<List<Selection>>(pickFiles(request.mimeTypes.joinToString(","),request.allowMultiple,maxBytes).await().toString())
        if(files.isEmpty()) PlatformFilePickerResult.Cancelled else PlatformFilePickerResult.Selected(files.map {
            val id=FileResourceId("imports",randomFileId());val bytes=Base64.decode(it.data)
            storage.write(FileWriteRequest(id,bytes,contentType=it.mime,storedAtMillis=browserNow().toLong(),attributes=mapOf("fileName" to it.name)))
            PlatformPickedFile(PlatformFileReference(id.storagePath),id,it.name,it.mime,bytes.size.toLong())
        })
    } catch (cancelled: CancellationException) { throw cancelled } catch(error: Throwable) { PlatformFilePickerResult.Failed(error) }
}
fun downloadBrowserFile(bytes: ByteArray,name: String,mime: String) { downloadFile(Base64.encode(bytes),name,mime) }
