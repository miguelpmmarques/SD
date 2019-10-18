import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.ArrayList;
import java.util.Iterator;


public class SearchRMIServer extends UnicastRemoteObject implements ServerLibrary {

    ArrayList<User> listLogedUsers = new ArrayList<>();
    public SearchRMIServer() throws RemoteException {
        super();
    }
    public String connected(ClientLibrary newUser) throws RemoteException {
        //System.out.println(newUser);
        System.out.println("[USER CONNECTED]");
        return "    --WELCOME--\n\nLogin - 1\nRegister -2\nSearch -3\nExit -4\n>>>";
    }
    public boolean userRegistration(User newUser) throws RemoteException{
        System.out.println("[USER REGISTERED] - "+newUser.name);
        synchronized(listLogedUsers)
        {
            listLogedUsers.add(newUser);
        }
        return false;   //Um ifzinho para verificar se e admin
    }
    public boolean userLogin(User newUser) throws RemoteException{
        synchronized(listLogedUsers)
        {
            listLogedUsers.add(newUser);
        }
        newUser.client.notification("LOGGED IN");
        System.out.println("[USER LOG IN] - "+newUser.username);
        return true; //Um ifzinho para verificar se e admin
    }
    public String searchWords(String[] words) throws RemoteException{
        System.out.println("[USER SEARCH]");
        for (int i=0;i<words.length;i++) {
            System.out.println("-> "+words[i]);
        }
        return "BAL BLA BLA";
    }
    public ArrayList<User> listActiveUsers() throws RemoteException{
        synchronized(listLogedUsers)
        {
            return listLogedUsers;
        }
    }
    public String sendSystemInfo() throws RemoteException{
        synchronized(listLogedUsers)
        {
            return "Top 10 pages Visited:\nBla Bla Bla\nTop 10 users Visited:\nBla Bla Bla\nMultiCast Servers List:\n127.0.0.1 : 3664\n...\nNumber Active Users -> "+listLogedUsers.size();
        }
    }
    //CHECK MAIN SERVER FUNCIONALITY
    public int  checkMe() throws RemoteException{
        return 0;
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
                Thread.sleep(5000);
            } catch(InterruptedException e) {
                System.out.println("Interrupted");
            }
            try{
                checkMainServer = (ServerLibrary) LocateRegistry.getRegistry(1401).lookup("ucBusca");
                checkMainServer.checkMe();
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
