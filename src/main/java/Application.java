import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Application {
    private final static Logger LOGGER = Logger.getLogger(Application.class.getName());
    private final static String FTP_HOST = "ftp.freebsd.org";
    private final static int FTP_PORT = 21;

    public static void main(String[] args) throws IOException, InterruptedException {
        Socket data = null;
        try (Socket socket = new Socket(FTP_HOST, FTP_PORT);
             BufferedWriter out =
                     getWriter(socket);
             BufferedReader in = getReader(socket)
             ) {
            LOGGER.info(readAnswer(in));
            send("USER anonymous", out, in);
            send("PASS ivol@inbox.ru", out, in);
            /*
             *  PASV command returns IP address and encoded port.
             *  We should use created socked to get data
             */
            data = createSocketForPassiveMode(send("PASV", out, in));
            // We send command to "command" socket. And get restponse from "data" socket
            send("LIST", out, getReader(data));
            data.close();
            send("QUIT", out, in);
        } finally {
            if (data != null && !data.isClosed()) {
                data.close();
            }
        }
    }

    /**
     * Create "data" socket from "PASV" response.
     * Response example: INFO: 227 Entering Passive Mode (213,138,116,78,239,147).
     * We should parse numbers. First 4 numbers is IP. To get port we calculate: elem[5] * 256 + elem[6]
     */
    private static Socket createSocketForPassiveMode(String passiveResponse) throws IOException {
        Pattern pattern = Pattern.compile("\\((.*)\\)");
        Matcher matches = pattern.matcher(passiveResponse);
        if (!matches.find(1)) {
            throw new RuntimeException("Cannot proceed response");
        }
        String[] split = matches.group(1).split(",");
        String ip = "";
        for (int i = 0; i < 4; i++) {
            if (!ip.isEmpty()) {
                ip += ".";
            }
            ip += split[i];
        }
        int port = Integer.parseInt(split[4]) * 256 + Integer.parseInt(split[5]);
        return new Socket(ip, port);
    }

    private static String send(String command, BufferedWriter out, BufferedReader in) throws IOException, InterruptedException {
        LOGGER.info("Sending " + command);
        out.write(command +"\r\n");
        out.flush();
        String response = readAnswer(in);
        LOGGER.info(response);
        return response;
    }

    /**
     * By RFC: Multiline response will be in format [code]-[message].
     * Last line/single line is: [code] [message]
     */
    private static String readAnswer(BufferedReader in) throws IOException, InterruptedException {
        String str;
        StringBuilder builder = new StringBuilder();
        char delimeter = ' ';
        do {
            String line = in.readLine();
            delimeter = line.charAt(3);
            builder.append(line + "\n");
        } while (delimeter == '-');
        return builder.toString();
    }

    private static BufferedReader getReader(Socket socket) throws IOException {
        return new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
    }

    private static BufferedWriter getWriter(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }
}