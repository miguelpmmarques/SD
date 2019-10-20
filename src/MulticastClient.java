import java.io.IOException;
import java.net.*;
import java.util.Scanner;
public class MulticastClient extends Thread {
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private int PORT = 4322;

    public static void main(String[] args) {
        MulticastClient client = new MulticastClient();
        client.start();
        MulticastUser user = new MulticastUser();
        user.start();
    }

  public void run() {
    MulticastSocket aSocket = null;
    try {
      aSocket = new MulticastSocket(PORT);
      InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
      aSocket.joinGroup(group);
      while (true) {
        byte[] buffer = new byte[1000];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        aSocket.receive(packet);
        System.out.println(
            "Received packet from "
                + packet.getAddress().getHostAddress()
                + ":"
                + packet.getPort()
                + " with message:");
        String message = new String(packet.getData(), 0, packet.getLength());
        System.out.println(message);
      } // while
    } catch (SocketException e) {
      System.out.println("Socket: " + e.getMessage());
    } catch (IOException e) {
      System.out.println("IO: " + e.getMessage());
    } finally {
      if (aSocket != null) aSocket.close();
    }
    }

}


class MulticastUser extends Thread {
    private String MULTICAST_ADDRESS = "224.0.224.0";
    private int PORT = 4321;

    public MulticastUser() {
        super("User " + (long) (Math.random() * 1000));
    }

    public void run() {
        listenShellInterface();
    }
    public void listenShellInterface(){
        MulticastSocket socket = null;
        System.out.println(this.getName() + " ready...");
        try {
            socket = new MulticastSocket();  // create socket without binding it (only for sending)

            Scanner keyboardScanner = new Scanner(System.in);
            while (true) {
                System.out.println("Please type your url:");
                String readKeyboard = keyboardScanner.nextLine();
                byte[] buffer = readKeyboard.getBytes();
                InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, PORT);
                socket.send(packet);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }
}
