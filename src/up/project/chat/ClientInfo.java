package up.project.chat;

import java.nio.channels.SocketChannel;

public class ClientInfo {
    public static final byte STATE_INIT = 1;
    public static final byte STATE_OUT  = 2;
    public static final byte STATE_IN   = 3;

    private final SocketChannel channel;
    private String nick;
    private String forum;
    private StringBuffer dataBuffer;

    ClientInfo (SocketChannel channel) {
        this.channel = channel;
        this.nick = null;
        this.forum = null;
        this.dataBuffer = new StringBuffer(16384);
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
}
