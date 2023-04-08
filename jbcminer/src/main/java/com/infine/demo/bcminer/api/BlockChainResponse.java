package com.infine.demo.bcminer.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.Utils;

import static com.infine.demo.bcminer.BlockHeader.*;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY)
public class BlockChainResponse {
    public String hash;
    @JsonDeserialize(using = UnsignedIntDeserializer.class)
    public int ver;
    public String prev_block;
    public String mrkl_root;
    @JsonDeserialize(using = UnsignedIntDeserializer.class)
    public int time;
    @JsonDeserialize(using = UnsignedIntDeserializer.class)
    public int bits;
    @JsonDeserialize(using = UnsignedIntDeserializer.class)
    public int nonce;

    public BlockHeader createBlockHeader() {
        int[] data = new int[HEADER_INTS];
        data[VERSION] = Utils.flipEndianess(ver);
        Utils.parse(Utils.reverse(prev_block), data, PREV_HASH);
        Utils.parse(Utils.reverse(mrkl_root), data, MRKL_ROOT);
        data[TIME] = Utils.flipEndianess(time);
        data[NBITS] = Utils.flipEndianess(bits);
        data[NONCE] = Utils.flipEndianess(nonce);
        return new BlockHeader(data);
    }

    public static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return om;
    }
}
