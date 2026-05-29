package org.apache.guacamole.tunnel.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.EndpointConfig;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.tunnel.TunnelRequestService;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ServerEndpoint(value = "/websocket-tunnel", subprotocols = {"guacamole"})
public class GuacamoleWebSocketEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(GuacamoleWebSocketEndpoint.class);
    private static final int BUFFER_SIZE = 8192;

    private static volatile TunnelRequestService tunnelRequestService;

    public static void setTunnelRequestService(TunnelRequestService service) {
        tunnelRequestService = service;
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) throws IOException {
        if (tunnelRequestService == null) {
            logger.error("TunnelRequestService not initialized.");
            session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Service not ready"));
            return;
        }

        WebSocketTunnelRequest tunnelRequest = new WebSocketTunnelRequest(session.getRequestParameterMap());

        try {
            GuacamoleTunnel tunnel = tunnelRequestService.createTunnel(tunnelRequest);
            if (tunnel == null) {
                session.close(new jakarta.websocket.CloseReason(
                        jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION, "No tunnel created"));
                return;
            }

            session.getUserProperties().put("tunnel", tunnel);
            startReadThread(session, tunnel);

        } catch (GuacamoleException e) {
            logger.error("Failed to create WebSocket tunnel: {}", e.getMessage());
            session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage()));
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        GuacamoleTunnel tunnel = (GuacamoleTunnel) session.getUserProperties().get("tunnel");
        if (tunnel == null || !tunnel.isOpen())
            return;

        try {
            org.apache.guacamole.io.GuacamoleWriter writer = tunnel.acquireWriter();
            try {
                writer.write(message.toCharArray());
            } finally {
                tunnel.releaseWriter();
            }
        } catch (GuacamoleException e) {
            logger.debug("WebSocket tunnel write failed.", e);
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

    private void sendInstruction(Session session, GuacamoleInstruction instruction) throws IOException {
        // 在发送前检查 session 是否仍然打开，避免向已关闭的连接发送消息
        if (!session.isOpen()) {
            throw new IOException("WebSocket session is closed.");
        }
        synchronized (session.getBasicRemote()) {
            session.getBasicRemote().sendText(instruction.toString());
        }
    }

    private void sendInstruction(Session session, String instruction) throws IOException {
        // 在发送前检查 session 是否仍然打开，避免向已关闭的连接发送消息
        if (!session.isOpen()) {
            throw new IOException("WebSocket session is closed.");
        }
        synchronized (session.getBasicRemote()) {
            session.getBasicRemote().sendText(instruction);
        }
    }

    private void startReadThread(Session session, GuacamoleTunnel tunnel) {
        Thread readThread = new Thread(() -> {
            StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
            org.apache.guacamole.io.GuacamoleReader reader = tunnel.acquireReader();
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
                    if (session.isOpen()) {
                        session.close(new jakarta.websocket.CloseReason(
                                jakarta.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE, "Done"));
                    }
                } catch (GuacamoleException e) {
                    logger.debug("Error reading from tunnel.", e);
                    if (session.isOpen()) {
                        session.close(new jakarta.websocket.CloseReason(
                                jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage()));
                    }
                } catch (IllegalStateException e) {
                    // Session 已被关闭（客户端断开连接），无需进一步处理
                    logger.debug("WebSocket session closed, stopping read thread.", e);
                }
            } catch (IOException e) {
                logger.debug("I/O error in WebSocket read thread.", e);
            } finally {
                tunnel.releaseReader();
            }
        });
        readThread.setDaemon(true);
        readThread.start();
    }
}
