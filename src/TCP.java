import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.Serializable;
import RMISERVER.*;
/*
class objects responsible for sending elements for tcp synchronization of databases and url queues
 */

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
            // sending a MessageByTcp type object via tcp
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
// tcp server class responsible for listing for tcp messages and connections
class TCP_SERVER implements Runnable {
    private Thread serverThread;
    private ServerSocket s;
    String ip;
    private int serversocketPort;
    private FilesNamesObject database_object;
    BlockingQueue<String> urls_queue = new LinkedBlockingQueue<>();

    public TCP_SERVER(int serversocketPort,String ip) {

        this.serverThread = new Thread(this);
        this.serversocketPort = serversocketPort;
        this.ip = ip;
        // using the try connection method to verify if the ip:port combination is already beeing used in the same network. If it is, the port is incremented.
        // This ip:port combination will be used by the server as its identification, seeing how it is unique
        this.tryConnection();
        // the FilesNameObject type object beeing used all across the program (because of synchronization concerns) is created here, seeing how this is the firts object to be initialized
        // both because database syncrhonization at the begining of the program if paramout, but also because the try connection method is necessary to establish the server id
        this.database_object = new FilesNamesObject(this.serversocketPort);
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

            new Connection(clientSocket,serversocketPort, this.database_object, urls_queue);
        }
    }

    public FilesNamesObject getDatabase_object() {
        return database_object;
    }

    // Atempting a connection to checkout if the tcp port is already occupied-- Used to establish the identification of the server
    public int tryConnection(){
        try {
            System.out.println(".................ip -> "+this.ip);
            this.s = new ServerSocket(this.serversocketPort,100, InetAddress.getByName(this.ip));
            System.out.println("LISTEN SOCKET=" + s);
            }catch(BindException e){
            System.out.println("Port Occupied");
            ++this.serversocketPort;
            tryConnection();
        } catch (Exception e) {
            System.out.println("Port Occupied");
            ++this.serversocketPort;
            tryConnection();
        }
        return this.serversocketPort;
    }

    public int getServersocketPort() {
        return serversocketPort;
    }

    public BlockingQueue<String> getUrls_queue() {
        return urls_queue;
    }
}

// object to be sent via tcp, for database/urls queue synchronization
class MessageByTCP implements Serializable {
    String type; // used to diferentiate the message type.
    HashMap<String, HashSet<String>> refereceURL;
    HashMap<String, HashSet<String>> indexURL;
    ArrayList<User> users_list;
    BlockingQueue<String> urls_queue;

    // If type=="NEW", then the first constructor must be used, for synchronising the databases only, at the beginning of the program.
    public MessageByTCP(String type, HashMap<String, HashSet<String>> refereceURL,HashMap<String, HashSet<String>> indexURL,ArrayList<User> users_list){
        this.type = type;
        this.refereceURL= refereceURL;
        this.indexURL= indexURL;
        this.users_list= users_list;
    }
    // Otherwise, if type=="UPDATE" the urls_queue will also be shared
    public MessageByTCP(String type, BlockingQueue<String> urls_queue,HashMap<String, HashSet<String>> refereceURL,HashMap<String, HashSet<String>> indexURL){
        this.type = type;
        this.urls_queue = urls_queue;
        this.refereceURL= refereceURL;
        this.indexURL= indexURL;
    }
    // getter methods
    public String getType(){ return this.type; }
    public ArrayList<User> getUsers(){
        return this.users_list;
    }
    public HashMap<String, HashSet<String>> getIndexURL(){
        return this.indexURL;
    }
    public HashMap<String, HashSet<String>> getRefereceURL(){
        return this.refereceURL;
    }
    //-----------------------------------------------
    @Override
    public String toString() {
        return type;
    }
}
/*
handling the tcp connection everytime a tcp client connects
 */
class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    ObjectInputStream objectInput;
    Socket clientSocket;
    FilesNamesObject filesManager;
    BlockingQueue<String> urls_queue;
    public Connection(Socket aClientSocket, int serversocketPort, FilesNamesObject database_object,BlockingQueue<String> urls_queue) {
    System.out.println("CONNECTION");
        this.urls_queue = urls_queue;
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
                synchronized (urls_queue){
                    urls_queue.notify();
                }

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
                if(!aux_new.getUsername().equals(aux.getUsername())){
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