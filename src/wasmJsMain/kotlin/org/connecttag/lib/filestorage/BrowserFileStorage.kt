package org.connecttag.lib.filestorage

import kotlin.js.JsFun
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlin.io.encoding.Base64
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

@JsFun("""(name,op,key,value) => new Promise((resolve,reject) => {
 const opening=indexedDB.open(name,1);
 opening.onupgradeneeded=()=>opening.result.createObjectStore('files');
 opening.onerror=()=>reject(opening.error);
 opening.onsuccess=()=>{ const db=opening.result; const tx=db.transaction('files',op==='get'||op==='list'?'readonly':'readwrite');
 const store=tx.objectStore('files'); let request;
 if(op==='get')request=store.get(key); else if(op==='list')request=store.getAll();
 else if(op==='put')request=store.put(value,key); else request=store.delete(key);
 let output=''; request.onsuccess=()=>{output=op==='list'?JSON.stringify(request.result.map(v=>JSON.parse(v))):op==='get'?(request.result||''):'';};
 tx.oncomplete=()=>{db.close();resolve(output);};tx.onerror=()=>{db.close();reject(tx.error);};tx.onabort=()=>{db.close();reject(tx.error);}; }
})""")
private external fun idb(name: String,op: String,key: String,value: String): Promise<JsString>

@Serializable private data class BrowserFileRecord(val metadata: FileResourceMetadata,val base64: String)

/** Persistent IndexedDB storage. Errors propagate instead of pretending a write succeeded. */
class BrowserFileStorage(private val databaseName: String) : FileStorage {
    private val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
    override suspend fun read(id: FileResourceId): StoredFile? {
        val value=idb(databaseName,"get",id.storagePath,"").await().toString()
        if(value.isEmpty())return null
        val record=json.decodeFromString<BrowserFileRecord>(value)
        return StoredFile(record.metadata,Base64.decode(record.base64))
    }
    override suspend fun write(request: FileWriteRequest): StoredFile {
        val metadata=FileResourceMetadata(request.id,request.bytes.size.toLong(),request.contentType,request.etag,request.lastModifiedAtMillis,request.storedAtMillis,request.expiresAtMillis,request.sourceUrl,request.attributes)
        idb(databaseName,"put",request.id.storagePath,json.encodeToString(BrowserFileRecord(metadata,Base64.encode(request.bytes)))).await()
        return StoredFile(metadata,request.bytes.copyOf())
    }
    override suspend fun metadata(id: FileResourceId)=read(id)?.metadata
    override suspend fun list(namespace: String?): List<FileResourceMetadata> =
        json.decodeFromString(ListSerializer(BrowserFileRecord.serializer()),idb(databaseName,"list","","").await().toString()).map { it.metadata }.filter { namespace==null||it.id.namespace==namespace }
    override suspend fun delete(id: FileResourceId): Boolean {
        if(metadata(id)==null)return false
        idb(databaseName,"delete",id.storagePath,"").await();return true
    }
    override suspend fun clear(namespace: String?): Int { val entries=list(namespace);entries.forEach { delete(it.id) };return entries.size }
}
