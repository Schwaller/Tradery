package com.tradery.sharing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradery.news.ui.coin.FactStore;
import com.tradery.sharing.sync.NetworkMessage;
import com.tradery.sharing.sync.PeerConnection;
import com.tradery.sharing.sync.PeerServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class PeerConnectionTest {

    private ObjectMapper mapper;
    private PeerServer server;
    private BlockingQueue<PeerConnection> serverConnections;

    @BeforeEach
    void setUp() throws IOException {
        mapper = new ObjectMapper();
        serverConnections = new ArrayBlockingQueue<>(10);
        server = new PeerServer(mapper, conn -> {
            serverConnections.offer(conn);
            // Keep connection alive until test closes it
            while (!conn.isClosed()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private PeerConnection connectClient() throws IOException {
        Socket socket = new Socket("localhost", server.port());
        return new PeerConnection(socket, mapper);
    }

    @Test
    void helloMessage_roundTrips() throws Exception {
        try (PeerConnection client = connectClient()) {
            var hello = new NetworkMessage.Hello("peer-1", "pubkey-base64", "token-123",
                    List.of("doc-a", "doc-b"));
            client.send(hello);

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);
            NetworkMessage received = serverConn.receive();

            assertInstanceOf(NetworkMessage.Hello.class, received);
            var receivedHello = (NetworkMessage.Hello) received;
            assertEquals("peer-1", receivedHello.peerId());
            assertEquals("pubkey-base64", receivedHello.publicKey());
            assertEquals("token-123", receivedHello.token());
            assertEquals(List.of("doc-a", "doc-b"), receivedHello.documentIds());
        }
    }

    @Test
    void syncRequest_roundTrips() throws Exception {
        try (PeerConnection client = connectClient()) {
            var req = new NetworkMessage.SyncRequest("doc-1", 42);
            client.send(req);

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);
            NetworkMessage received = serverConn.receive();

            assertInstanceOf(NetworkMessage.SyncRequest.class, received);
            var receivedReq = (NetworkMessage.SyncRequest) received;
            assertEquals("doc-1", receivedReq.documentId());
            assertEquals(42, receivedReq.sinceLclock());
        }
    }

    @Test
    void syncResponse_withFacts_roundTrips() throws Exception {
        try (PeerConnection client = connectClient()) {
            var fact = new FactStore.Fact("f1", "e1", "name", "Bitcoin",
                    "user", "peer-A", 10, 1700000000000L, "commit-1");
            var resp = new NetworkMessage.SyncResponse("doc-1", List.of(fact));
            client.send(resp);

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);
            NetworkMessage received = serverConn.receive();

            assertInstanceOf(NetworkMessage.SyncResponse.class, received);
            var receivedResp = (NetworkMessage.SyncResponse) received;
            assertEquals("doc-1", receivedResp.documentId());
            assertEquals(1, receivedResp.facts().size());
            assertEquals("Bitcoin", receivedResp.facts().getFirst().value());
            assertEquals("peer-A", receivedResp.facts().getFirst().peerId());
        }
    }

    @Test
    void syncDone_roundTrips() throws Exception {
        try (PeerConnection client = connectClient()) {
            var done = new NetworkMessage.SyncDone("doc-1");
            client.send(done);

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);
            NetworkMessage received = serverConn.receive();

            assertInstanceOf(NetworkMessage.SyncDone.class, received);
            assertEquals("doc-1", ((NetworkMessage.SyncDone) received).documentId());
        }
    }

    @Test
    void memberUpdate_roundTrips() throws Exception {
        try (PeerConnection client = connectClient()) {
            var update = new NetworkMessage.MemberUpdate("doc-1", List.of(
                    new NetworkMessage.MemberUpdate.MemberEntry("user-1", "OWNER"),
                    new NetworkMessage.MemberUpdate.MemberEntry("user-2", "MEMBER")
            ));
            client.send(update);

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);
            NetworkMessage received = serverConn.receive();

            assertInstanceOf(NetworkMessage.MemberUpdate.class, received);
            var receivedUpdate = (NetworkMessage.MemberUpdate) received;
            assertEquals("doc-1", receivedUpdate.documentId());
            assertEquals(2, receivedUpdate.members().size());
            assertEquals("user-1", receivedUpdate.members().getFirst().userId());
        }
    }

    @Test
    void multipleMessages_inSequence() throws Exception {
        try (PeerConnection client = connectClient()) {
            client.send(new NetworkMessage.Hello("p1", null, null, List.of("doc-1")));
            client.send(new NetworkMessage.SyncRequest("doc-1", 0));
            client.send(new NetworkMessage.SyncDone("doc-1"));

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);

            assertInstanceOf(NetworkMessage.Hello.class, serverConn.receive());
            assertInstanceOf(NetworkMessage.SyncRequest.class, serverConn.receive());
            assertInstanceOf(NetworkMessage.SyncDone.class, serverConn.receive());
        }
    }

    @Test
    void bidirectionalCommunication() throws Exception {
        try (PeerConnection client = connectClient()) {
            // Client sends hello
            client.send(new NetworkMessage.Hello("client-peer", null, null, List.of("doc-1")));

            PeerConnection serverConn = serverConnections.poll(5, TimeUnit.SECONDS);
            assertNotNull(serverConn);

            // Server receives and sends back
            NetworkMessage received = serverConn.receive();
            assertInstanceOf(NetworkMessage.Hello.class, received);

            serverConn.send(new NetworkMessage.Hello("server-peer", null, null, List.of("doc-1", "doc-2")));

            // Client receives server's response
            NetworkMessage serverHello = client.receive();
            assertInstanceOf(NetworkMessage.Hello.class, serverHello);
            assertEquals("server-peer", ((NetworkMessage.Hello) serverHello).peerId());
        }
    }
}
