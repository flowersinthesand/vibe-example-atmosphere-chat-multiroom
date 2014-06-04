package org.atmosphere.vibe.samples.chat;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.atmosphere.vibe.Action;
import org.atmosphere.vibe.VoidAction;
import org.atmosphere.vibe.atmosphere.AtmosphereBridge;
import org.atmosphere.vibe.runtime.DefaultServer;
import org.atmosphere.vibe.runtime.Server;
import org.atmosphere.vibe.runtime.Socket;
import org.eclipse.jetty.util.ConcurrentHashSet;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebListener
public class Bootstrap implements ServletContextListener {
    
    // socket id : author name
    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();
    private final ConcurrentHashSet<String> rooms = new ConcurrentHashSet<>();
   
    // Temporary
    protected static Map<String, String> parseURI(String uri) {
        Map<String, String> map = new LinkedHashMap<>();
        String query = URI.create(uri).getQuery();
        if ((query == null) || (query.equals(""))) {
            return map;
        }

        String[] params = query.split("&");
        for (String param : params) {
            try {
                String[] pair = param.split("=", 2);
                String name = URLDecoder.decode(pair[0], "UTF-8");
                if (name == "") {
                    continue;
                }

                map.put(name, pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "");
            } catch (UnsupportedEncodingException e) {}
        }

        return Collections.unmodifiableMap(map);
    }
    
    @Override
    public void contextInitialized(ServletContextEvent event) {
        final Server server = new DefaultServer();
        server.socketAction(new Action<Socket>() {
            @Override
            public void on(final Socket socket) {
                final String room = parseURI(socket.uri()).get("room");
                rooms.add(room);
                
                socket.on("init", new Action<String>() {
                    @Override
                    public void on(String author) {
                        socket.tag(author);
                        users.put(socket.id(), author);
                        server.all().send("entered", new ChatProtocol(author, " entered room " + room, users.values(), rooms));
                    }
                })
                .on("private", new Action<Object>() {
                    @Override
                    public void on(Object object) {
                        UserMessage user = new ObjectMapper().convertValue(object, UserMessage.class);
                        server.byTag(user.getUser()).send("private", new ChatProtocol(user.getUser(), " sent you a private message: " + user.getMessage().split(":")[1], users.values(), rooms));
                    };
                })
                .on("public", new Action<Object>() {
                    @Override
                    public void on(Object object) {
                        ChatProtocol message = new ObjectMapper().convertValue(object, ChatProtocol.class);
                        message.setUsers(users.values());
                        server.all().send("entered", new ChatProtocol(message.getAuthor(), message.getMessage(), users.values(), rooms));
                    }
                })
                .on("close", new VoidAction() {
                    @Override
                    public void on() {
                        server.all().send("entered", new ChatProtocol(users.remove(socket.id()), " disconnected from room " + room, users.values(), rooms));
                    }
                });
            }
        });
        
        new AtmosphereBridge(event.getServletContext(), "/chat").httpAction(server.httpAction()).websocketAction(server.websocketAction());
    }
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {}
}
