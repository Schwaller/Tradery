package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.UdpPeerConnection;
import com.tradery.sharing.sync.UdpPeerServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class UdpTransportTest {

    private ObjectMapper mapper;
    private UdpPeerServer serverA;
    private UdpPeerServer serverB;
    private BlockingQueue<UdpPeerConnection> incomingA;
    private BlockingQueue<UdpPeerConnection> incomingB;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        incomingA = new ArrayBlockingQueue<>(10);
        incomingB = new ArrayBlockingQueue<>(10);

        serverA = new UdpPeerServer(mapper, conn -> {
            incomingA.offer(conn);
            // Keep connection alive until test closes it
            while (!conn.isClosed()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
        serverA.start();
        serverB = new UdpPeerServer(mapper, conn -> {
            incomingB.offer(conn);
            while (!conn.isClosed()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
        serverB.start();
    }

    @AfterEach
    void tearDown() {
        if (serverA != null) serverA.close();
        if (serverB != null) serverB.close();
    }

    @Test
    void connect_and_helloExchange() throws Exception {
        // A connects to B
        UdpPeerConnection clientConn = serverA.connect("localhost", serverB.port(), 5000);
        assertNotNull(clientConn, "Client connection should succeed");

        // B should get an incoming connection
        UdpPeerConnection serverConn = incomingB.poll(5, TimeUnit.SECONDS);
        assertNotNull(serverConn, "Server should accept connection");

        // Exchange HELLO messages
        var hello = new NetworkMessage.Hello("peer-A", "device-A", null, null, List.of("doc-1"));
        clientConn.send(hello);

        NetworkMessage received = serverConn.receive();
        assertInstanceOf(NetworkMessage.Hello.class, received);
        assertEquals("peer-A", ((NetworkMessage.Hello) received).peerId());

        // Server sends back
        serverConn.send(new NetworkMessage.Hello("peer-B", "device-B", null, null, List.of("doc-1")));

        NetworkMessage reply = clientConn.receive();
        assertInstanceOf(NetworkMessage.Hello.class, reply);
        assertEquals("peer-B", ((NetworkMessage.Hello) reply).peerId());

        clientConn.close();
    }

    @Test
    void largeMessage_fragmentsAndReassembles() throws Exception {
        UdpPeerConnection clientConn = serverA.connect("localhost", serverB.port(), 5000);
        assertNotNull(clientConn);

        UdpPeerConnection serverConn = incomingB.poll(5, TimeUnit.SECONDS);
        assertNotNull(serverConn);

        // Create a SyncResponse with enough facts to exceed 1389 bytes (single fragment limit)
        var facts = new java.util.ArrayList<com.tradery.news.ui.coin.FactStore.Fact>();
        for (int i = 0; i < 50; i++) {
            facts.add(new com.tradery.news.ui.coin.FactStore.Fact(
                    "fact-" + i, "entity-" + i, "description",
                    "This is a moderately long value for testing fragmentation across UDP packets number " + i,
                    "user", "peer-A", i, System.currentTimeMillis(), "commit-" + i));
        }
        var largeMsg = new NetworkMessage.SyncResponse("doc-1", facts);

        // Verify it's actually large enough to fragment
        byte[] json = mapper.writeValueAsBytes(largeMsg);
        assertTrue(json.length > 1389,
                "Message should be large enough to require fragmentation (" + json.length + " bytes)");

        clientConn.send(largeMsg);

        NetworkMessage received = serverConn.receive();
        assertInstanceOf(NetworkMessage.SyncResponse.class, received);
        var resp = (NetworkMessage.SyncResponse) received;
        assertEquals("doc-1", resp.documentId());
        assertEquals(50, resp.facts().size());
        assertEquals("fact-0", resp.facts().getFirst().id());

        clientConn.close();
    }

    @Test
    void simultaneousConnect_atLeastOneSucceeds() throws Exception {
        // Both sides try to connect to each other at the same time
        var futureAtoB = CompletableFuture.supplyAsync(() ->
                serverA.connect("localhost", serverB.port(), 5000));
        var futureBtoA = CompletableFuture.supplyAsync(() ->
                serverB.connect("localhost", serverA.port(), 5000));

        UdpPeerConnection connAtoB = futureAtoB.get(10, TimeUnit.SECONDS);
        UdpPeerConnection connBtoA = futureBtoA.get(10, TimeUnit.SECONDS);

        // At least one should succeed (both typically do with simultaneous open)
        assertTrue(connAtoB != null || connBtoA != null,
                "At least one direction should connect");

        if (connAtoB != null) connAtoB.close();
        if (connBtoA != null) connBtoA.close();
    }

    @Test
    void multipleMessages_inSequence() throws Exception {
        UdpPeerConnection clientConn = serverA.connect("localhost", serverB.port(), 5000);
        assertNotNull(clientConn);

        UdpPeerConnection serverConn = incomingB.poll(5, TimeUnit.SECONDS);
        assertNotNull(serverConn);

        // Send multiple messages in sequence
        clientConn.send(new NetworkMessage.Hello("p1", null, null, null, List.of("doc-1")));
        clientConn.send(new NetworkMessage.SyncRequest("doc-1", 0));
        clientConn.send(new NetworkMessage.SyncDone("doc-1"));

        assertInstanceOf(NetworkMessage.Hello.class, serverConn.receive());
        assertInstanceOf(NetworkMessage.SyncRequest.class, serverConn.receive());
        assertInstanceOf(NetworkMessage.SyncDone.class, serverConn.receive());

        clientConn.close();
    }

    @Test
    void disconnect_closesCleanly() throws Exception {
        UdpPeerConnection clientConn = serverA.connect("localhost", serverB.port(), 5000);
        assertNotNull(clientConn);

        UdpPeerConnection serverConn = incomingB.poll(5, TimeUnit.SECONDS);
        assertNotNull(serverConn);

        // Client closes — server should see it
        clientConn.close();
        assertTrue(clientConn.isClosed());

        // Server receive should return null (disconnect)
        NetworkMessage msg = serverConn.receive();
        assertNull(msg, "Server should get null after client disconnect");
    }
}
