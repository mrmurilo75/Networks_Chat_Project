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
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ChatServer
{
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

    // Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    static private final Hashtable<SocketChannel, ClientInfo> clients = new Hashtable<>();
    static private final TreeMap<String, ClientInfo> nicks = new TreeMap<>();
    static private final TreeMap<String, TreeSet<ClientInfo>> foruns = new TreeMap<>();

    static public void main( String args[] ) throws Exception {
        // Parse port from command line
        int port = Integer.parseInt( args[0] );

        try {
            // Instead of creating a ServerSocket, create a ServerSocketChannel
            ServerSocketChannel ssc = ServerSocketChannel.open();

            // Set it to non-blocking, so we can use select
            ssc.configureBlocking( false );

            // Get the Socket connected to this channel, and bind it to the
            // listening port
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress( port );
            ss.bind( isa );

            // Create a new Selector for selecting
            Selector selector = Selector.open();

            // Register the ServerSocketChannel, so we can listen for incoming
            // connections
            ssc.register( selector, SelectionKey.OP_ACCEPT );
            System.out.println( "Listening on port "+port );

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
                        System.out.println( "Got connection from "+s );

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking( false );

                        // Register it with the selector, for reading
                        sc.register( selector, SelectionKey.OP_READ );

                        // Add to the client table
                        clients.putIfAbsent(sc, new ClientInfo(sc));

                    } else if (key.isReadable()) {

                        SocketChannel sc = null;

                        try {

                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel)key.channel();
                            boolean ok = processInput( sc );

                            // If the connection is dead, remove it from the selector
                            // and close it
                            if (!ok) {
                                key.cancel();

                                Socket s = null;
                                try {
                                    s = sc.socket();
                                    System.out.println( "Closing connection to "+s );
                                    s.close();

                                    // Remove client from tables
                                    deleteClient(sc);

                                } catch( IOException ie ) {
                                    System.err.println( "Error closing socket "+s+": "+ie );
                                }
                            }

                        } catch( IOException ie ) {

                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            } catch( IOException ie2 ) { System.out.println( ie2 ); }

                            System.out.println( "Closed "+sc );
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        } catch( IOException ie ) {
            System.err.println( ie );
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

        if(forum != null) {
            TreeSet<ClientInfo> members = foruns.get(forum);

            //Remove member and delete forum if it is empty
            if (members.remove(cc) && members.isEmpty()){
                foruns.remove(forum);

                return;
            }

            messageRoomExcept( ("LEFT "+cc.getNick()+"\n").getBytes(), forum, null);

        }

    }

    // Just read the message from the socket and send it to stdout
    static private boolean processInput( SocketChannel sc ) throws IOException {
        // Read the message to the buffer
        buffer.clear();
        sc.read( buffer );
        buffer.flip();

        // If no data, close the connection
        if (buffer.limit()==0) {
            return false;
        }

        // Decode and print the message to stdout
        String message = decoder.decode(buffer).toString();
        try {
            clients.get(sc).getDataBuffer().append(message);
        } catch (NullPointerException e ) {
            return false;
        }

        return true;
    }


    private static void messageRoomExcept(byte[] msg, String forum, ClientInfo exc) {
        for( ClientInfo member : foruns.get(forum) ) {
            if (member != null && member.equals(exc)) continue;

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
            System.err.println("Error sending message ( "+msg+" ) to "+cc.getNick()+" ( "+cc.getChannel()+" )");

        }
    }
}