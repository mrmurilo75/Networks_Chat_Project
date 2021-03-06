package up.project.chat;

import java.io.*;
import java.net.Socket;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas com a interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    static private BufferedWriter writeBuffer;
    static private Socket s;

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }


    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui

        s = new Socket(server, port);
        writeBuffer = new BufferedWriter( new OutputStreamWriter(s.getOutputStream()) );
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // Show in our client
        printMessage(message + '\n');

        // Send to server
        if (message.startsWith("/") && !(message.startsWith("/nick ") || message.startsWith("/join ") || message.startsWith("/leave") ||   message.startsWith("/bye") || message.startsWith("/priv "))) {
            message = "/" + message;
        }
        writeBuffer.write(message);
        writeBuffer.newLine();
        writeBuffer.flush();


    }


    // Método principal do objecto
    public void run() throws IOException {
        BufferedReader readerBuffer = new BufferedReader( new InputStreamReader(s.getInputStream()) );

        String response;

        while ((response = readerBuffer.readLine()) != null && s.isConnected() ) {
            printMessage(processResponse(response));
        }
    }

    private String processResponse(String response) {
        if (response.startsWith("JOINED ")) {
            return responseJoined(response.substring(7));
        }
        if (response.startsWith("LEFT ")) {
            return responseLeft(response.substring(5));
        }
        if (response.startsWith("MESSAGE ")) {
            return responseMessage(response.substring(8).split(" ", 2));
        }
        if (response.startsWith("NEWNICK ")) {
            return responseNewnick(response.substring(8).split(" ", 2));
        }
        if (response.startsWith("PRIVATE ")) {
            return responsePrivate(response.substring(8).split(" ", 2));
        }

        return response + "\n";
    }

    private String responsePrivate(String[] response) {
        try {
            return "(private) " + response[0] + ": " + response[1] + "\n";
        } catch (IndexOutOfBoundsException e) {
            return "(private) " + response[0] + ": \n";
        }
    }

    private String responseNewnick(String[] response) {
        try {
            return response[0] + " has changed to " + response[1] + "\n";
        } catch (IndexOutOfBoundsException e) {
            return response[0] + " has changed to \n";
        }
    }

    private String responseMessage(String[] response) {
        try {
            return response[0] + ": " + response[1] + "\n";
        } catch (IndexOutOfBoundsException e) {
            return response[0] + ": \n";
        }
    }

    private String responseLeft(String nick) {
        return nick + " has left\n";
    }

    private String responseJoined(String nick) {
        return nick + " has joined\n";
    }


    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
