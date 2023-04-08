package com.infine.demo.bcminer.api;

import com.infine.demo.bcminer.BlockHeader;
import com.infine.demo.bcminer.TestHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BlockChainInfoClientTest {

    private BlockChainInfoClient client;

    @BeforeEach
    public void setup() {
        client = new BlockChainInfoClient();
    }

    @Test
    void fetchLastHash() throws IOException, InterruptedException {
        String lastHash = client.fetchLastHash();
        assertEquals(lastHash.length(), 8 * 4 * 2); // 8 ints * 4 bytes * 2 chars
    }

    @Test
    void fetchBlock() throws IOException, InterruptedException {
        BlockHeader blockHeader = client.fetchBlock("00000000000001272c7eb572d183c9b8da350b1835b78d3f56cc07c082d78a5c");
        assertEquals(TestHeader.TEST_HEADER_HEX, blockHeader.toString());
    }

}