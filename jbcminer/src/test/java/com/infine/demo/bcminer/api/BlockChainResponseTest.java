package com.infine.demo.bcminer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.TestHeader;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockChainResponseTest {

    @Test
    void testDeserialize() throws IOException {
        ObjectMapper objectMapper = BlockChainResponse.createObjectMapper();
        try (InputStream is = BlockChainResponseTest.class.getResourceAsStream("/block_239711.json")){
            if (is == null) throw new FileNotFoundException("classpath resource block_239711.json");
            byte[] bytes = is.readAllBytes();
            BlockChainResponse bcr = objectMapper.readValue(bytes, BlockChainResponse.class);
            BlockHeader blockHeader = bcr.createBlockHeader();
            assertEquals(TestHeader.TEST_HEADER_HEX, blockHeader.toString());
        }
    }

}