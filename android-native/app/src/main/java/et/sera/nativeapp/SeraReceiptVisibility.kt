package et.sera.nativeapp

/** At least half the item (or half the viewport for a very tall message) must be visible. */
internal fun isReceiptVisible(offset:Int,size:Int,start:Int,end:Int):Boolean{
    if(size<=0||end<=start)return false
    val visible=(minOf(offset+size,end)-maxOf(offset,start)).coerceAtLeast(0)
    return visible*2>=minOf(size,end-start)
}
