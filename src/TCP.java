import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

class TCP_CLIENT implements Runnable {
    private Thread t;
    private int tcpServerPort;
    private MessageByTCP messageToBeSent;
    private String ipTCP;

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
    private FilesNamesObject database_object;
    private ComunicationUrlsQueueRequestHandler com;
    Queue<String> urls_queue = new LinkedList<>();

    public TCP_SERVER(int serversocketPort,String ip) {
        this.serverThread = new Thread(this);
        this.serversocketPort = serversocketPort;
        this.tryConnection();
        this.ip = ip;
        this.database_object = new FilesNamesObject(this.serversocketPort);
        this.com = new ComunicationUrlsQueueRequestHandler();
    }
    public void startTCPServer(){
        serverThread.start();
    }
    public void run() {
    System.out.println("OLE________________>>>"+ this.serversocketPort);
        while (true) {
            Socket clientSocket = null;
            try {
                clientSocket = this.s.accept();
                System.out.println("RUNNING");
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("CLIENT OFFLINE");
            }
            System.out.println("CLIENT_SOCKET (created at accept())=" + clientSocket);

            new Connection(clientSocket,serversocketPort, this.database_object, urls_queue, com);
        }
    }

    public FilesNamesObject getDatabase_object() {
        return database_object;
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

    public int getServersocketPort() {
        return serversocketPort;
    }

    public ComunicationUrlsQueueRequestHandler getCom() {
        return com;
    }

    public Queue<String> getUrls_queue() {
        return urls_queue;
    }
}
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    ObjectInputStream objectInput;
    Socket clientSocket;
    FilesNamesObject filesManager;
    Queue<String> urls_queue;
    ComunicationUrlsQueueRequestHandler com;
    public Connection(Socket aClientSocket, int serversocketPort, FilesNamesObject database_object,Queue<String> urls_queu, ComunicationUrlsQueueRequestHandler com) {
    System.out.println("CONNECTION");
        this.urls_queue = urls_queu;
        this.com = com;
        try {
            filesManager = database_object;
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
    System.out.println("ppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp");
        try {
            object = (MessageByTCP)objectInput.readObject();
            if (object.type.equals("NEW")){
        System.out.println("WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW");
                joinDataBase(true, true, object);
            }
            else if (object.type.equals("UPDATE")){
                joinDataBase(false, false, object);
                synchronized (urls_queue){
                    for (Map.Entry<String, HashSet<String>> elem : object.getRefereceURL().entrySet()){
                        urls_queue.addAll(elem.getValue());
                    }
                }
                com.process_url(urls_queue);
            }
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
    public void joinDataBase(Boolean with_users, Boolean with_references, MessageByTCP object){
    System.out.println("HERE--------------------------------------");
       HashMap<String, HashSet<String>> indexURL = filesManager.loadDataBase("INDEX");
        HashMap<String, HashSet<String>> refereceURL = filesManager.loadDataBase("REFERENCE");
        ArrayList<User> users_list = filesManager.loadUsersFromDataBase();
    System.out.println("INDEX======>"+ indexURL);
        System.out.println("REFRENCE======>"+ refereceURL);
        System.out.println("USERS======>"+ users_list);
      filesManager.saveHashSetsToDataBase(
          "INDEX", merge_hashmaps(indexURL, object.indexURL));
      if (with_references)
        filesManager.saveHashSetsToDataBase(
            "REFERENCE",
            merge_hashmaps(refereceURL, object.refereceURL));
      if (with_users)
        filesManager.saveUsersToDataBase(
            merge_users(users_list, object.users_list));

      System.out.println(filesManager.loadDataBase("INDEX").size());
      System.out.println(filesManager.loadDataBase("REFERENCE").size());
      System.out.println(filesManager.loadUsersFromDataBase().size());
    }
    public ArrayList<User> merge_users(ArrayList<User> existing_users, ArrayList<User> new_users){
        existing_users.removeAll(new_users);
        existing_users.addAll(new_users);
        return existing_users;
    }

    public HashMap<String, HashSet<String>> merge_hashmaps(HashMap<String, HashSet<String>> existing_map, HashMap<String, HashSet<String>> new_map){
        Queue<HashMap> queue = new LinkedList<>();
        queue.add(existing_map);
        queue.add(new_map);
        return filesManager.mergeQueue(queue);
    }
}