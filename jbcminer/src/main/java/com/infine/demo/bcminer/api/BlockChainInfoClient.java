package com.infine.demo.bcminer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infine.demo.bcminer.BlockHeader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * REST client for <a href="https://www.blockchain.com/explorer/api/blockchain_api">Blockchain Data API</a>
 */
public class BlockChainInfoClient {

    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public BlockChainInfoClient() {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        objectMapper = BlockChainResponse.createObjectMapper();
    }

    public String fetchLastHash() throws IOException, InterruptedException {
        try (InputStream is = fetch("https://blockchain.info/q/latesthash")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public BlockHeader fetchBlock(String hash) throws IOException, InterruptedException {
        try (InputStream is = fetch("https://blockchain.info/rawblock/" + hash)) {
            BlockChainResponse response = objectMapper.readValue(is, BlockChainResponse.class);
            return response.createBlockHeader();
        }
    }

    private InputStream fetch(String uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri))
                .header("content-type", "application/json; charset=utf-8")
                .build();
        HttpResponse<InputStream> response = client.send(request, b -> HttpResponse.BodySubscribers.ofInputStream());
        if (response.statusCode() != 200) {
            try (InputStream is = response.body()){
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException(response.statusCode() + "  : " + body);
            }
        }
        return response.body();
    }

}
