import java.io.IOException;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;


public class SearchRMIServer extends UnicastRemoteObject implements ServerLibrary {
    static Queue<String> queueURL = new LinkedList<>();
    ArrayList<User> listLogedUsers = new ArrayList<>();
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private int PORT = 4321;
    MulticastSocket socket = null;
    private boolean sendToMulticast(String message){
        byte[] buffer = message.getBytes();
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
            socket.send(packet);
        } catch (UnknownHostException e){
            sendToMulticast(message);
        }catch (IOException e){
            sendToMulticast(message);
        }
        return true;
    }
    public SearchRMIServer() throws RemoteException {
        super();
        System.out.println(queueURL);
    }
    public String connected(ClientLibrary newUser) throws RemoteException {
        //System.out.println(newUser);
        System.out.println("[USER CONNECTED]");
        return "    --WELCOME--\n\nLogin - 1\nRegister -2\nSearch -3\nExit -4\n>>> ";
    }
    public boolean userRegistration(User newUser) throws RemoteException, UnknownHostException {
        String requestToMulticast =  "type|requestUSERRegist;" +
                "user|"+newUser.username+";" +
                "pass|"+newUser.password+"";
        // ifs para verificar
        System.out.println("[USER REGISTERED] - "+requestToMulticast);
        sendToMulticast(requestToMulticast);
        synchronized(listLogedUsers)
        {
            listLogedUsers.add(newUser);
        }



        return false;   //Um ifzinho para verificar se o username esta livre
    }
    public boolean userLogin(User newUser) throws RemoteException, InterruptedException {
        //Thread.sleep(5000);
        String requestToMulticast =  "type|requestUSERRegist;" +
                "user|"+newUser.username+";" +
                "pass|"+newUser.password+"";

        // ifs para verificar
        queueURL.add(newUser.username);
        synchronized(listLogedUsers)
        {
            listLogedUsers.add(newUser);
            System.out.println(queueURL);
        }

        System.out.println("[USER LOG IN] - "+requestToMulticast);
        //sendToMulticast("URL|facebook.com");
        return true;
    }
    public String changeUserPrivileges(String username) throws RemoteException{
        String requestToMulticast ="type|requestChangeUSERPrivileges;" +
                "user|"+username;
        System.out.println(requestToMulticast);
        sendToMulticast(requestToMulticast);
        return username+"'s Privileges changed Successfully";//Ou user not found
    }
    public String getHistory(User thisUser) throws RemoteException{
        String requestToMulticast ="type|requestUSERhistory;" +
                "user|"+thisUser.username;
        System.out.println(requestToMulticast);
        sendToMulticast(requestToMulticast);
        return "Empty";
    }
    public String getReferencePages(String url) throws RemoteException{
        String requestToMulticast ="type|requestURLbyRef;" +
                "URL|"+url;
        sendToMulticast(requestToMulticast);
        return "List of referenced Pages -> "+requestToMulticast;
    }
    public void addURLbyADMIN(String url) throws RemoteException{
        String requestToMulticast ="type|requestaddURLbyADMIN;" +
                "URL|"+url;
        System.out.println("Admin added ULR -> "+requestToMulticast);
        sendToMulticast(requestToMulticast);
    }
    public String searchWords(String[] words) throws RemoteException{
        String requestToMulticast ="type|requestURLbyWord;" +
                "word_count|"+words.length;
        for (int i = 1; i <= words.length; i++) {
            requestToMulticast+= "word_"+ i+"|"+words[i-1]+";";
        }
        System.out.println("[USER SEARCH] - "+requestToMulticast);
        sendToMulticast(requestToMulticast);
        return "BAL BLA BLA";
    }
    public String getAllUsers() throws RemoteException{
        String requestToMulticast ="type|requestURLbyWord";
        System.out.println(requestToMulticast);
        sendToMulticast(requestToMulticast);
        return "Empty";
    }

    public String sendSystemInfo() throws RemoteException{
        String requestToMulticast ="type|requestSYSinfo";
        sendToMulticast(requestToMulticast);
        return "Not Done Yet";
    }
    //CHECK MAIN SERVER FUNCIONALITY
    public Queue<String>  checkMe() throws RemoteException{
        return queueURL;
    }
    // MAIN
    public static void main(String[] args) throws RemoteException, NotBoundException {
        connection();

    }
    public static void connection() throws RemoteException, NotBoundException {
        try {
            Registry r = LocateRegistry.createRegistry(1401);
            r.rebind("ucBusca", new SearchRMIServer());
            System.out.println("Im the main Server\nRunning...");

        } catch (RemoteException re) {
            System.out.println("Im the Backup Server");
            failover();
        }
    }

    public static void failover() throws RemoteException, NotBoundException {
        ServerLibrary checkMainServer;
        int faultCounter = 0;
        while (true){
            try {
                Thread.sleep(2000);
            } catch(InterruptedException e) {
                System.out.println("Interrupted");
            }
            try{
                checkMainServer = (ServerLibrary) LocateRegistry.getRegistry(1401).lookup("ucBusca");
                queueURL = checkMainServer.checkMe();
                System.out.println("[WORKIN]");
                faultCounter = 0;
            }catch (Exception e) {
                System.out.println("[FAULT]");
                faultCounter++;
            }
            if (faultCounter==5)
                break;
        }
        connection();
    }
}
