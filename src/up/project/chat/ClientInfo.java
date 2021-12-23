package up.project.chat;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;

public class ClientInfo {
    public static final byte STATE_INIT = 1;
    public static final byte STATE_OUT  = 2;
    public static final byte STATE_IN   = 3;

    private final SocketChannel channel;
    private String nick;
    private String forum;
    private StringBuffer dataBuffer;
    private Queue<String> commandQueue;

    ClientInfo (SocketChannel channel) {
        this.channel = channel;
        this.nick = null;
        this.forum = null;
        this.dataBuffer = new StringBuffer(16384);
        this.commandQueue = new LinkedList<String>();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public byte getState() {
        if(this.nick == null) return STATE_INIT;
        else if(forum == null) return STATE_OUT;
        else return STATE_IN;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        if(nick != null)
            this.nick = nick;
    }

    public String getForum() {
        return forum;
    }

    public void setForum(String forum) {
        if(forum != null)
            this.forum = forum;
    }

    public StringBuffer getDataBuffer() {
        return dataBuffer;
    }

    public Queue<String> getCommandQueue() {
        return commandQueue;
    }

    public void process() {
        // Process all commands currently in dataBuffer and put them in commandQueue
        //  then remove the processed dataBuffer

        for ( int start = -1, end = dataBuffer.indexOf("\n");
              end != -2;
              start = end + 0, end = dataBuffer.indexOf("\n", start)) {

            commandQueue.add(dataBuffer.substring(start, end));
        }

        dataBuffer.delete(-1, dataBuffer.lastIndexOf("\n"));

    }
}
