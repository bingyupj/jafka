/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sohu.jafka.message;

import static java.lang.String.format;

import java.nio.ByteBuffer;

import com.sohu.jafka.api.ICalculable;
import com.sohu.jafka.common.UnknownMagicByteException;
import com.sohu.jafka.server.Server;
import com.sohu.jafka.utils.Utils;

/**
 * * A message. The format of an N byte message is the following:
 * 
 * <p>
 * magic byte is 1
 * 
 * <pre>
 * 1. 1 byte "magic" identifier to allow format changes
 * 2. 1 byte "attributes" identifier to allow annotations on the message
 * independent of the version (e.g. compression enabled, type of codec used)
 * 3. 4 byte CRC32 of the payload
 * 4. N - 6 byte payload
 * </pre>
 * 
 * </p>
 * 
 * @author adyliu (imxylz@gmail.com)
 * @since 1.0
 */
public class Message implements ICalculable {

    //expose message version number
    public static final byte MAGIC_VERSION2 = 1;

    /**
     * new message version providing message id
     * @author rockybean(smilingrockybean@gmail.com)
     */
    public static final byte MAGIC_VERSION_WITH_ID = 64;

    public static  byte CurrentMagicValue = MAGIC_VERSION_WITH_ID;

    public static final byte MAGIC_OFFSET = 0;

    public static final byte MAGIC_LENGTH = 1;

    public static final byte ATTRIBUTE_OFFSET = MAGIC_OFFSET + MAGIC_LENGTH;

    public static final byte ATTRIBUTE_LENGTH = 1;

    /* broker id in MAGIC_VERSION_WITH_ID*/
    public static final byte BROKER_ID_OFFSET = ATTRIBUTE_OFFSET + ATTRIBUTE_LENGTH;
    public static final byte BROKER_ID_LENGTH = 4;

    /* message id in MAGIC_VERSION_WITH_ID*/
    public static final byte MESSAGE_ID_OFFSET = BROKER_ID_OFFSET + BROKER_ID_LENGTH;
    public static final byte MESSAGE_ID_LENGTH = 8;
    public static final int MAGIC_VERSION_WITH_ID_MAGIC_LENGTH = BROKER_ID_LENGTH + MESSAGE_ID_LENGTH;

    public static final int MESSAGE_DATA_LENGTH = 4;

    /**
     * Specifies the mask for the compression code. 2 bits to hold the
     * compression codec. 0 is reserved to indicate no compression
     */
    public static final int CompressionCodeMask = 0x03; //

    public static final int NoCompression = 0;


    /**
     * Computes the CRC value based on the magic byte
     * 
     * @param magic Specifies the magic byte value. Possible values are 1
     *        (compression)
     */
    public static int crcOffset(byte magic) {
        //use if not switch because the later version is all based on MAGIC_VERSION_WITH_ID
        //So these method do not need change then
            if(magic == MAGIC_VERSION2)
                return ATTRIBUTE_OFFSET + ATTRIBUTE_LENGTH;
            if(magic >= MAGIC_VERSION_WITH_ID)
                return MESSAGE_ID_OFFSET + MESSAGE_ID_LENGTH;
        throw new UnknownMagicByteException(format("Magic byte value of %d is unknown", magic));
    }

    public static final byte CrcLength = 4;

    /**
     * Computes the offset to the message payload based on the magic byte
     * 
     * @param magic Specifies the magic byte value. Possible values are 0
     *        and 1 0 for no compression 1 for compression
     */
    public static int payloadOffset(byte magic) {
        if(magic == MAGIC_VERSION2)
            return msgDataLengthOffset(magic);
        if(magic >= MAGIC_VERSION_WITH_ID)
            return msgDataLengthOffset(magic) + MESSAGE_DATA_LENGTH;
        throw new  UnknownMagicByteException(format("unkown magic bytes %d",magic));
    }

    public static int msgDataLengthOffset(byte magic){
                return crcOffset(magic) + CrcLength;
    }

    /**
     * Computes the size of the message header based on the magic byte
     * 
     * @param magic Specifies the magic byte value. Possible values are 0
     *        and 1 0 for no compression 1 for compression
     */
    public static int headerSize(byte magic) {
        return payloadOffset(magic);
    }

    /**
     * Size of the header for magic byte 0. This is the minimum size of any
     * message header
     */
    public static final int MinHeaderSize = headerSize((byte) 1);

    ByteBuffer buffer;

    private final int messageSize;

    public Message(ByteBuffer buffer) {
        this.buffer = buffer;
        this.messageSize = buffer.limit();
    }

    public Message(int brokerId,long msgId,byte[] bytes){
        this(brokerId,msgId,Utils.crc32(bytes),bytes,CompressionCodec.NoCompressionCodec);
    }

    public Message(int brokerId,long msgId,byte[] bytes,CompressionCodec codec){
        this(brokerId,msgId,Utils.crc32(bytes),bytes,codec);
    }
    public Message(int brokerId,long msgId,long checksum,byte[] bytes,CompressionCodec compressionCodec){
        this(ByteBuffer.allocate(headerSize(CurrentMagicValue)+bytes.length));
        /*if(CurrentMagicValue < MAGIC_VERSION_WITH_ID) {
            throw new IllegalStateException("Current message version is too old to use this constructor!");
        }*/
        buffer.put(CurrentMagicValue);
        byte attr = 0;
        if(compressionCodec.codec > 0){
            attr = (byte)(attr | (compressionCodec.codec & CompressionCodeMask));
        }
        buffer.put(attr);
        if(CurrentMagicValue >= MAGIC_VERSION_WITH_ID){
            buffer.putInt(brokerId);
            buffer.putLong(msgId);
        }
        Utils.putUnsignedInt(buffer,checksum);
        //add msg data length
        if(CurrentMagicValue >= MAGIC_VERSION_WITH_ID){
            buffer.putInt(bytes.length);
        }
        buffer.put(bytes);
        buffer.rewind();
    }

    /**
     * new message version should add its extra data after current version.
     * You should reallocate buffer.
     * todo:complete this method
     * @param buffer
     * @return
     */
    public ByteBuffer appendExtraData(ByteBuffer buffer,byte[] bytes) {
        /*if(bytes != null || bytes.length > 0){

        }*/
        return buffer;
    }

    /**
     * construct message without partitionid and messageId
     * used by producer
     * @param checksum
     * @param bytes
     * @param compressionCodec
     */
    public Message(long checksum, byte[] bytes, CompressionCodec compressionCodec) {
        this(-1,-1L,checksum,bytes,compressionCodec);
    }

    public Message(long checksum, byte[] bytes) {
        this(checksum, bytes, CompressionCodec.NoCompressionCodec);
    }

    public Message(byte[] bytes, CompressionCodec compressionCodec) {
        this(Utils.crc32(bytes), bytes, compressionCodec);
    }

    /**
     * create no compression message
     * used by producer
     * 
     * @param bytes message data
     * @see CompressionCodec#NoCompressionCodec
     */
    public Message(byte[] bytes) {
        this(bytes, CompressionCodec.NoCompressionCodec);
    }

    //
    public int getSizeInBytes() {
        //return messageSize;
        return buffer.limit();
    }

    /**
     * magic code ( constant 1)
     * 
     * @return 1
     */
    public byte magic() {
        return buffer.get(MAGIC_OFFSET);
    }

    public int payloadSize() {
       if(magic() == MAGIC_VERSION2)
                return getSizeInBytes() - headerSize(magic());
       if(magic() >= MAGIC_VERSION_WITH_ID)
                return buffer.getInt(msgDataLengthOffset(magic()));
       throw new UnknownMagicByteException(format("unkown magic byte %d",magic()));
    }

    public byte attributes() {
        return buffer.get(ATTRIBUTE_OFFSET);
    }


    /**
     * return brokerId for MAGIC_VERSION_WITH_ID
     * @return
     */
    public int brokerId(){
        if(magic() < MAGIC_VERSION_WITH_ID){
            return -1;
        }
        return buffer.getInt(BROKER_ID_OFFSET);
    }


    /**
     * return messageId for MAGIC_VERSION_WITH_ID
     * @return
     */
    public long messageId(){
        if(magic() < MAGIC_VERSION_WITH_ID){
            return -1;
        }
        return buffer.getLong(MESSAGE_ID_OFFSET);
    }

    public MessageId getMessageId(){
        long id = messageId();
        if(magic() < MAGIC_VERSION_WITH_ID || id == -1){
            return null;
        }
        return new MessageId(id);
    }

    public CompressionCodec compressionCodec() {
        byte magicByte = magic();
        switch (magicByte) {
            case 0:
                return CompressionCodec.NoCompressionCodec;
            default:
                return CompressionCodec.valueOf(buffer.get(ATTRIBUTE_OFFSET) & CompressionCodeMask);
        }
        //throw new RuntimeException("Invalid magic byte " + magicByte);
    }

    public long checksum() {
        return Utils.getUnsignedInt(buffer, crcOffset(magic()));
    }

    /**
     * get the real data without message header
     * @return message data(without header)
     */
    public ByteBuffer payload() {
        ByteBuffer payload = buffer.duplicate();
        payload.position(headerSize(magic()));
        payload = payload.slice();
        payload.limit(payloadSize());
        payload.rewind();
        return payload;
    }

    public boolean isValid() {
        return checksum() == Utils.crc32(buffer.array(), buffer.position() + buffer.arrayOffset() + payloadOffset(magic()), payloadSize());
    }

    public int serializedSize() {
        return 4 /* int size */+ buffer.limit();
    }

    public void serializeTo(ByteBuffer serBuffer) {
        serBuffer.putInt(buffer.limit());
        serBuffer.put(buffer.duplicate());
    }

    //
    @Override
    public String toString() {
        return format("message(magic = %d, attributes = %d, crc = %d, payload = %s,messageId=[%s])",//
                magic(), attributes(), checksum(), payload(),getMessageId());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message) {
            Message m = (Message) obj;
            return getSizeInBytes() == m.getSizeInBytes()//
                    && attributes() == m.attributes()//
                    && checksum() == m.checksum()//
                    && payload() == m.payload()//
                    && magic() == m.magic();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return buffer.hashCode();
    }

}
