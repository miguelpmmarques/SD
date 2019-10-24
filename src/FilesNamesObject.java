import java.io.*;
import java.util.*;

public class FilesNamesObject {
    String userFile;
    String indexFile;
    String referenceFile;
    public FilesNamesObject(int portId){
         this.userFile = "indexURL"+portId+".tmp";;
         this.indexFile = "referenceURL"+portId+".tmp";
         this.referenceFile = "users"+portId+".tmp";
    }
    public synchronized ArrayList<User> loadUsersFromDataBase() {
        File f_users = new File(this.userFile);
        FileInputStream fis;
        ObjectInputStream ois;
        ArrayList<User> users_list = new ArrayList<>();
        try {
            fis = new FileInputStream(f_users);
            ois = new ObjectInputStream(fis);
            users_list = (ArrayList) ois.readObject();
            return users_list;
        } catch (FileNotFoundException ex) {
            return new ArrayList<User>();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return users_list;
    }

    public synchronized HashMap<String, HashSet<String>> loadDataBase(String file) {
        String file_name = "";
        switch (file) {
            case "INDEX":
                file_name = this.indexFile;
                break;
            case "REFERENCE":
                file_name = this.referenceFile;
                break;
        }
        File f_ref = new File(file_name);
        FileInputStream fis;
        ObjectInputStream ois;
        HashMap map;
        try {
            fis = new FileInputStream(f_ref);
            ois = new ObjectInputStream(fis);
            map = (HashMap) ois.readObject();

            return map;

        } catch (FileNotFoundException ex) {
            System.out.println("Rip");
        } catch (IOException e) {
            System.out.println("IO -FILE- "+file_name);

            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.out.println("Class not found");
            e.printStackTrace();
        }
        return new HashMap<>();
    }
    public synchronized void saveUsersToDataBase(ArrayList<User> objectToSave){

        try {
            FileOutputStream fos = new FileOutputStream(this.userFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToSave);
            System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> USERS");
            oos.close();
            fos.close();
        } catch (IOException ex) {
            System.out.println("Erro a escrever para o ficheiro.");
            ex.printStackTrace();
        }
    }
    // so far only useful for saving url hashMaps
    public synchronized void saveHashSetsToDataBase(String typeObject, HashMap<String, HashSet<String>> objectToSave) {
        String file_name = "";
        switch (typeObject) {
            case "INDEX":
                file_name = this.indexFile;
                break;
            case "REFERENCE":
                file_name = this.referenceFile;
                break;
        }

        try {
            FileOutputStream fos = new FileOutputStream(file_name);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToSave);

            System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> " + typeObject);
            oos.close();
            fos.close();
        } catch (IOException ex) {
            System.out.println("Erro a escrever para o ficheiro.");
            ex.printStackTrace();
        }
    }
}
