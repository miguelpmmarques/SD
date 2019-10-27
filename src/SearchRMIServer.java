import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.UnknownHostException;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class SearchRMIServer extends UnicastRemoteObject implements ServerLibrary {

    private AtomicInteger numberRequest;
    private ArrayList<User> listLogedUsers = new ArrayList<>();
    private String MULTICAST_ADDRESS;
    private Comunication comunication;
    private int PORTsend;
    MulticastSocket socketSend;

    public SearchRMIServer(Comunication comunication, int numberRequest,Properties prop) throws RemoteException {
        super();
        this.comunication = comunication;
        this.numberRequest = new AtomicInteger(numberRequest);
        this.MULTICAST_ADDRESS = prop.getProperty("MULTICAST_ADDRESS");
        this.PORTsend = Integer.parseInt(prop.getProperty("MULTICAST_PORT"));
        new MulticastThread(comunication,MULTICAST_ADDRESS,PORTsend);
        System.out.println("[CURRENT REQUEST NUMBER] - "+numberRequest);
    }

    /*
    * Metodo que trata de toda a comunicacao com os servidores multicast
    * Acrescenta o prefixo idRMI com o respetivo indentificador unico para identificar o
    * pacote recebido.
    * E aqui se que inicia o protoco RRA feito por nos de raiz
    * */
    private synchronized String sendToMulticast(String message,int idPack) {
        int MAXNUMBEROFTIMEOUTS = 0;
        message = "idRMI|"+idPack+";"+message;
        byte[] buffer = message.getBytes();
        int id =-1;
        String messageFromMulticast="";
        do{
            try{
                socketSend = new MulticastSocket();
            } catch (IOException e){
                return "";
            }
            try {
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORTsend);
                socketSend.send(packet);
                socketSend.close();
            } catch (IOException e){
                socketSend.close();
                return "";
            }
            messageFromMulticast = comunication.receiveAnswer();
            String[] splitedsms = messageFromMulticast.split("\\;");
            String[] splitedsplitedsms = splitedsms[0].split("\\|");
            System.out.println(splitedsplitedsms[0]);
            if (splitedsplitedsms[0].equals("id"))
            {
                id = Integer.parseInt(splitedsplitedsms[1]);
                System.out.println("id - "+id);
                System.out.println("idPack - "+idPack);
            }
            if (MAXNUMBEROFTIMEOUTS == 16){
                return "SERVERS ARE OFFLINE";
            }
            MAXNUMBEROFTIMEOUTS++;
            System.out.println("message - "+messageFromMulticast);
        // Verificacao se recebeu o pacote certo, senao volta a enviar a request
        }while (messageFromMulticast.equals("") || id!=idPack);
        return messageFromMulticast;
    }
    /*
    * Ligacao inicial de coneccao entre o servidor RMI e o cliente RMI
    * */
    public String connected(ClientLibrary newUser) throws RemoteException {
        //System.out.println(newUser);
        System.out.println("[USER CONNECTED]");
        return "    --WELCOME--\n\nLogin - 1\nRegister -2\nSearch -3\nExit -4\n>>> ";
    }
    /*
     * Self-explanatory
     * */
    public String userRegistration(User newUser) throws RemoteException, UnknownHostException { // DONE
        String requestToMulticast =  "type|requestUSERRegist;" +
                "user|"+newUser.getUsername()+";" +
                "pass|"+newUser.getPassword()+"";
        // ifs para verificar
        System.out.println("[USER REGISTERED] - "+requestToMulticast);
        synchronized (listLogedUsers){
            listLogedUsers.add(newUser);
        }
        String answer  =sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    /*
     * Self-explanatory
     * */
    public String userLogin(User newUser) { // DONE
        String requestToMulticast =  "type|requestUSERLogin;" + "user|"+newUser.getUsername()+";" + "pass|"+newUser.getPassword()+"";
        System.out.println("[USER LOG IN] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        char aux = answer.charAt(answer.length()-1);
        if (aux== 'e'){
            try {
                newUser.getClient().notification("YOU'RE A ADMIN NOW");
            } catch (Exception e){
                System.out.println("Notificacao enviada");
            }

        }
        newUser.setThis((ClientLibrary)newUser.getClient());
        synchronized (listLogedUsers){
            listLogedUsers.add(newUser);
        }
        System.out.println("RESPOSTA -> "+answer);
        return answer;
    }
    /*
     * Self-explanatory
     * */
    public String getHistory(User thisUser) throws RemoteException{
        String requestToMulticast ="type|requestUSERhistory;" + "user|"+thisUser.getUsername();
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    /*
     * Self-explanatory
     * */
    public String getReferencePages(String url) throws RemoteException{
        String requestToMulticast ="type|requestURLbyRef;" + "URL|"+url;
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        System.out.println("REturn -> "+answer);
        return "List of referenced Pages -> "+answer;
    }
    /*
    * Metodo que caso nao esteja a ser feito web crawling, inicializa-o, e se ja estiver a ser feito,
    * acrescenta o url na queue de urls prontos a ser processados
    * */
    public String addURLbyADMIN(String url) throws RemoteException{
        String requestToMulticast ="type|getMulticastList;";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());

        // PRIMEIRO PEDE A LISTA DE TODOS OS MULTICASTS ATIVOS PARA ESCOLHER UM DELES PARA INDEXAR
        String[] chooseMulticast = answer.split("\\;");
        String choosenMulticast = chooseMulticast[1];

        // ASSIM QUE ESCOLHEU O MULTICAST, INDEXA-LHE O URL ESCRITO
        requestToMulticast ="type|requestaddURLbyADMIN;" + "URL|"+url+";MulticastId|"+choosenMulticast;
        System.out.println("Admin added ULR -> "+requestToMulticast);
        answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    /*
    * Metodo que permite o Administrador tornar outros utilizadores administradores
    * Trata tambem das notificacoes, tanto online como offline, avisando o Multicast
    * */
    public String changeUserPrivileges(String username) throws RemoteException{
        String requestToMulticast ="type|requestChangeUSERPrivileges;" + "user|"+username;
        System.out.println(requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        char aux = answer.charAt(answer.length()-1);
        if (aux == 's'){
            synchronized (listLogedUsers){
                for (int i=0;i<listLogedUsers.size();i++){
                    System.out.println(listLogedUsers.get(i).getUsername());
                    if(listLogedUsers.get(i).getUsername().equals(username)){
                        try{
                            listLogedUsers.get(i).getClient().notification("YOU'RE A ADMIN NOW");
                        } catch (Exception e){
                            System.out.println(sendToMulticast("type|requestSetNotify;user|"+username,this.numberRequest.incrementAndGet()));
                        }
                        finally {
                            return answer;
                        }
                    }
                }
            }
            System.out.println(sendToMulticast("type|requestSetNotify;user|"+username,this.numberRequest.incrementAndGet()));
        }
        return answer;
    }
    /*
     * Self-explanatory
     * */
    public String searchWords(String[] words) throws RemoteException{
        System.out.println();
        String requestToMulticast ="type|requestURLbyWord;" +
                "user|"+words[0]+
                ";word_count|"+(words.length-1)+";";
        for (int i = 1; i <= words.length-1; i++) {
            requestToMulticast+= "word_"+ i+"|"+words[i]+";";
        }
        System.out.println("[USER SEARCH] - "+requestToMulticast);
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    /*
     * Self-explanatory
     * */
    public String getAllUsers() throws RemoteException{
        String requestToMulticast ="type|requestAllUSERSPrivileges";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        return answer;
    }
    /*
     * Self-explanatory
     * */
    public String sendSystemInfo() throws RemoteException{
        String requestToMulticast ="type|requestSYSinfo";
        String answer = sendToMulticast(requestToMulticast,this.numberRequest.incrementAndGet());
        System.out.println(answer);
        return answer;
    }
    /*
     * Metodo que o servidor em backup vai invocando para verificar que o servidor
     * principal esta a funcionar corretamente
     * */
    public int checkMe() throws RemoteException{
        return this.numberRequest.intValue();
    }
    // MAIN
    public static void main(String[] args) throws RemoteException {
        String propFileName = "config.properties";
        InputStream inputStream = SearchRMIServer.class.getClassLoader().getResourceAsStream(propFileName);
        Properties prop = new Properties();
        try {
            prop.load(inputStream);
        } catch (Exception e){
            System.out.println("Cannot read properties File");
            return;
        }
        connection(0,prop);
    }
    /*
    * Metodo que cria o registry e que inicializa o servidor principaç
    * Se ja tiver a ser utilizado, e porque ja existe um servidor nesse porto,
    * o que disperta uma excecao chamando uma funcao onde fica o backup server
    * */
    public static void connection(int numberRequest,Properties prop) throws RemoteException {
        try {
            System.setProperty("java.rmi.server.hostname",prop.getProperty("REGISTRYIP"));
            Registry r = LocateRegistry.createRegistry(Integer.parseInt(prop.getProperty("REGISTRYPORT")));
            System.out.println(r);
            r.rebind(prop.getProperty("LOOKUP"), new SearchRMIServer(new Comunication(),numberRequest,prop));
            System.out.println("Im the main Server\nRunning...");

        } catch (RemoteException re) {
            System.out.println("Im the Backup Server");
            failover(numberRequest,prop);
        }
    }
    /*
    * Metodo onde esta o backup server faz chamadas remotas ao servidor principal de maneira
    * a verificar se este esta ativo
    * Se passarem 10 segundos sem este dar sinais de vida, fica principal.
    * */
    public static void failover(int numberRequest,Properties prop) throws RemoteException {
        ServerLibrary checkMainServer;
        int faultCounter = 0;
        while (true){
            try {
                Thread.sleep(2000);
            } catch(InterruptedException e) {
                System.out.println("Interrupted");
            }
            try{

                checkMainServer = (ServerLibrary) LocateRegistry.getRegistry(Integer.parseInt(prop.getProperty("REGISTRYPORT"))).lookup(prop.getProperty("LOOKUP"));
                numberRequest = checkMainServer.checkMe();
                System.out.println("[WORKIN]");
                faultCounter = 0;
            }catch (Exception e) {
                System.out.println("[FAULT]");
                faultCounter++;
            }
            if (faultCounter==5)
                break;
        }
        connection(numberRequest,prop);
    }
}
class MulticastThread extends Thread {
    private String MULTICAST_ADDRESS;
    private int PORT;
    Comunication comunication;
    /*
     * Thread incorporada no servidor RMI, que esta sempre a correr durante toda a execucao o servidor RMI
     * que tem o objetivo de receber as respostas vindas por parte dos servidores Multicast
     * */

    public MulticastThread(Comunication comunication,String MULTICAST_ADDRESS,int PORT){
        this.start();
        this.comunication=comunication;
        this.MULTICAST_ADDRESS = MULTICAST_ADDRESS;
        this.PORT = PORT;
    }

    public void run() {
        MulticastSocket aSocket = null;
        String getSms;
        int requestId;
        try {
            aSocket = new MulticastSocket(PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            aSocket.joinGroup(group);
            while (true) {
                byte[] buffer = new byte[5000];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                aSocket.receive(packet);
                getSms = new String(packet.getData(),0,packet.getLength());
                System.out.println("REECEBEU NO PORTO -> "+getSms);
                if (getSms.substring(0,5).equals("idRMI") ||getSms.substring(0,3).equals("ACK") )
                {
                    System.out.println("AutoEnviou-se");
                    continue;
                }
                requestId  = this.comunication.sendAnswerToRMI(getSms);
                if (requestId != -1){
                    String message = "ACK|"+requestId+";";
                    buffer = message.getBytes();
                    packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                    aSocket.send(packet);
                }
            }
        } catch (IOException e) {
            System.out.println("Socket: " + e.getMessage());
        }finally {
            System.out.println("NAOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO");
            if (aSocket != null) aSocket.close();
        }
    }

}
/*
 * Troca de respostas pela Thread de leitura e as threads RMI a espera de resposta
 * E feito deixa maneira de modo a sincronizar esta troca de variaveis
 * */
class Comunication {
    String sharedObj = "";
    boolean sendToTCPclient = false;
    synchronized String receiveAnswer() {

        while (!sendToTCPclient)
        {
            try {
                wait(5000);
            } catch (InterruptedException e) {
                System.out.println("interruptedException caught");
                sendToTCPclient = false;
            }
            String aux =  this.sharedObj.split("\\;")[0];
            String[] id = aux.split("\\|");
            if (!sendToTCPclient){
                System.out.println("[TIME OUT]");
                sendToTCPclient = false;
                return "";
            }
            if (id[0].equals("idRMI")){
                sendToTCPclient = false;
                continue;
            }
        }
        sendToTCPclient = false;
        return this.sharedObj;
    }
    synchronized int sendAnswerToRMI(String sharedObj) {
        this.sharedObj = sharedObj;
        String aux =  sharedObj.split("\\;")[0];
        String[] id = aux.split("\\|");
        try {
            int requestId = Integer.parseInt(id[1]);
            if (id[0].equals("id")){
                System.out.println("NOTIFICOU");
                sendToTCPclient = true;
                notifyAll();
                return requestId;

            }
        } catch (NumberFormatException e){
            return -1;
        }
        return -1;

    }
}
