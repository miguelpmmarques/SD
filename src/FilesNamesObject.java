import java.io.*;
import java.util.*;

public class FilesNamesObject {
    String userFile;
    String indexFile;
    String referenceFile;
    public FilesNamesObject(int portId){
         this.indexFile = "indexURL"+portId+".tmp";;
         this.referenceFile = "referenceURL"+portId+".tmp";
         this.userFile = "users"+portId+".tmp";
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
            new ArrayList<User>();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            new ArrayList<User>();
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
        HashMap<String, HashSet<String>> map;
        //System.out.println("FILE NAME __________________>>>"+file_name);
        try {
            fis = new FileInputStream(f_ref);
            ois = new ObjectInputStream(fis);
            map = (HashMap<String, HashSet<String>>) ois.readObject();
            return map;

        } catch (FileNotFoundException ex) {
            System.out.println("Ficheiro ainda nao existe");
            return new HashMap<>();
        } catch (IOException e) {
            System.out.println("ERROR READING FILE --> "+file_name);
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR FIDING CLASS FROM FILE --> "+file_name);
        }
        return new HashMap<>();
    }
    public synchronized void saveUsersToDataBase(ArrayList<User> objectToSave){

        try {
            FileOutputStream fos = new FileOutputStream(this.userFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(objectToSave);
            //System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> USERS");
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

            //System.out.println("GUARDADO EM FICHEIROS OBJETOS COM SUCESSO -> " + typeObject);
            oos.close();
            fos.close();
        } catch (IOException ex) {
            System.out.println("Erro a escrever para o ficheiro.");
            ex.printStackTrace();
        }
    }
    public synchronized HashMap<String, HashSet<String>> mergeQueue(Queue<HashMap> queue){
        HashMap merged_hashmap = new HashMap<>();
        synchronized (queue){
            if(! queue.isEmpty()){
                for (HashMap<String, HashSet<String>> elem : queue){
                    elem.forEach(
                            (key, value) -> merged_hashmap.merge( key, value, (v1, v2) -> v1.equals(v2) ? v1 : v1 + "," + v2)
                    );
                }
            }
        }
        return merged_hashmap;
    }
}
