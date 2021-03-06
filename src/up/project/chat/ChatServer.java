package up.project.chat;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChatServer {
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = StandardCharsets.UTF_8;
    static private final CharsetDecoder decoder = charset.newDecoder();

    static private final Hashtable<SocketChannel, ClientInfo> clients = new Hashtable<>();
    static private final Hashtable<String, ClientInfo> nicks = new Hashtable<>();
    static private final Hashtable<String, HashSet<ClientInfo>> foruns = new Hashtable<>();

    @SuppressWarnings({"InfiniteLoopStatement", "ThrowablePrintedToSystemOut"})
    static public void main(String[] args) {
        // Parse port from command line
        int port = Integer.parseInt(args[0]);

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking(false);

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);

            while (true) {
                // See if we've had any activity -- either an incoming connection,
                // or incoming data on an existing connection
                int num = selector.select();

                // If we don't have any activity, loop around and wait again
                if (num == 0) {
                    continue;
                }

                // Get the keys corresponding to the activity that has been
                // detected, and process them one by one
                Set<SelectionKey> keys = selector.selectedKeys();

//                Iterator<SelectionKey> it = keys.iterator();
//                while (it.hasNext()) {
//                    SelectionKey key = it.next();
                // Get a key representing one of bits of I/O activity
                for (SelectionKey key : keys) {

                    // What kind of activity is it?
                    if (key.isAcceptable()) {

                        // It's an incoming connection.  Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking(false);

                        // Register it with the selector, for reading
                        sc.register(selector, SelectionKey.OP_READ);

                        // Add to the client table
                        clients.putIfAbsent(sc, new ClientInfo(sc));

                    } else if (key.isReadable()) {

                        SocketChannel sc = null;

                        try {

                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc, key);

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println("Closing connection to " + s);
                                    s.close();

                                    // Remove client from tables
                                    deleteClient(sc);

                                } catch (IOException ie) {
                                    System.err.println("Error closing socket " + s + ": " + ie);
                                }
                            }

                        } catch (IOException ie) {

                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            } catch (IOException ie2) {
                                System.out.println(ie2);
                            }

                            System.out.println("Closed " + sc);
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }


    private static void deleteClient(SocketChannel sc) {
        ClientInfo cc = clients.get(sc);
        if (cc != null) {
            switch (cc.getState()) {
                case ClientInfo.STATE_IN:
                    removeFromRoom(cc);

                case ClientInfo.STATE_OUT:
                    nicks.remove(cc.getNick());

                default:
                    clients.remove(sc);
            }
        }
    }

    private static void removeFromRoom(ClientInfo cc) {
        String forum = cc.getForum();

        if (forum != null) {
            cc.setForum(null);

            HashSet<ClientInfo> members = foruns.get(forum);

            //Remove member and delete forum if it is empty
            if (members.remove(cc) && members.isEmpty()) {
                foruns.remove(forum);

                return;
            }

            messageRoomAll(("LEFT " + cc.getNick() + "\n").getBytes(), forum);

        }

    }

    // Just read the message from the socket and send it to stdout
    static private boolean processInput(SocketChannel sc, SelectionKey key) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read(buffer);
        buffer.flip();

        // If no data, close the connection
        if (buffer.limit() == 0) {
            return false;
        }

        ClientInfo cc = clients.get(sc);
        if (cc == null)
            return false;

        // Decode and pass the message to client processor
        String message = decoder.decode(buffer).toString();
        cc.getDataBuffer().append(message);
        cc.process();

        processCommands(cc, key);

        return true;
    }

    private static void processCommands(ClientInfo cc, SelectionKey key) {
        Queue<String> commands = cc.getCommandQueue();
        while (!commands.isEmpty()) {
            String cmd = commands.poll();

            if (cmd.length() == 0) {
                continue;
            }

            if (cmd.startsWith("/nick ")) {
                tryGiveNick(cmd.substring(6), cc);
                continue;
            }
            if (cmd.startsWith("/join ")) {
                joinForum(cmd.substring(6), cc);
                continue;
            }
            if (cmd.startsWith("/leave")) {
                leaveForum(cc);
                continue;
            }
            if (cmd.startsWith("/bye")) {
                leaveChat(cc, key);
                return;
            }
            if (cmd.startsWith("/priv ")){
                sendPrivateMessage(cmd.substring(6), cc);
                continue;
            }
            if (cmd.startsWith("//")) {
                sendMessage(cmd.substring(1), cc);
                continue;
            }
            if (cmd.startsWith("/")) {
                commandError(cc);
                continue;
            }

            // Is a message
            if (cc.getState() != ClientInfo.STATE_IN) {
                commandError(cc);
                continue;
            }
            sendMessage(cmd, cc);

        }

    }

    private static void sendPrivateMessage(String cmd, ClientInfo cc) {
        int separate = cmd.indexOf(' ');
        if(separate == -1) {
            commandError(cc);
            return;
        }

        String dest = cmd.substring(0, separate);
        String msg = cmd.substring(separate +1);

        ClientInfo cd = nicks.get(dest);
        if (cd == null) {
            commandError(cc);
            return;
        }
        messageClient( ("PRIVATE "+cc.getNick()+" "+msg+"\n").getBytes(), cd);
        commandComplete(cc);
    }

    private static void sendMessage(String cmd, ClientInfo cc) {
        messageRoomAll(("MESSAGE " + cc.getNick() + " " + cmd + "\n").getBytes(), cc.getForum());
    }

    private static void leaveChat(ClientInfo cc, SelectionKey key) {
        messageClient(("BYE\n").getBytes(), cc);

        key.cancel();

        Socket s = null;
        SocketChannel sc = cc.getChannel();
        try {
            s = sc.socket();
            System.out.println("Closing connection to " + s);
            s.close();

            // Remove client from tables
            deleteClient(sc);

        } catch (IOException ie) {
            System.err.println("Error closing socket " + s + ": " + ie);
        }
    }

    private static void leaveForum(ClientInfo cc) {
        String forum = cc.getForum();
        if (forum == null) {
            commandError(cc);
            return;
        }

        removeFromRoom(cc);
        commandComplete(cc);
    }

    private static void joinForum(String new_forum, ClientInfo cc) {
        // Limit naming
        if (!isValidName(new_forum)){
            commandError(cc);
            return;
        }

        if (cc.getNick() == null) {
            commandError(cc);
            return;
        }

        removeFromRoom(cc);

        HashSet<ClientInfo> memebers = foruns.get(new_forum);
        if (memebers == null) {
            foruns.put(new_forum, new HashSet<>());
        } else {
            messageRoomAll(("JOINED " + cc.getNick() + "\n").getBytes(), new_forum);
        }

        foruns.get(new_forum).add(cc);
        cc.setForum(new_forum);

        commandComplete(cc);
    }

    private static void tryGiveNick(String new_nick, ClientInfo cc) {
        // Limit naming
        if (!isValidName(new_nick)){
            commandError(cc);
            return;
        }

        // Check availability or if we already have the nick
        ClientInfo cx = nicks.get(new_nick);
        if (cx != null) {
            if (!cx.equals(cc)) {
                commandError(cc);
            } else {
                commandComplete(cc);
            }
            return;
        }

        // Nick is available
        //   give it to user and tell forum (if state inside)
        String old_nick = cc.getNick();
        if (old_nick != null)
            nicks.remove(old_nick);
        nicks.put(new_nick, cc);
        cc.setNick(new_nick);

        String forum = cc.getForum();
        if (forum != null) {
            messageRoomExcept(("NEWNICK " + old_nick + " " + new_nick + "\n").getBytes(), forum, cc);
        }

        commandComplete(cc);
    }

    private static boolean isValidName(String new_name) {
        return !new_name.contains(" ");

        // TODO change from simple validating to a cleanup ann validation
        //  it is currently ERRORing '/join room ' bc of ending space...
    }

    private static void commandComplete(ClientInfo cc) {
        messageClient(("OK\n").getBytes(), cc);
    }

    private static void commandError(ClientInfo cc) {
        messageClient(("ERROR\n").getBytes(), cc);
    }

    private static void messageRoomAll(byte[] msg, String forum) {
        messageRoomExcept(msg, forum, null);
    }

    private static void messageRoomExcept(byte[] msg, String forum, ClientInfo exc) {
        for (ClientInfo member : foruns.get(forum)) {
            if (member.equals(exc)) continue;

            messageClient(msg, member);
        }
    }

    private static void messageClient(byte[] msg, ClientInfo cc) {
        try {
            buffer.clear();
            buffer.put(msg);
            buffer.flip();

            cc.getChannel().write(buffer);

        } catch (IOException e) {
            System.err.println("Error sending message ( " + Arrays.toString(msg) + " ) to " + cc.getNick() + " ( " + cc.getChannel() + " )");

        }
    }
}