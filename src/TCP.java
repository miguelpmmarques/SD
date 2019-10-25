import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

class TCP_CLIENT implements Runnable {
    Thread t;
    int tcpServerPort;
    MessageByTCP messageToBeSent;
    String ipTCP;

    public TCP_CLIENT(int tcpServerPort,MessageByTCP messageToBeSent,String ipTCP) {
        this.messageToBeSent = messageToBeSent;
        this.tcpServerPort = tcpServerPort;
        this.ipTCP = ipTCP;
        t = new Thread(this);
        t.start();
    }
    public void run() {
        Socket s = null;
        try {
            s = new Socket(ipTCP, this.tcpServerPort);
            System.out.println("SOCKET=" + s);
            ObjectOutputStream objectOutput = new ObjectOutputStream(s.getOutputStream());
            objectOutput.reset();
            objectOutput.writeObject(this.messageToBeSent);
            System.out.println("[CLIENT SOCKET] - Sent Info to "+ this.tcpServerPort);
            s.close();
            return;
        } catch (UnknownHostException e) {
            System.out.println("Sock:" + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (IOException e) {
                    System.out.println("close:" + e.getMessage());
                }
            System.out.println("[CLIENT SOCKET] - Dead");
        }


    }
}

// SERVER---------------------------------------------------------------------
class TCP_SERVER implements Runnable {

    private Thread serverThread;
    private ServerSocket s;
    String ip;
    private int serversocketPort;

    public TCP_SERVER(int serversocketPort,String ip) {
        this.serverThread = new Thread(this);
        this.serversocketPort = serversocketPort;
        this.ip = ip;
    }
    public void startTCPServer(){
        serverThread.start();
    }
    public void run() {
        while (true) {
            Socket clientSocket = null;
            try {
                clientSocket = this.s.accept();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("CLIENT OFFLINE");
            }
            System.out.println("CLIENT_SOCKET (created at accept())=" + clientSocket);

            new Connection(clientSocket,serversocketPort);
        }
    }
    public int tryConnection(){

        try {
            this.s = new ServerSocket(this.serversocketPort,100, InetAddress.getByName(this.ip));
            System.out.println("LISTEN SOCKET=" + s);
        } catch (IOException e) {
            System.out.println("Port Occupied");
            ++this.serversocketPort;
            tryConnection();
        }
        return this.serversocketPort;
    }
}
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    ObjectInputStream objectInput;
    Socket clientSocket;
    FilesNamesObject filesManager;
    public Connection(Socket aClientSocket, int serversocketPort) {
        try {
            filesManager = new FilesNamesObject(serversocketPort);
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());
            objectInput = new ObjectInputStream(clientSocket.getInputStream());


            this.start();
        } catch (IOException e) {
            System.out.println("Connection:" + e.getMessage());
        }
    }

    //=============================
    public void run() {
        MessageByTCP object = null;
        try {
            object = (MessageByTCP)objectInput.readObject();
            filesManager.saveUsersToDataBase(object.users_list);
            filesManager.saveHashSetsToDataBase("INDEX",object.indexURL);
            filesManager.saveHashSetsToDataBase("REFERENCE",object.refereceURL);
        } catch (EOFException e) {
            System.out.println("Client Loggeg out");
        } catch (IOException e) {
            System.out.println("IO:" + e);
            System.out.println("Client Loggeg out");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("Client Loggeg out");
        } finally {
            return;
        }
    }
}