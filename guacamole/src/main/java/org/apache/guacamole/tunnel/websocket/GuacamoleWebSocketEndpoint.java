package org.apache.guacamole.tunnel.websocket;

import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.EndpointConfig;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleConnectionClosedException;
import org.apache.guacamole.io.GuacamoleReader;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.protocol.FilteredGuacamoleWriter;
import org.apache.guacamole.protocol.GuacamoleFilter;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.apache.guacamole.protocol.GuacamoleStatus;
import org.apache.guacamole.tunnel.TunnelRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@ServerEndpoint(value = "/websocket-tunnel", subprotocols = {"guacamole"})
public class GuacamoleWebSocketEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(GuacamoleWebSocketEndpoint.class);
    private static final int BUFFER_SIZE = 8192;

    /**
     * The opcode of the instruction used to indicate a connection stability
     * test ping request or response. Note that this instruction is
     * encapsulated within an internal tunnel instruction (with the opcode
     * being the empty string), thus this will actually be the value of the
     * first element of the received instruction.
     */
    private static final String PING_OPCODE = "ping";

    private static volatile TunnelRequestService tunnelRequestService;

    public static void setTunnelRequestService(TunnelRequestService service) {
        tunnelRequestService = service;
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) throws IOException {
        if (tunnelRequestService == null) {
            logger.error("TunnelRequestService not initialized.");
            closeConnection(session, GuacamoleStatus.SERVER_ERROR.getGuacamoleStatusCode(),
                    GuacamoleStatus.SERVER_ERROR.getWebSocketCode());
            return;
        }

        WebSocketTunnelRequest tunnelRequest = new WebSocketTunnelRequest(session.getRequestParameterMap());

        try {
            GuacamoleTunnel tunnel = tunnelRequestService.createTunnel(tunnelRequest);
            if (tunnel == null) {
                closeConnection(session, GuacamoleStatus.RESOURCE_NOT_FOUND);
                return;
            }

            session.getUserProperties().put("tunnel", tunnel);
            startReadThread(session, tunnel);

        } catch (GuacamoleException e) {
            logger.error("Creation of WebSocket tunnel to guacd failed: {}", e.getMessage());
            logger.debug("Error connecting WebSocket tunnel.", e);
            closeConnection(session, e.getStatus().getGuacamoleStatusCode(),
                    e.getWebSocketCode());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        GuacamoleTunnel tunnel = (GuacamoleTunnel) session.getUserProperties().get("tunnel");
        if (tunnel == null || !tunnel.isOpen())
            return;

        // Filter received instructions, handling tunnel-internal instructions
        // without passing through to guacd. This matches the behavior of the
        // original GuacamoleWebSocketTunnelEndpoint.
        GuacamoleFilter filter = new GuacamoleFilter() {
            @Override
            public GuacamoleInstruction filter(GuacamoleInstruction instruction)
                    throws GuacamoleException {

                // Filter out all tunnel-internal instructions
                if (instruction.getOpcode().equals(GuacamoleTunnel.INTERNAL_DATA_OPCODE)) {

                    // Respond to ping requests to keep the connection alive
                    List<String> args = instruction.getArgs();
                    if (args.size() >= 2 && args.get(0).equals(PING_OPCODE)) {
                        try {
                            sendInstruction(session, new GuacamoleInstruction(
                                GuacamoleTunnel.INTERNAL_DATA_OPCODE,
                                PING_OPCODE, args.get(1)
                            ));
                        } catch (IOException e) {
                            logger.debug("Unable to send \"ping\" response for WebSocket tunnel.", e);
                        }
                    }

                    // Never pass internal instructions through to guacd
                    return null;
                }

                // Pass through all non-internal instructions untouched
                return instruction;
            }
        };

        try {
            // Write received message through filter
            FilteredGuacamoleWriter writer = new FilteredGuacamoleWriter(tunnel.acquireWriter(), filter);
            writer.write(message.toCharArray());
        } catch (GuacamoleConnectionClosedException e) {
            logger.debug("Connection to guacd closed.", e);
        } catch (GuacamoleException e) {
            logger.debug("WebSocket tunnel write failed.", e);
        } finally {
            tunnel.releaseWriter();
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.debug("WebSocket error: {}", error.getMessage());
        GuacamoleTunnel tunnel = (GuacamoleTunnel) session.getUserProperties().get("tunnel");
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (GuacamoleException e) {
                logger.debug("Error closing tunnel after error.", e);
            }
        }
    }

    @OnClose
    public void onClose(Session session) {
        GuacamoleTunnel tunnel = (GuacamoleTunnel) session.getUserProperties().get("tunnel");
        if (tunnel != null) {
            try {
                tunnel.close();
            } catch (GuacamoleException e) {
                logger.debug("Error closing tunnel.", e);
            }
        }
    }

    /**
     * Sends the numeric Guacamole Status Code and WebSocket code and closes
     * the connection.
     *
     * @param session
     *     The outbound WebSocket connection to close.
     *
     * @param guacamoleStatusCode
     *     The numeric Guacamole status to send.
     *
     * @param webSocketCode
     *     The numeric WebSocket status to send.
     */
    private void closeConnection(Session session, int guacamoleStatusCode,
            int webSocketCode) {
        try {
            CloseReason.CloseCode code = CloseReason.CloseCodes.getCloseCode(webSocketCode);
            String message = Integer.toString(guacamoleStatusCode);
            session.close(new CloseReason(code, message));
        } catch (IOException e) {
            logger.debug("Unable to close WebSocket connection.", e);
        }
    }

    /**
     * Sends the given Guacamole Status and closes the given connection.
     *
     * @param session
     *     The outbound WebSocket connection to close.
     *
     * @param guacStatus
     *     The status to use for the connection.
     */
    private void closeConnection(Session session, GuacamoleStatus guacStatus) {
        closeConnection(session, guacStatus.getGuacamoleStatusCode(),
                guacStatus.getWebSocketCode());
    }

    private void sendInstruction(Session session, GuacamoleInstruction instruction)
            throws IOException {
        if (!session.isOpen())
            throw new IOException("WebSocket session is closed.");
        synchronized (session.getBasicRemote()) {
            session.getBasicRemote().sendText(instruction.toString());
        }
    }

    private void sendInstruction(Session session, String instruction)
            throws IOException {
        if (!session.isOpen())
            throw new IOException("WebSocket session is closed.");
        synchronized (session.getBasicRemote()) {
            session.getBasicRemote().sendText(instruction);
        }
    }

    private void startReadThread(Session session, GuacamoleTunnel tunnel) {
        Thread readThread = new Thread(() -> {
            StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
            GuacamoleReader reader = tunnel.acquireReader();
            char[] readMessage;

            try {
                // Send tunnel UUID (matching original: empty opcode + UUID argument)
                sendInstruction(session, new GuacamoleInstruction(
                        GuacamoleTunnel.INTERNAL_DATA_OPCODE,
                        tunnel.getUUID().toString()
                ));

                try {
                    while (session.isOpen() && (readMessage = reader.read()) != null) {
                        buffer.append(readMessage);
                        if (!reader.available() || buffer.length() >= BUFFER_SIZE) {
                            sendInstruction(session, buffer.toString());
                            buffer.setLength(0);
                        }
                    }
                    // No more data
                    if (session.isOpen()) {
                        closeConnection(session, GuacamoleStatus.SUCCESS);
                    }
                } catch (GuacamoleClientException e) {
                    logger.info("WebSocket connection terminated: {}", e.getMessage());
                    logger.debug("WebSocket connection terminated due to client error.", e);
                    closeConnection(session, e.getStatus().getGuacamoleStatusCode(),
                            e.getWebSocketCode());
                } catch (GuacamoleConnectionClosedException e) {
                    logger.debug("Connection to guacd closed.", e);
                    closeConnection(session, GuacamoleStatus.SUCCESS);
                } catch (GuacamoleException e) {
                    logger.error("Connection to guacd terminated abnormally: {}", e.getMessage());
                    logger.debug("Internal error during connection to guacd.", e);
                    closeConnection(session, e.getStatus().getGuacamoleStatusCode(),
                            e.getWebSocketCode());
                }

            } catch (IOException e) {
                logger.debug("I/O error prevents further reads.", e);
                closeConnection(session, GuacamoleStatus.SERVER_ERROR);
            } finally {
                tunnel.releaseReader();
            }
        });
        readThread.setDaemon(true);
        readThread.start();
    }
}
