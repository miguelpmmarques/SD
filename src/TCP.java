import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    BlockingQueue<String> urls_queue = new LinkedBlockingQueue<>();

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

    public BlockingQueue<String> getUrls_queue() {
        return urls_queue;
    }
}
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    ObjectInputStream objectInput;
    Socket clientSocket;
    FilesNamesObject filesManager;
    BlockingQueue<String> urls_queue;
    ComunicationUrlsQueueRequestHandler com;
    public Connection(Socket aClientSocket, int serversocketPort, FilesNamesObject database_object,BlockingQueue<String> urls_queue, ComunicationUrlsQueueRequestHandler com) {
    System.out.println("CONNECTION");
        this.urls_queue = urls_queue;
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
        try {
            object = (MessageByTCP)objectInput.readObject();
            if (object.type.equals("NEW")){
                updateDataBase(object,true);
            }
            else if (object.type.equals("UPDATE")){
                urls_queue.addAll(object.urls_queue);
                updateDataBase(object,false);
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
    private void updateDataBase(MessageByTCP object,boolean saveUsers){
        System.out.println("DEBUG INDEX object --------- "+object.indexURL);
        System.out.println("DEBUG INDEX file --------- "+filesManager.loadDataBase("INDEX"));

        System.out.println("DEBUG REFERENCE object --------- "+object.refereceURL);
        System.out.println("DEBUG REFERENCE file --------- "+filesManager.loadDataBase("REFERENCE"));

        filesManager.saveHashSetsToDataBase("INDEX",mergeDataBases(filesManager.loadDataBase("INDEX"),object.indexURL));
        filesManager.saveHashSetsToDataBase("REFERENCE",mergeDataBases(filesManager.loadDataBase("REFERENCE"),object.refereceURL));
        if (saveUsers)
            filesManager.saveUsersToDataBase(merge_users(filesManager.loadUsersFromDataBase(),object.users_list));
    }

    private HashMap<String, HashSet<String>> mergeDataBases(HashMap<String, HashSet<String>> one ,HashMap<String, HashSet<String>> two){
        Iterator it = one.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            if (two.get(pair.getKey())==null)
                two.put((String) pair.getKey(),(HashSet<String>) pair.getValue());
            else {
                two.get(pair.getKey()).addAll((HashSet<String>) pair.getValue());
            }
        }
        return two;
    }



    public ArrayList<User> merge_users(ArrayList<User> existing_users, ArrayList<User> new_users){
        ArrayList<User> list_to_send = new ArrayList<>();
        list_to_send.addAll(existing_users);
        for ( User aux_new : new_users ){
            int helper=0;
            for (User aux : existing_users){
                if(!aux_new.username.equals(aux.username)){
                    helper++;
                }
            }
            if(helper==existing_users.size()){
                list_to_send.add(aux_new);
            }
        }
        return list_to_send;
    }

}