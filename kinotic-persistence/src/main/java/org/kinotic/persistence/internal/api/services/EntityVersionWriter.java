package org.kinotic.persistence.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.RawJson;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Writes the version an entity is at into its documents on their way out to a caller, as {@code primaryTerm:seqNo}
 * under the entity's version field. For an entity without a version field a document is returned as it is.
 */
public class EntityVersionWriter {

    // the version field's name as it opens a member whose value is a string: "name":"
    private final byte[] memberStart;

    /**
     * @param versionFieldName the entity's version field, or null for an entity without one
     */
    public EntityVersionWriter(JsonMapper jsonMapper, String versionFieldName) {
        this.memberStart = versionFieldName != null
                ? (jsonMapper.writeValueAsString(versionFieldName) + ":\"").getBytes(StandardCharsets.UTF_8)
                : null;
    }

    /**
     * Returns the document that was read, carrying the given version as its last field.
     *
     * @param document    a document read without a value under the version field
     * @param primaryTerm the primary term of the document's last write
     * @param seqNo       the sequence number of the document's last write
     * @return the document with its version, or the document itself for an entity without a version field
     */
    public EntityDocument write(EntityDocument document, Long primaryTerm, Long seqNo) {
        return memberStart != null ? new EntityDocument(write(document.json(), primaryTerm, seqNo), document.tenantId())
                                   : document;
    }

    /**
     * Returns the document with the given version as its last field.
     *
     * @param document    a json object that holds no value under the version field
     * @param primaryTerm the primary term of the document's last write
     * @param seqNo       the sequence number of the document's last write
     * @return the document with its version, or the document itself for an entity without a version field
     */
    public RawJson write(RawJson document, Long primaryTerm, Long seqNo) {
        RawJson ret;
        if(memberStart == null){
            ret = document;
        }else{
            byte[] data = document.data();
            int close = lastContentIndex(data, data.length - 1);
            int lastContent = lastContentIndex(data, close - 1);
            Validate.isTrue(lastContent >= 0 && data[close] == '}', "An entity's document must be a json object");
            boolean empty = data[lastContent] == '{';

            // made of digits and a colon, so it is written without escaping
            byte[] version = (primaryTerm + ":" + seqNo).getBytes(StandardCharsets.US_ASCII);

            byte[] versioned = new byte[lastContent + 1 + (empty ? 0 : 1) + memberStart.length + version.length + 2];
            int position = lastContent + 1;
            System.arraycopy(data, 0, versioned, 0, position);
            if(!empty){
                versioned[position++] = ',';
            }
            System.arraycopy(memberStart, 0, versioned, position, memberStart.length);
            position += memberStart.length;
            System.arraycopy(version, 0, versioned, position, version.length);
            position += version.length;
            versioned[position++] = '"';
            versioned[position] = '}';
            ret = new RawJson(versioned);
        }
        return ret;
    }

    // The documents this is given are written without whitespace, but a mapper set to indent its output adds some
    private static int lastContentIndex(byte[] data, int from) {
        int index = from;
        while(index >= 0 && (data[index] == ' ' || data[index] == '\n' || data[index] == '\r' || data[index] == '\t')){
            index--;
        }
        return index;
    }
}
