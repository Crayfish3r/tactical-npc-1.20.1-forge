package dev.zuma.tacticalnpc.api;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Fixed, persistent Bloom filter. Bits NEVER expire: false positives reject, never reopen an ID.
 * Hash layout is part of registry schema2. A full filter fails closed, requiring operator action. */
final class UsedMatchIds {
    private static final int WORDS=16384, MASK=WORDS*64-1, HASHES=7;
    private final long[] bits;
    UsedMatchIds(){bits=new long[WORDS];}
    private UsedMatchIds(long[] bits){this.bits=bits;}
    private static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    boolean contains(UUID id){
        long a=mix(id.getMostSignificantBits()),b=mix(id.getLeastSignificantBits())|1;
        for(int i=0;i<HASHES;i++){int bit=(int)(a+i*b)&MASK;if((bits[bit>>>6]&(1L<<(bit&63)))==0)return false;}return true;
    }
    boolean add(UUID id){
        boolean changed=false;long a=mix(id.getMostSignificantBits()),b=mix(id.getLeastSignificantBits())|1;
        for(int i=0;i<HASHES;i++){int bit=(int)(a+i*b)&MASK;long mask=1L<<(bit&63);if((bits[bit>>>6]&mask)==0){bits[bit>>>6]|=mask;changed=true;}}return changed;
    }
    private byte[] checksum(){
        try{var digest=MessageDigest.getInstance("SHA-256");var buffer=ByteBuffer.allocate(Long.BYTES);for(long word:bits){buffer.clear();buffer.putLong(word);digest.update(buffer.array());}return digest.digest();}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    void save(CompoundTag root){root.putLongArray("usedIds",bits);root.putByteArray("usedIdsSha256",checksum());}
    static UsedMatchIds load(CompoundTag root){
        if(!root.contains("usedIds",Tag.TAG_LONG_ARRAY) || root.getLongArray("usedIds").length!=WORDS || !root.contains("usedIdsSha256",Tag.TAG_BYTE_ARRAY))throw new IllegalStateException("tacticalnpc_matches schema 2: invalid used-ID filter");
        var data=new UsedMatchIds(root.getLongArray("usedIds"));
        if(!Arrays.equals(data.checksum(),root.getByteArray("usedIdsSha256")))throw new IllegalStateException("tacticalnpc_matches schema 2: used-ID checksum mismatch");return data;
    }
}
