package com.infine.demo.bcminer;

public class TestHeader {

    public static final String TEST_HEADER_HEX = "020000000affed3fc96851d8c74391c2d9333168fe62165eb228bced7e000000000000004277b65e3bd527f0ceb5298bdb06b4aacbae8a4a808c2c8aa414c20f252db801130dae516461011a3aeb9bb8";
    public static final BlockHeader TEST_HEADER = BlockHeader.parse(TEST_HEADER_HEX);
    public static final int EXPECTED_NONCE = TEST_HEADER.nonce(); // 3097226042

}
